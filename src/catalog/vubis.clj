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
   :volumes-raw [:datafield (attr= :tag "300") :subfield (attr= :code "9")] ; Anzahl Medien
   :source-publisher [:datafield (attr= :tag "534") :subfield (attr= :code "c")] ; Verlag
   :source-date-raw [:datafield (attr= :tag "534") :subfield (attr= :code "d")] ; Erscheinungsjahr
   :language [:datafield (attr= :tag "041") :subfield (attr= :code "a")] ; Sprache
   :general-note [:datafield (attr= :tag "500") :subfield (attr= :code "a")] ; Land, Erscheinungsjahr (des Originalfilms)
   :accompanying_material [:datafield (attr= :tag "300") :subfield (attr= :code "e")] ; Begleitmaterial
   :personel-name [:datafield (attr= :tag "700") :subfield (attr= :code "a")] ; Regie oder Darsteller
   :personel-relator-term [:datafield (attr= :tag "700") :subfield (attr= :code "e")]
   :narrator [:datafield (attr= :tag "709") :subfield (attr= :code "a")] ; Sprecher
   :duration [:datafield (attr= :tag "391") :subfield (attr= :code "a")] ; Spieldauer
   :braille-grade-raw [:datafield (attr= :tag "392") :subfield (attr= :code "a")] ; Schriftart Braille
   :braille-music-grade-raw [:datafield (attr= :tag "393") :subfield (attr= :code "a")] ; Schriftart Braille
   :producer-long-raw [:datafield (attr= :tag "260") :subfield (attr= :code "b")] ; Produzent
   :producer-raw [:datafield (attr= :tag "260") :subfield (attr= :code "9")] ; a number that determines Produzent Kürzel und Stadt
   :producer-place-raw [:datafield (attr= :tag "260") :subfield (attr= :code "a")] ; Produzent Stadt
   :produced-date-raw [:datafield (attr= :tag "260") :subfield (attr= :code "c")]
   :produced-commercially-raw? [:datafield (attr= :tag "260") :subfield (attr= :code "d")] ; kommerziell?
   :series-title-raw [:datafield (attr= :tag "830") :subfield (attr= :code "a")] ; u.a. Rucksackbuch
   :series-volume-raw [:datafield (attr= :tag "830") :subfield (attr= :code "v")]
   :format-raw [:datafield (attr= :tag "091") :subfield (attr= :code "c")]
   :genre-raw [:datafield (attr= :tag "099") :subfield (attr= :code "b")]
   :genre-code [:datafield (attr= :tag "099") :subfield (attr= :code "a")] ; used for movie genre i.e. Filmkategorie
   :library-signature [:datafield (attr= :tag "091") :subfield (attr= :code "a")] ; Signaturen
   :product-number [:datafield (attr= :tag "024") :subfield (attr= :code "a")] ; MVL-Bestellnummer
   :price [:datafield (attr= :tag "024") :subfield (attr= :code "c")] ; Preis
   :game-category-raw [:datafield (attr= :tag "024") :fixme] ; Spiel-Systematikgruppe
   :game-description-raw [:datafield (attr= :tag "300") :subfield (attr= :code "a")] ; Beschreibung von Spielen
   :game-materials-raw [:datafield (attr= :tag "024") :fixme] ; Spiel-Materialdetails
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

(defn get-subfield
  "Get the subfield text for the given `path` in the given `record`.
  Returns nil if there is no such subfield"
  [record path]
  (some-> (apply xml1-> record path) text string/trim))

