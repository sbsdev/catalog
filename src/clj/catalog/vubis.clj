(ns catalog.vubis
  "Import XML files from the library system"
  (:require [catalog.layout.common :as layout]
            [catalog.unicode-normalization :as unicode]
            [clojure.data.zip :as data-zip]
            [clojure.data.zip.xml :refer [attr= text xml-> xml1->]]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [java-time :as time]
            [medley.core :refer [assoc-some]])
  (:import java.text.Collator
           java.util.Locale))

(def ^:private collator (Collator/getInstance (Locale. "de_CH")))

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
   :ismn  [:datafield (attr= :tag "534") :subfield (attr= :code "z")] ; International Standard Music Number
   :language [:datafield (attr= :tag "041") :subfield (attr= :code "a")] ; Sprache
   :general-information [:controlfield (attr= :tag "008")] ; used to extract target audience
   :general-note [:datafield (attr= :tag "500") :subfield (attr= :code "a")] ; Land, Erscheinungsjahr (des Originalfilms)
   :accompanying-material [:datafield (attr= :tag "300") :subfield (attr= :code "e")] ; Begleitmaterial oder Spiel-Materialdetails
   :accompanying-material-legacy [:datafield (attr= :tag "392") :subfield (attr= :code "f")] ; Begleitmaterial oder Spiel-Materialdetails for old records
   :accompanying-material-legacy-other [:datafield (attr= :tag "300") :subfield (attr= :code "b")] ; Begleitmaterial oder Spiel-Materialdetails for very old records
   :accompanying-material-images [:datafield (attr= :tag "300") :subfield (attr= :code "b")] ; Begleitmaterial für E-Books
   :narrators [:datafield (attr= :tag "709") :subfield (attr= :code "a")] ; Sprecher
   :duration [:datafield (attr= :tag "391") :subfield (attr= :code "a")] ; Spieldauer
   :personel-text [:datafield (attr= :tag "246") :subfield (attr= :code "g")] ; Regie/Darsteller bei Hörfilm
   :braille-grade [:datafield (attr= :tag "392") :subfield (attr= :code "a")] ; Schriftart Braille
   :double-spaced? [:datafield (attr= :tag "392") :subfield (attr= :code "g")] ; Weitzeiliger Druck
   :braille-music-grade [:datafield (attr= :tag "393") :subfield (attr= :code "a")] ; Schriftart Braille
   :producer-long [:datafield (attr= :tag "260") :subfield (attr= :code "b")] ; Produzent
   :producer [:datafield (attr= :tag "260") :subfield (attr= :code "9")] ; a number that determines Produzent Kürzel und Stadt
   :producer-place [:datafield (attr= :tag "260") :subfield (attr= :code "a")] ; Produzent Stadt
   :produced-date [:datafield (attr= :tag "260") :subfield (attr= :code "c")]
   :produced-commercially? [:datafield (attr= :tag "260") :subfield (attr= :code "d")] ; kommerziell?
   :series-type [:datafield (attr= :tag "492") :subfield (attr= :code "a")] ; Easyreader, Print&Braille, etc
   :series-title [:datafield (attr= :tag "830") :subfield (attr= :code "a")] ; u.a. Rucksackbuch
   :series-volume [:datafield (attr= :tag "830") :subfield (attr= :code "v")]
   :format [:datafield (attr= :tag "091") :subfield (attr= :code "c")]
   :genre [:datafield (attr= :tag "099") :subfield (attr= :code "b")] ; also Spiel-Systematikgruppe
   :genre-code [:datafield (attr= :tag "099") :subfield (attr= :code "a")] ; used for movie genre i.e. Filmkategorie
   :genre-text [:datafield (attr= :tag "099") :subfield (attr= :code "f")] ; used to display the genre
   :library-signature [:datafield (attr= :tag "091") :subfield (attr= :code "a")] ; Signaturen
   :product-number [:datafield (attr= :tag "024") :subfield (attr= :code "a")] ; MVL-Bestellnummer
   :price [:datafield (attr= :tag "024") :subfield (attr= :code "c")] ; Preis
   :price-on-request? [:datafield (attr= :tag "024") :subfield (attr= :code "d")] ; Preis auf Anfrage
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
   "k" :kinder-und-jugendbücher
   "x" :bücher-in-fremdsprachen})

