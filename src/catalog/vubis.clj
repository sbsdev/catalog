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
   :subtitle [:datafield (attr= :tag "245") :subfield (attr= :code "b")]
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
   :personel-name [:datafield (attr= :tag "700") :subfield (attr= :code "a")] ; Regie oder Darsteller
   :personel-relator-term [:datafield (attr= :tag "700") :subfield (attr= :code "e")]
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
   103 "Paderborn"})

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

(defn get-subfield
  "Get the subfield text for the given `path` in the given `record`.
  Returns nil if there is no such subfield"
  [record path]
  (some-> (apply xml1-> record path) text string/trim))

(defn get-multi-subfields
  "Get a list of subfield texts for the given `path` in the given
  `record`. Returns an empty list if there is no such subfield"
  [record path]
  (some->> (apply xml-> record path) (map text) (map string/trim)))

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
  (string/replace s #"[¶¬]" ""))

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

(defn get-personel [s]
  (-> s
      trim-punctuation
      normalize-name))

(defn get-format [s]
  (->> s
   ;; some format entries in the MARC21 file contain garbage after the
   ;; format, so only look at the start
   ;; FIXME: tbh shouldn't this be fixed in the original data? Were're
   ;; only talking about 4 records that have 'LUD' and one that has
   ;; 'LUn'
   (re-find #"^(?:BR|DVD|DY|ER|GD|LU|MN|TB)")
   format-raw-to-format))

(defn clean-raw-item
  "Return a proper production based on a raw item, e.g.
  translate the language tag into proper ISO 639-1 codes"
  [{:keys [record-id source description source-publisher
           library-signature title creator
           price product-number
           genre genre-code language format producer
           produced-commercially? source-date general-note
           series-title series-volume duration
           volumes narrators producer-long
           game-description double-spaced?
           braille-grade personel-name
           accompanying-material braille-music-grade] :as item
    :or {genre "x01" ; an invalid genre
         genre-code "x0"}}] ; an invalid genre-code
  (let [fmt (get-format format)
        item (-> {}
                 (assoc-some
                  :record-id record-id
                  :title (remove-nonprintable-chars title)
                  :creator creator
                  :description description
                  :source-publisher source-publisher
                  :source source
                  :language (iso-639-2-to-iso-639-1 language)
                  :genre (or (genre-raw-to-genre (subs genre 0 1))
                             (genre-code-to-genre (subs genre-code 0 2)))
                  :sub-genre (genre-raw-to-subgenre (subs genre 0 3))
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
                    :personel-name (get-personel personel-name)))
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
      :taktilesbuch item)))

(defn select-vals
  "Return a list of the values for the given keys `ks` from `item` if
  the value of the first key is non-nil"
  [ks item]
  (when ((first ks) item)
    (-> item (select-keys ks) vals)))

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
         (map #(select-vals [:product-number :braille-grade :volumes :double-spaced?] %))
         (remove empty?)
         (group-by (juxt second #(boolean (fourth %)))) ; group by grade and line spacing
         not-empty) ; only pick entries that are non-empty
    :library-signature
    (->> items
         (map #(select-vals [:library-signature :braille-grade :volumes :double-spaced?] %))
         (remove empty?)
         (group-by (juxt second #(boolean (fourth %)))) ; group by grade and line spacing
         not-empty)) ; only pick entries that are non-empty
   (dissoc :volumes :braille-grade)))

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
   (sort-by (juxt :creator :title))
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

(defn read-file
  "Read an export file from VUBIS and return a map with all the data"
  [file]
  (let [root (-> file io/file xml/parse zip/xml-zip)]
    (for [record (xml-> root :record)]
      (->> (for [[key path] param-mapping
                 :let [val (cond
                             (#{:narrators} key) (get-multi-subfields record path)
                             :else (get-subfield record path))]
                 :when (some? val)]
             [key val])
           (into {})
           clean-raw-item))))
