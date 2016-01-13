(ns catalog.vubis
  "Import XML files from the library system"
  (:require [clj-time
             [coerce :as time.coerce]
             [format :as time.format]]
            [clojure
             [string :as string]
             [xml :as xml]
             [zip :as zip]]
            [clojure.data.zip.xml :refer [attr= text xml-> xml1->]]
            [clojure.java.io :as io]
            [medley.core :refer [assoc-some]]))

(def ^:private param-mapping
  "Mapping from Marc21 XML to parameters. See [MARC 21 Format for Bibliographic Data](http://www.loc.gov/marc/bibliographic/)"
  {:record-id [:controlfield (attr= :tag "001")]
   :title [:datafield (attr= :tag "245") :subfield (attr= :code "a")]
   :subtitles [:datafield (attr= :tag "245") :subfield (attr= :code "b")]
   :name-of-part [:datafield (attr= :tag "245") :subfield (attr= :code "p")] ; Bandangabe
   :creator [:datafield (attr= :tag "100") :subfield] ; autor
   :source [:datafield (attr= :tag "020") :subfield] ; isbn
   :description [:datafield (attr= :tag "520") :subfield] ; abstract
   :volumes [:datafield (attr= :tag "300") :subfield (attr= :code "9")] ; Anzahl Medien
   :source-publisher [:datafield (attr= :tag "534") :subfield (attr= :code "c")] ; Verlag
   :source-date [:datafield (attr= :tag "534") :subfield (attr= :code "d")] ; Erscheinungsjahr
   :language [:datafield (attr= :tag "041") :subfield (attr= :code "a")] ; Sprache
   :general-note [:datafield (attr= :tag "500") :subfield (attr= :code "a")] ; Land, Erscheinungsjahr (des Originalfilms)
   :accompanying-material [:datafield (attr= :tag "300") :subfield (attr= :code "e")] ; Begleitmaterial oder Spiel-Materialdetails
   :narrators [:datafield (attr= :tag "709") :subfield (attr= :code "a")] ; Sprecher
   :duration [:datafield (attr= :tag "391") :subfield (attr= :code "a")] ; Spieldauer
   :braille-grade [:datafield (attr= :tag "392") :subfield (attr= :code "a")] ; Schriftart Braille
   :double-spaced? [:datafield (attr= :tag "392") :subfield (attr= :code "g")] ; Weitzeiliger Druck
   :braille-music-grade [:datafield (attr= :tag "393") :subfield (attr= :code "a")] ; Schriftart Braille
   :producer-long [:datafield (attr= :tag "260") :subfield (attr= :code "b")] ; Produzent
   :producer [:datafield (attr= :tag "260") :subfield (attr= :code "9")] ; a number that determines Produzent Kürzel und Stadt
   :producer-place [:datafield (attr= :tag "260") :subfield (attr= :code "a")] ; Produzent Stadt
   :produced-date [:datafield (attr= :tag "260") :subfield (attr= :code "c")]
   :produced-commercially? [:datafield (attr= :tag "260") :subfield (attr= :code "d")] ; kommerziell?
   :series-title [:datafield (attr= :tag "830") :subfield (attr= :code "a")] ; u.a. Rucksackbuch
   :series-volume [:datafield (attr= :tag "830") :subfield (attr= :code "v")]
   :format [:datafield (attr= :tag "091") :subfield (attr= :code "c")]
   :genre [:datafield (attr= :tag "099") :subfield (attr= :code "b")] ; also Spiel-Systematikgruppe
   :genre-code [:datafield (attr= :tag "099") :subfield (attr= :code "a")] ; used for movie genre i.e. Filmkategorie
   :genre-text [:datafield (attr= :tag "099") :subfield (attr= :code "f")] ; used to display the genre
   :library-signature [:datafield (attr= :tag "091") :subfield (attr= :code "a")] ; Signaturen
   :product-number [:datafield (attr= :tag "024") :subfield (attr= :code "a")] ; MVL-Bestellnummer
   :price [:datafield (attr= :tag "024") :subfield (attr= :code "c")] ; Preis
   :game-description [:datafield (attr= :tag "300") :subfield (attr= :code "a")] ; Beschreibung von Spielen
   })