(def genre-raw-to-ludo-genre
  "Mapping between genre-raw and ludo genre"
  {"l01" :lernspiel
   "l02" :solitairspiel
   "l03" :denkspiel
   "l04" :geschicklichkeitsspiel
   "l05" :kartenspiel
   "l06" :legespiel
   "l07" :rollenspiel
   "l08" :würfelspiel
   "l09" :ratespiel
   "l10" :bücher-über-spiel})

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
   "k04" :kinderbücher-ab-6
   "x01" :englisch
   "x02" :weitere-fremdsprachen
   "x03" :weitere-fremdsprachen
   "x04" :weitere-fremdsprachen
   "x05" :weitere-fremdsprachen
   "x09" :weitere-fremdsprachen})

(def format-raw-to-format
  "Mapping between format-raw and format"
  {"BR" :braille
   "DVD" :hörfilm
   "DY" :hörbuch
   "ER" :e-book
   "GD" :grossdruck
   "LU" :ludo
   "MN" :musiknoten
   "TB" :taktilesbuch
   "TH" :text-hörbuch})

;; FIXME: The following data changes once in a while (probably once a
;; year), so it would make sense to store it somewhere where the users
;; could configure it without the need for a recompile. I changed it
;; so that this data would be read from a database. However there is
;; also the validation that depends on this data. The validation
;; schema is built at compile time. TBH I'm not sure if the validation
;; schema could be built at run time. So the change has been rolled
;; back (see 9304cb01c31b6ed4b513bc7c404d8e5d463f606e).
(def producer-raw-to-producer
  "Mapping between producer-raw and producer"
  {1   "Blista, Marburg"
   2   "BBH, Berlin"
   4   "NBH, Hamburg"
   5   "BBH, München"
   6   "WBH, Münster"
   7   "Blindenhörbücherei des Saarlandes"
   8   "SBH, Stuttgart"
   9   "EBS, Marburg"
   10  "CFB, Hamburg"
   11  "DKBB, Bonn"
   12  "Stimme der Hoffnung, Alsbach-Hähnlein"
   13  "SBS, Zürich"
   24  "dzb lesen, Leipzig"
   25  "CAB, Landschlacht"
   26  "Reformierte Blindenpflege Zürich"
   27  "HSL, Kreuzlingen"
   30  "VzFB, Hannover"
   31  "BBI, Wien"
   32  "PVM, Bonn"
   33  "Zollikofen"
   34  "CBD, Wernigerode"
   40  "BIT, München"
   50  "BSVÖ, Wien"
   103 "Planer-Regis, Berkenthin"
   109 "ONCE, Madrid"
   112 "Freunde blinder und sehbehinderter Kinder, Hamburg"
   113 "The Princeton Braillists, Princeton"
   115 "Nota, Kopenhagen"
   117 "VA, Melbourne"
   118 "SALB, Grahamstown"
   119 "Visability, Victoria Park"
   121 "Bartkowski, München"
   123 "Braille-Kinderbücher, Düren"
   124 "Berlin, Anderes Sehen"
   128 "IKS, Kassel"
   140 "Velen Integrationsspiele, Neuwied"
   153 "Vogel, Hamburg"
   154 "Alexander Reuss, Schwetzingen/Strassburg"
   159 "Blindenunterrichtsanstalt, Frankfurt"
   242 "SZB, St. Gallen"
   248 "B. Lang, Freiburt im Üechtland"
   250 "Blindenunterichtsanstalt, Ilzach"
   305 "RNIB, London"
   350 "Biblioteca Italiana per Ciechi, Monza"
   401 "American Braille Press, Paris"
   402 "American Foundation for Overseas Blind, Paris"
   404 "Association pour nos Aveugles, Paris"
   405 "AVH, Paris"
   407 "Institut Nationale des Jounes Aveugles, Paris"
   490 "Stamperia Nazionale Braille, Florenz"
   508 "American Printing House for the Blind, Louisville"})