(defn get-year
  "Grab the date year out of a string. Return nil if `s` cannot be
  parsed."
  [s]
  (when-let [year (and s (re-find #"\d{4}" s))]
    (time.coerce/to-date
     (time.format/parse (time.format/formatters :year) year))))

(defn get-duration [s]
  (when-let [duration (and s (re-find #"\d+" s))]
    (Integer/parseInt s)))

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

(defn clean-raw-item
  "Return a proper production based on a raw item, i.e.
  translate the language tag into proper ISO 639-1 codes"
  [{:keys [genre-raw genre-code language format-raw producer-raw
           produced-commercially-raw? source-date-raw general-note
           series-title-raw series-volume-raw duration
           volumes-raw narrator producer-long-raw
           game-category-raw game-description-raw game-materials-raw
           braille-grade-raw title] :as item
    :or {genre-raw "x01"}}]
  (let [rucksackbuch-number (when (and series-title-raw
                                       (re-find #"^Rucksackbuch" series-title-raw))
                              (Integer/parseInt series-volume-raw))
        fmt (format-raw-to-format format-raw)]
    (-> item
        (assoc-some
         :title (remove-nonprintable-chars title)
         :language (iso-639-2-to-iso-639-1 language)
         :genre (or (genre-raw-to-genre (subs genre-raw 0 1))
                    (genre-code-to-genre (subs genre-code 0 2)))
         :sub-genre (genre-raw-to-subgenre (subs genre-raw 0 3))
         :format fmt
         :producer-brief (producer-raw-to-producer (Integer/parseInt producer-raw))
         :produced-commercially? (when (= fmt :hörbuch) (some? produced-commercially-raw?))
         :rucksackbuch-number (when (= fmt :braille) rucksackbuch-number)
         :rucksackbuch? (when (= fmt :braille) (if rucksackbuch-number true false))
         :duration (when (= fmt :hörbuch) (get-duration duration))
         :braille-grade (when (= fmt :braille) (braille-grade-raw-to-braille-grade braille-grade-raw))
         :source-date (get-year source-date-raw)
         :narrator (when (= fmt :hörbuch) (normalize-name narrator))
         :volumes (when (#{:braille :grossdruck} fmt) volumes-raw)
         :movie_country (when general-note (second (re-find #"^Originalversion: (.*)$" general-note)))
         :producer (when (= fmt :hörfilm) producer-long-raw)
         :game-category (when (= fmt :ludo) game-category-raw)
         :game-description (when (= fmt :ludo) game-description-raw)
         :game-materials (when (= fmt :ludo) game-materials-raw))
        (dissoc :genre-raw :genre-code :format-raw :producer-raw
                :series-title-raw :series-volume-raw :source-date-raw
                :produced-commercially-raw? :braille-grade-raw
                :volumes-raw :producer-long-raw
                :produced-date-raw :producer-place-raw
                :game-category-raw :game-description-raw :game-materials-raw
                :personel-name :personel-relator-term))))

(defn select-vals
  "Return a list of the values for the given keys `ks` from `item` if
  the value of the first key is non-nil"
  [ks item]
  (when ((first ks) item)
    (-> item (select-keys ks) vals)))

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
         (map #(select-vals [:product-number :braille-grade :volumes] %))
         (remove empty?)
         (group-by second)
         not-empty) ; only pick entries that are non-empty
    :library-signature
    (->> items
         (map #(select-vals [:library-signature :braille-grade :volumes] %))
         (remove empty?)
         (group-by second)
         not-empty)) ; only pick entries that are non-empty
   (dissoc :volumes :braille-grade)))

(defn collate-duplicate-items
  "Merge duplicate braille `items` into one. Typically we have
  multiple entries for the same braille book for all the different
  braille grades. In the catalog we want one entry"
  [items]
  (let [braille-items (filter #(= (:format %) :braille) items)
        others (remove #(= (:format %) :braille) items)]
    (->> braille-items
         (group-by (juxt :source :title))
         vals
         (map #(merge-braille-catalog-items %))
         (concat others))))

(defn order-and-group [items]
  (->>
   items
   collate-duplicate-items
   (sort-by (juxt :creator :title))
   (reduce
    (fn [m {:keys [format genre sub-genre] :as item}]
      (let [update-keys
            (cond
              (= format :hörbuch) [format genre sub-genre]
              (#{:hörfilm :ludo} format) [format]
              (= genre :kinder-und-jugendbücher) [format genre sub-genre]
              :else [format genre])]
        (update-in m update-keys (fnil conj []) item))) {})))

(defn read-file
  "Read an export file from VUBIS and return a map with all the data"
  [file]
  (let [root (-> file io/file xml/parse zip/xml-zip)]
    (for [record (xml-> root :record)]
      (->> (for [[key path] param-mapping
                 :let [val (get-subfield record path)]
                 :when (some? val)]
             [key val])
           (into {})
           clean-raw-item))))