(def iso-639-2-to-iso-639-1
  "Mapping between three letter codes of [ISO
  639-2](http://en.wikipedia.org/wiki/ISO_639-2) and the two letter
  codes of [ISO 639-1](http://en.wikipedia.org/wiki/ISO_639-1)"
  {"ger" "de"
   "gsw" "de-CH"
   "fre" "fr"
   "eng" "en"})

(def genre-raw-to-genre
  "Mapping between genre-raw and genre"
  {"b" :belletristik
   "s" :sachbücher
   "k" :kinder-und-jugendbücher})

(def genre-raw-to-subgenre
  "Mapping between genre-raw and subgenre"
  {"b01" :action-und-thriller
   "b02" :beziehungsromane
   "b03" :fantasy-science-fiction
   "b04" :gesellschaftsromane
   "b05" :historische-romane
   "b06" :hörspiele
   "b07" :krimis
   "b08" :lebensgeschichten-und-schicksale
   "b09" :literarische-gattungen
   "b10" :literatur-in-fremdsprachen
   "b11" :mundart-heimat-natur
   "b12" :glaube-und-philosophie
   "s01" :biografien
   "s02" :freizeit-haus-garten
   "s03" :geschichte-und-gegenwart
   "s04" :kunst-kultur-medien
   "s05" :lebensgestaltung-gesundheit-erziehung
   "s06" :philosophie-religion-esoterik
   "s07" :reisen-natur-tiere
   "s08" :sprache
   "s09" :wissenschaft-technik
   "k01" :jugendbücher
   "k02" :kinder-und-jugendsachbücher
   "k03" :kinderbücher-ab-10
   "k04" :kinderbücher-ab-6})

(def format-raw-to-format
  "Mapping between format-raw and format"
  {"BR" :braille
   "DVD" :hörfilm
   "DY" :hörbuch
   "ER" :e-book
   "GD" :grossdruck
   "LU" :ludo
   "MN" :musiknoten
   "TB" :taktilesbuch})

(def producer-raw-to-producer
  "Mapping between producer-raw and producer"
  {1 "DBh, Marburg"
   101 "DBh, Marburg"
   2 "BHB, Berlin"
   4 "NBH, Hamburg"
   5 "BBH, München"
   6 "WBH, Münster"
   8 "SBH, Stuttgart"
   11 "DKBB, Bonn"
   13 "SBS, Zürich"
   100 "SBS, Zürich"
   24 "DZB, Leipzig"
   102 "DZB, Leipzig"
   25 "CAB, Landschlacht"
   40 "BIT, München"
   50 "BSVÖ, Wien"
   103 "Paderborn"
   111 "RNIB, London"})

(def genre-code-to-genre
  "Mapping between genre-code and genre. Used for movies"
  {"I1" :spielfilm
   "I2" :dokumentarfilm
   "I3" :mundartfilm})

(def braille-grade-raw-to-braille-grade
  "Mapping between braille-grade-raw and braille-grade"
  {"kr" :kurzschrift
   "vd" :vollschrift})

(def braille-music-grade-raw-to-braille-grade
  "Mapping between braille-music-grade-raw and braille-grade"
  {"vd" :vollschrift ; Vollschrift Deutsch
   "vs" :vollschrift ; Vollschrift other language
   "ms" :vollschrift}) ; Musikschrift, not valid really, but the
                       ; MARC21 data isn't super clean for music, so
                       ; swallow this as well

(defn parse-int [s]
   (when-let [number (and s (re-find #"\d+" s))]
     (Integer/parseInt number)))

(defn get-year
  "Grab the date year out of a string. Return nil if `s` cannot be
  parsed."
  [s]
  (when-let [year (and s (re-find #"\d{4}" s))]
    (time.coerce/to-date
     (time.format/parse (time.format/formatters :year) year))))

(defn remove-nonprintable-chars [s]
  (and s (string/replace s #"[¶¬]" "")))

(defn normalize-name
  "Change a name from 'name, surname' to 'surname name'"
  [name]
  (when name
    (->> (string/split name #",")
         reverse
         (string/join " ")
         string/trim
         remove-nonprintable-chars)))

(defn trim-punctuation [s]
  (-> s
      (string/replace #"[\p{Punct}]+$" "")
      (string/replace #"^[\p{Punct}]+" "")))

(defn- trunc
  "Get the first `n` chars of `s`. If `s` has fewer chars than `n`
  then just return `s`."
  ;; http://stackoverflow.com/questions/20747112/simple-way-to-truncate-a-string-in-clojure
  [s n]
  (subs s 0 (min (count s) n)))

(defn get-personel [s]
  (-> s
      trim-punctuation
      normalize-name))

(defn get-format [s]
  "Find the given format for a string `s`. If the string doesn't
  correspond to any of BR, DVD, DY, ER, GD, LU, MN or TB return nil"
  (->> s
   (re-find #"^(?:BR|DVD|DY|ER|GD|LU|MN|TB)$")
   format-raw-to-format))

(defn clean-raw-item
  "Return a proper production based on a raw item, e.g.
  translate the language tag into proper ISO 639-1 codes"
  [{:keys [record-id source description source-publisher
           library-signature title subtitles name-of-part creator
           price product-number language format producer
           genre genre-code genre-text
           produced-commercially? source-date general-note
           series-title series-volume duration
           volumes narrators producer-long
           game-description double-spaced?
           braille-grade
           directed-by actors
           accompanying-material braille-music-grade] :as item
    :or {genre "x01" ; an invalid genre
         genre-code "x0"}}] ; an invalid genre-code
  (let [fmt (get-format format)
        ;; tactile books aren't properly tagged in the format field.
        ;; They are tagged as :ludo and their library signature starts
        ;; with "TB"
        fmt (if (and (= fmt :ludo) (re-find #"^TB " library-signature))
              :taktilesbuch fmt)
        item (-> {}
                 (assoc-some
                  :record-id record-id
                  :title (remove-nonprintable-chars title)
                  :subtitles (seq (map remove-nonprintable-chars subtitles))
                  :name-of-part name-of-part
                  :creator creator
                  :description description
                  :source-publisher source-publisher
                  :source source
                  :language (iso-639-2-to-iso-639-1 language)
                  :genre (or (genre-raw-to-genre (trunc genre 1))
                             (genre-code-to-genre (trunc genre-code 2)))
                  :sub-genre (genre-raw-to-subgenre (trunc genre 3))
                  :genre-text genre-text
                  :format fmt
                  :producer-brief (producer-raw-to-producer (parse-int producer))
                  :source-date (get-year source-date)
                  :library-signature library-signature
                  :product-number product-number
                  :price price))]
    (case fmt
      :hörbuch (-> item
                   (assoc-some
                    :produced-commercially? (some? produced-commercially?)
                    :duration (parse-int duration)
                    :narrators (not-empty (map normalize-name narrators))))
      :braille (let [rucksackbuch-number (and series-title
                                              (re-find #"^Rucksackbuch" series-title)
                                              (parse-int series-volume))
                     double-spaced? (boolean
                                      (and double-spaced?
                                           (re-find #"^Weitzeiliger Druck" double-spaced?)))]
                 (-> item
                     (assoc-some
                      :rucksackbuch-number rucksackbuch-number
                      :rucksackbuch? (boolean rucksackbuch-number)
                      :braille-grade (braille-grade-raw-to-braille-grade braille-grade)
                      :double-spaced? double-spaced?
                      :volumes (parse-int volumes))))
      :grossdruck (-> item
                      (assoc-some
                       :volumes (parse-int volumes)))
      :hörfilm (-> item
                   (assoc-some
                    :movie_country (and general-note
                                        (second (re-find #"^Originalversion: (.*)$" general-note)))
                    :producer producer-long
                    :directed-by directed-by
                    :actors actors))
      :ludo (-> item
                (assoc-some
                 ;; FIXME: in the future we will have the genre of the
                 ;; game encoded in MARC21 but this hasn't been done
                 ;; in the libary. So for now just say it is of
                 ;; genre :spiel
                 :genre :spiel
                 :game-description game-description
                 :accompanying-material accompanying-material))

      :e-book item
      :musiknoten (-> item
                      (assoc-some
                       :braille-grade (braille-music-grade-raw-to-braille-grade braille-music-grade)
                       :volumes (parse-int volumes)
                       :accompanying-material accompanying-material))
      :taktilesbuch (-> item
                        (assoc-some
                         :braille-grade (braille-grade-raw-to-braille-grade braille-grade)
                         :double-spaced? double-spaced?
                         :accompanying-material accompanying-material))
      ; default case that shouldn't really happen. When the MARC21
      ; entry contains a faulty format then just return the item with
      ; the faulty format. The validation will filter the item out.
      (assoc item :format format))))

(defn select-vals
  "Return a list of the values for the given keys `ks` from `item` if
  the value of the first key is non-nil"
  [ks item]
  (when ((first ks) item)
    (reduce #(conj %1 (item %2)) [] ks)))

(defn fourth [coll] (first (next (next (next coll)))))

(defn merge-braille-catalog-items
  "Merge `items` into one. All product-numbers and all
  library-signatures together with their related information such as
  braille-grade and volumes are collected in a list and merged with
  the other properties."
  [items]
  (some-> items
   first
   (assoc-some
    :product-number
    (->> items
         (map #(select-vals [:product-number :braille-grade :volumes :double-spaced? :accompanying-material] %))
         (remove empty?)
         (group-by (juxt second #(boolean (fourth %)))) ; group by grade and line spacing
         not-empty) ; only pick entries that are non-empty
    :library-signature
    (->> items
         (map #(select-vals [:library-signature :braille-grade :volumes :double-spaced? :accompanying-material] %))
         (remove empty?)
         (group-by (juxt second #(boolean (fourth %)))) ; group by grade and line spacing
         not-empty)) ; only pick entries that are non-empty
   (dissoc :volumes :braille-grade :double-spaced? :accompanying-material)))

(defn collate-duplicate-items [items]
  (->> items
       (group-by (juxt :source :title))
       vals
       (map #(merge-braille-catalog-items %))))

(defn collate-all-duplicate-items
  "Merge duplicate braille `items` into one. Typically we have
  multiple entries for the same braille book for all the different
  braille grades. In the catalog we want one entry"
  [items]
  (let [braille-items (filter #(= (:format %) :braille) items)
        musiknoten-items (filter #(= (:format %) :musiknoten) items)
        taktile-items (filter #(= (:format %) :taktilesbuch) items)
        others (remove #(#{:braille :musiknoten :taktilesbuch} (:format %)) items)]
    (concat
     (mapcat collate-duplicate-items [braille-items musiknoten-items taktile-items])
     others)))

(defn order-and-group [items]
  (->>
   items
   collate-all-duplicate-items
   ;; sort by creator, title. If there is no creator then sort just by title
   (sort-by (juxt #(or (:creator %) (:title %)) :title))
   (reduce
    (fn [m {:keys [format genre sub-genre] :as item}]
      (let [update-keys
            (cond
              (= format :hörbuch) [format genre sub-genre]
              (#{:hörfilm :ludo} format) [format]
              ;; file tactile books and musiknoten under braille with
              ;; the original format as genre
              (#{:taktilesbuch :musiknoten} format) [:braille format]
              (= genre :kinder-und-jugendbücher) [format genre sub-genre]
              :else [format genre])]
        (update-in m update-keys (fnil conj []) item))) {})))

(defn ignore-sine-nomine [s]
  "Return input string `s` unless it contains \"s.n.\" (Sine nomine),
  \"s.l.\" (Sine loco) or \"s.a.\" (Sine anno)"
  (when-not (re-matches #"\[?s.[nla]+.\]?" s)
    s))

(defn get-subfield
  "Get the subfield text for the given `path` in the given `record`.
  Returns nil if there is no such subfield"
  [record path]
  (some->
   (apply xml1-> record path) text string/trim ignore-sine-nomine))

(defn get-multi-subfields
  "Get a list of subfield texts for the given `path` in the given
  `record`. Returns an empty list if there is no such subfield"
  [record path]
  (some->>
   (apply xml-> record path) (map text) (map string/trim) (map ignore-sine-nomine)))

(defn get-personel-role
  "Return `:director` if `personel` is a director, `actor` if
  `personel` is an actor or nil otherwise"
  [personel]
  (when-let [raw-role (some-> (xml1-> personel :subfield (attr= :code "e")) text)]
    (cond
      (re-find #"Regie" raw-role) :director
      (re-find #"Darst\." raw-role) :actor
      :else nil)))

(defn get-personel-name
  "Get the name of a `personel`"
  [personel]
  (some->
   (xml1-> personel :subfield (attr= :code "a"))
   text
   normalize-name))

(defn personel-director? [personel]
  (= :director (get-personel-role personel)))

(defn personel-actor? [personel]
  (= :actor (get-personel-role personel)))

(defn get-personel-fields
  "Grab all the personel related fields, e.g. directors and actors
  from a `record`. Return a map with the keys `:directed-by` and
  `:actors` that contains seqs of strings. If a record has no actors
  or directors the corresponding key is not in the returned map, so
  the returned map is potentially empty"
  [record]
  (let [personel (xml-> record :datafield (attr= :tag "700"))] ; Regie oder Darsteller
    (assoc-some
     {}
     :directed-by (seq (map get-personel-name (filter personel-director? personel)))
     :actors (seq (map get-personel-name (filter personel-actor? personel))))))

(defn read-file
  "Read an export file from VUBIS and return a map with all the data"
  [file]
  (let [root (-> file io/file xml/parse zip/xml-zip)]
    (for [record (xml-> root :record)]
      (->> (for [[key path] param-mapping
                 :let [val (cond
                             (#{:narrators :subtitles} key) (get-multi-subfields record path)
                             :else (get-subfield record path))]
                 :when (some? val)]
             [key val])
           (into {})
           (merge (get-personel-fields record))
           clean-raw-item))))