(def genre-code-to-genre
  "Mapping between genre-code and genre. Used for movies"
  {"I1" :spielfilm
   "I2" :dokumentarfilm
   "I3" :mundartfilm})

(defn braille-grade-raw-to-braille-grade
  "Mapping between braille-grade-raw and braille-grade"
  [grade]
  (when grade
    (condp #(string/starts-with? %2 %1) grade
      "k" :kurzschrift
      "v" :vollschrift
      "s" :schwarzschrift
      nil)))

(def braille-music-grade-raw-to-braille-grade
  "Mapping between braille-music-grade-raw and braille-grade"
  {"vd" :vollschrift ; Vollschrift Deutsch
   "vs" :vollschrift ; Vollschrift other language
   ;; Musikschrift, not valid really, but the MARC21 data isn't super
   ;; clean for music, so swallow this as well
   "ms" :vollschrift
   ;; now we're getting into the really weird cases: In very rare
   ;; occasions we file some stuff under braille-music that isn't
   ;; really music, e.g. books about braille music notation, so we
   ;; need to handle other contraction grades as well
   "kr" :kurzschrift})

(def target-audience-raw-to-target-audience
  "Mapping between raw target-audience and target-audience. See
  also [Marc21](https://www.loc.gov/marc/bibliographic/bd008b.html)"
  {\a :kinderbücher-ab-3
   \b :kinderbücher-ab-6
   \c :kinderbücher-ab-10
   \d :kinderbücher-ab-14
   \e :bücher-für-erwachsene})

(defn get-target-audience
  "Extract the target audience from a string `s` according to the rules
  of [008 - MARC
  21](https://www.loc.gov/marc/bibliographic/bd008b.html). Return nil
  if `s` doesn't comply with the standard."
  [s]
  (get target-audience-raw-to-target-audience (nth s 22)))

(defn parse-int [s]
   (when-let [number (and s (re-find #"\d+" s))]
     (Integer/parseInt number)))

(defn get-year
  "Grab the date year out of a string. Return nil if `s` cannot be
  parsed."
  [s]
  (when-let [year (and (string? s) (re-find #"\d{4}" s))]
    ;; we return a legacy date here so that time literals work and
    ;; stay compatible with what e already have in the database
    (time/java-date
     (time/with-zone
       (time/zoned-date-time (Integer/parseInt year)) "UTC"))))

(defn- replace-less-than-sign [s]
  (some-> s (string/replace #"<|>" {"<" "(" ">" ")"})))

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

(defn get-accompanying-material
  "Get the accompanying material for `item`. Use
  `accompanying-material` and `accompanying-material-legacy`. If
  `accompanying-material-legacy-other` use that instead of
  `accompanying-material-legacy`. Return `nil` if all of the input
  values are nil."
  [{:keys [accompanying-material accompanying-material-legacy accompanying-material-legacy-other]}]
  (let [materials [accompanying-material (or accompanying-material-legacy
                                             accompanying-material-legacy-other)]]
    (when (some some? materials)
      (->>
       materials
       (remove nil?)
       (string/join ", ")))))

(defn get-double-spaced
  "Determine whether the item is double-spaced. Return true if the
  `field` starts with \"Weitzeilig\", false otherwise."
  [field]
  (boolean (and field (re-find #"^Weitzeilig" field))))

(defn- construct-producer-brief [producer-long producer-place]
  "Return a producer-brief string based on `producer-long` and
  `producer-place`. Both of these can potentially be nil or blank."
  (some->>
   [producer-long producer-place]
   (remove nil?)
   (remove string/blank?)
   seq ; short-circuit if both producer-long and producer-place are
       ; nil or blank
   (string/join ", ")))

(defn get-producer-brief
  "Get the producer-brief. If `producer` contains a valid numerical
  value other than `0` then return the string from the lookup table in
  [[producer-raw-to-producer]]. If `producer` contains `0` or is nil
  then generate a brief from `producer-long` and `producer-place`."
  [{:keys [producer producer-long producer-place]}]
  (let [producer-key (parse-int producer)
        producer-brief (construct-producer-brief producer-long producer-place)]
    (cond
       ;; no valid producer key
      (nil? producer-key) producer-brief
      ;; the producer is known to be unknown: create a producer based
      ;; on producer-long and producer-place
      (= producer-key 0) producer-brief
      ;; the producer has a valid key. Return the brief string from
      ;; the lookup map.
      :else (producer-raw-to-producer producer-key))))

(defn clean-raw-item
  "Return a proper production based on a raw item, e.g.
  translate the language tag into proper ISO 639-1 codes"
  [{:keys [record-id source description source-publisher
           library-signature title subtitles name-of-part creator
           price price-on-request? product-number language format producer
           genre genre-code genre-text
           general-information
           produced-commercially? source-date general-note
           series-title series-volume series-type duration
           volumes narrators producer-long
           game-description double-spaced?
           braille-grade
           directed-by actors personel-text
           braille-music-grade ismn
           accompanying-material-images] :as raw-item
    :or {genre "" ; an invalid genre
         genre-code ""}}] ; an invalid genre-code
  (let [fmt (format-raw-to-format format)
        ;; print-and-braille books have a special series-type
        print-and-braille? (when (some #{"printundbraille"} series-type) true)
        fmt (cond
              ;; tactile books aren't properly tagged in the format
              ;; field. They are tagged as :ludo and their library
              ;; signature starts with "TB"
              (and (= fmt :ludo) library-signature
                   (re-find #"^TB " library-signature)) :taktilesbuch
              ;; tactile books that are only for sale do not have a
              ;; signature that starts with "TB". However if they are
              ;; print-and-braille? we also treat them as tactile
              ;; books
              (and (= fmt :ludo) print-and-braille?) :taktilesbuch
              :else fmt)
        item (-> {}
                 (assoc-some
                  :record-id record-id
                  :title (remove-nonprintable-chars title)
                  :subtitles (seq (map remove-nonprintable-chars subtitles))
                  :name-of-part (remove-nonprintable-chars name-of-part)
                  :creator (replace-less-than-sign creator)
                  :description description
                  :source-publisher source-publisher
                  :source source
                  :language (iso-639-2-to-iso-639-1 language)
                  :target-audience (get-target-audience general-information)
                  :genre (or (genre-raw-to-genre (trunc genre 1))
                             (genre-raw-to-ludo-genre (trunc genre 3))
                             (genre-code-to-genre (trunc genre-code 2)))
                  :sub-genre (genre-raw-to-subgenre (trunc genre 3))
                  :genre-text genre-text
                  :format fmt
                  :producer-brief (get-producer-brief raw-item)
                  :source-date (get-year source-date)
                  :library-signature library-signature
                  :product-number product-number
                  :price-on-request? (when (some? price-on-request?) true)
                  :price (if price-on-request? (layout/translations :price-on-request) price)))]
    (case fmt
      :hörbuch (-> item
                   (assoc-some
                    :produced-commercially? (some? produced-commercially?)
                    :duration (parse-int duration)
                    :narrators (not-empty (map normalize-name narrators))))
      :text-hörbuch (-> item
                        (assoc-some
                         :duration (parse-int duration)
                         :narrators (not-empty (map normalize-name narrators))
                         :accompanying-material (get-accompanying-material raw-item)))
      :braille (let [rucksackbuch-number (and series-title
                                              (re-find #"^Rucksackbuch" series-title)
                                              (parse-int series-volume))
                     braille-grade (braille-grade-raw-to-braille-grade braille-grade)]
                 (-> item
                     (assoc-some
                      :rucksackbuch-number rucksackbuch-number
                      :rucksackbuch? (boolean rucksackbuch-number)
                      :braille-grade braille-grade
                      :double-spaced? (get-double-spaced double-spaced?)
                      :volumes (parse-int volumes)
                      :accompanying-material (get-accompanying-material raw-item)
                      :print-and-braille? print-and-braille?)))
      :grossdruck (-> item
                      (assoc-some
                       :volumes (parse-int volumes)))
      :hörfilm (-> item
                   (assoc-some
                    :movie_country (and general-note
                                        (second (re-find #"^Originalversion: (.*)$" general-note)))
                    :producer producer-long
                    :directed-by directed-by
                    :actors actors
                    :personel-text personel-text))
      :ludo (-> item
                (assoc-some
                 :game-description game-description
                 :accompanying-material (get-accompanying-material raw-item)
                 ;; some games are braillified
                 :braille-grade (braille-grade-raw-to-braille-grade braille-grade)))

      :e-book (-> item
                  (assoc-some
                   :accompanying-material accompanying-material-images))
      :musiknoten (-> item
                      (assoc-some
                       ;; for musiknoten we want the source to correspond to the International Standard
                       ;; Music Number, so that collation works as intended
                       :source ismn
                        ;; first try to decode the braille-music-grade. Next check if the product-number
                        ;; starts with "SS". Otherwise set the braille-grade to nil.
                       :braille-grade (or (braille-music-grade-raw-to-braille-grade braille-music-grade)
                                          (when (and (string? product-number)
                                                     (re-find #"^SS\d{5,}" product-number)) :schwarzschrift))
                       :volumes (parse-int volumes)
                       :accompanying-material (get-accompanying-material raw-item)))
      :taktilesbuch (-> item
                        (assoc-some
                         :braille-grade (braille-grade-raw-to-braille-grade braille-grade)
                         :double-spaced? (get-double-spaced double-spaced?)
                         :accompanying-material (get-accompanying-material raw-item)
                         :volumes (parse-int volumes)
                         :print-and-braille? print-and-braille?))
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
        spiele-items (filter #(= (:format %) :ludo) items)
        others (remove #(#{:braille :musiknoten :taktilesbuch :ludo} (:format %)) items)]
    (concat
     (mapcat collate-duplicate-items [braille-items musiknoten-items taktile-items spiele-items])
     others)))

(defn- locale-compare
  "Comparator similar to `clojure.core/compare`. Uses a locale aware
  collator for string comparison. Unlike `clojure.core/compare` treats
  `nil` as 'greater than', so that it comes last in the sort order.
  Numbers are smaller than strings to that they come first in sort
  order."
  [x y]
  (cond
    (every? string? [x y]) (. collator compare x y)
    (and (nil? x) (nil? y)) 0
    (nil? x) -1
    (nil? y) 1
    ;; when trying to compare strings and numbers make sure that
    ;; numbers are ordered before strings
    (and (string? x) (number? y)) 1
    (and (number? x) (string? y)) -1
    :else (compare x y)))

(defn- chunkify
  "Split a string `s` into a sequence of strings and numbers. This is
  needed for [natural sort
  order](http://www.davekoelle.com/alphanum.html)"
  [s]
  (some->>
   s
   (re-seq #"\d+|\D+")
   (map #(or (parse-int %) %))))

(defn- locale-comparator [x y]
  "Compare two sequences `x` and `y` using the locale aware
  `locale-compare`. See also `cmp-seq-lexi` in
  http://www.clojure.org/guides/comparators"
  (loop [x x
         y y]
    (if (seq x)
      (if (seq y)
        (let [x1 (first x)
              y1 (first y)
              c (if (every? seq? [x1 y1])
                  ;; a chunked string
                  (locale-comparator x1 y1)
                  ;; locale aware comparison
                  (locale-compare x1 y1))]
          (if (zero? c)
            (recur (rest x) (rest y))
            c))
        ;; else we reached end of y first, so x > y
        1)
      (if (seq y)
        ;; we reached end of x first, so x < y
        -1
        ;; Sequences contain same elements.  x = y
        0))))

(defn- get-update-keys
  "Return the update keys for a given item containing `format`,
  `genre` and `sub-genre`. The update-key determines where in the tree
  a particular catalog item will be inserted. This is used to group
  the catalog items."
  [{fmt :format genre :genre subgenre :sub-genre print-and-braille? :print-and-braille?}]
  (cond
    (= fmt :hörbuch) [fmt genre subgenre]
    (#{:hörfilm :ludo} fmt) [fmt]
    ;; file musiknoten under braille with the original format as genre
    (= fmt :musiknoten) [:braille fmt]
    ;; file tactile books and print-and-braille books under braille
    ;; with :print-and-braille as genre
    (or (= fmt :taktilesbuch) print-and-braille?) [:braille :print-and-braille]
    (= genre :kinder-und-jugendbücher) [fmt genre subgenre]
    :else [fmt genre]))

(defn get-update-keys-neu-als-hörbuch
  "Return the update keys for a given item (see [[get-update-keys]]).
  For the neu-als-hörbuch all foreign language books need to be
  grouped under `:bücher-in-fremdsprachen`, even kids books, so we
  need to group not only by `format`, `genre` and `sub-genre` but also
  by `language`."
  [{fmt :format language :language :as item}]
  (cond
    (and (= fmt :hörbuch)
         (not (#{"de" "de-CH"} language))) [fmt :bücher-in-fremdsprachen
                                            (if (= language "en")
                                              :englisch
                                              :weitere-fremdsprachen)]
    :else (get-update-keys item)))

(defn get-update-keys-hörfilm
  "Return the update keys for a given item (see [[get-update-keys]]).
  For the hörfilm catalog we need to group the :hörfilm items by genre."
  [{fmt :format genre :genre :as item}]
  (cond
    (#{:hörfilm} fmt) [fmt genre]
    :else (get-update-keys item)))

(defn get-update-keys-ludo
  "Return the update keys for a given item (see [[get-update-keys]]).
  For the ludo catalog we need to group all other formats under the
  genre :bücher-über-spiel."
  [{fmt :format genre :genre :as item}]
  (cond
    (not= fmt :ludo) [:ludo :bücher-über-spiel]
    :else [fmt genre]))

(defn get-update-keys-taktil
  "Return the update keys for a given item (see [[get-update-keys]]).
  For the taktil catalog we group the :taktilesbuch items by genre."
  [{fmt :format genre :genre :as item}]
  (cond
    (#{:taktilesbuch} fmt) [fmt genre]
    :else (get-update-keys item)))

(defn get-update-keys-print-and-braille
  "Return the update keys for a given item (see [[get-update-keys]]).
  For the print-and-braille catalog we group the
  :print-and-braille items by target-audience."
  [{fmt :format target-audience :target-audience
    print-and-braille? :print-and-braille? :as item}]
  ;; file the items under the format :print-and-braille and use the
  ;; target-audience as genre.
  (cond
    print-and-braille? [:print-and-braille target-audience]
    :else (get-update-keys item)))

(def sort-order
  "A hashmap that defines the sort order for a given key. Typically
  formats, genres and subgenres are defined in here as keys with the
  value being the order in which they are to be sorted."
  (apply hash-map
         (interleave
          (concat [:editorial :recommendation]
                  layout/formats layout/genres
                  layout/subgenres
                  [:recommendations])
          (iterate inc 0))))

(defn- by-format-genre-subgenre [x y]
  (compare (sort-order x) (sort-order y)))

(defn- update-in-sorted
  "Similar to `update-in` but adds sortedmaps instead of hashmaps for
  non-existing levels."
  ([m [k & ks] f & args]
   (let [empty-map (sorted-map-by by-format-genre-subgenre)]
     (if ks
       (assoc m k (apply update-in-sorted (get m k empty-map) ks f args))
       (assoc m k (apply f (get m k) args))))))

(defn- sort-key
  "Return a sort key that will sort an item by `creator`, `title`,
  `subtitles` and `name-of-part`. If there is no `creator` then sort
  just by `title`. Likewise if there are no `subtitles` then sort by
  `name-of-part`. All strings are chunkified, i.e. split into strings
  and numbers to enable [natural
  sort](http://www.davekoelle.com/alphanum.html)"
  [{:keys [creator title subtitles name-of-part]}]
  (->> [(or creator title)
        title
        (or (and subtitles (string/join subtitles)) name-of-part)
        name-of-part]
       (map chunkify)))

(defn duplicate-print-and-braille-items
  "Given a list of `items` return the list while duplicating the
  `:braille` format items that have the `:print-and-braille?`
  attribute. They are duplicated such that the original is kept as is
  and the second item is like the original but doesn't have the
  `:print-and-braille?` attribute. This will make sure
  `:print-and-braille?` items are shown under both Braille and
  Print&Braille in the catalog"
  [items]
  (mapcat
   (fn [item]
     (if (and (= (:format item) :braille) (:print-and-braille? item))
       [item (dissoc item :print-and-braille?)]
       [item]))
   items))

(defn order
  "Order the catalog `items`. Items representing the same book, e.g.
  different Braille versions of the same book are collated."
  [items]
  (->> items
   collate-all-duplicate-items
   (sort-by sort-key locale-comparator)))

(defn group
  "Group the catalog `items`. Given a sequence of `items` returns a tree
  where all items are grouped by format, genre and sometimes even
  subgenre. The exact details of the grouping are determined by the
  `get-update-keys-fn`."
  ([items]
   (group items get-update-keys))
  ([items get-update-keys-fn]
   (reduce
    (fn [m item]
      (let [update-keys (get-update-keys-fn item)]
        (update-in-sorted m update-keys (fnil conj []) item)))
    (sorted-map-by by-format-genre-subgenre)
    items)))

(defn order-and-group
  "Order and group the catalog `items`. Given a sequence of `items`
  returns a tree where all items are grouped by format, genre and
  sometimes even subgenre. The exact details of the grouping are
  determined by the `get-update-keys-fn`."
  ([items]
   (order-and-group items get-update-keys))
  ([items get-update-keys-fn]
   (-> items
    order
    (group get-update-keys-fn))))

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
   (apply xml1-> record path) text string/trim ignore-sine-nomine unicode/normalize))

(defn get-multi-subfields
  "Get a list of subfield texts for the given `path` in the given
  `record`. Returns an empty list if there is no such subfield"
  [record path]
  (some->>
   (apply xml-> record path) (map text) (map string/trim) (map ignore-sine-nomine)))

(defn text-raw
  "Similar
  to [clojure.data.zip/text](https://clojure.github.io/data.zip/#clojure.data.zip.xml/text)
  but doesn't normalize the text"
  [loc]
  (apply str (xml-> loc data-zip/descendants zip/node string?)))

(defn get-controlfield
  "Same as [[get-subfield]] but returns the text unaltered"
  [record path]
  (some->
   (apply xml1-> record path) text-raw))

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
                             (#{:narrators :subtitles :series-type} key) (get-multi-subfields record path)
                             (#{:general-information} key) (get-controlfield record path)
                             :else (get-subfield record path))]
                 :when (some? val)]
             [key val])
           (into {})
           (merge (get-personel-fields record))
           clean-raw-item))))
