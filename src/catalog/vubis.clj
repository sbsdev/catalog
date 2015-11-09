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
            [medley.core :refer [assoc-some]]
            [schema.core :as s]))

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
   :accompanying_material [:datafield (attr= :tag "300") :subfield (attr= :code "e")] ; Begleitmaterial
   :personel-name [:datafield (attr= :tag "700") :subfield (attr= :code "a")] ; Regie oder Darsteller
   :personel-relator-term [:datafield (attr= :tag "700") :subfield (attr= :code "e")]
   :narrator [:datafield (attr= :tag "709") :subfield (attr= :code "a")] ; Sprecher
   :duration [:datafield (attr= :tag "391") :subfield (attr= :code "a")] ; Spieldauer
   :braille-grade-raw [:datafield (attr= :tag "392") :subfield (attr= :code "a")] ; Schriftart Braille
   :braille-music-grade-raw [:datafield (attr= :tag "393") :subfield (attr= :code "a")] ; Schriftart Braille
   :producer [:datafield (attr= :tag "260") :subfield (attr= :code "b")] ; Produzent
   :producer-raw [:datafield (attr= :tag "260") :subfield (attr= :code "9")] ; a number that determines Produzent Kürzel und Stadt
   :producer_place [:datafield (attr= :tag "260") :subfield (attr= :code "a")] ; Produzent Stadt
   :produced_date [:datafield (attr= :tag "260") :subfield (attr= :code "c")]
   :produced-commercially? [:datafield (attr= :tag "260") :subfield (attr= :code "d")] ; kommerziell?
   :series-title-raw [:datafield (attr= :tag "830") :subfield (attr= :code "a")] ; u.a. Rucksackbuch
   :series-volume-raw [:datafield (attr= :tag "830") :subfield (attr= :code "v")]
   :format-raw [:datafield (attr= :tag "091") :subfield (attr= :code "c")]
   :genre-raw [:datafield (attr= :tag "099") :subfield (attr= :code "b")]
   :genre-code [:datafield (attr= :tag "099") :subfield (attr= :code "a")] ; used for movie genre i.e. Filmkategorie
   :library-signature [:datafield (attr= :tag "091") :subfield (attr= :code "a")] ; Signaturen
   :product-number [:datafield (attr= :tag "024") :subfield (attr= :code "a")] ; MVL-Bestellnummer
   :price [:datafield (attr= :tag "024") :subfield (attr= :code "c")] ; Preis
   :game_category [:datafield (attr= :tag "024") :fixme] ; Spiel-Systematikgruppe
   :game_description [:datafield (attr= :tag "300") :subfield (attr= :code "a")] ; Beschreibung von Spielen
   :game_materials [:datafield (attr= :tag "024") :fixme] ; Spiel-Materialdetails
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

(def CatalogItem
  {:record-id s/Str
   :title s/Str
   (s/optional-key :subtitle) s/Str
   :creator s/Str
   (s/optional-key :description) s/Str
   :source-publisher s/Str
   :source-date s/Inst
   :language (apply s/enum (conj (vals iso-639-2-to-iso-639-1) "und"))
   (s/optional-key :genre) (apply s/enum (vals genre-raw-to-genre))
   (s/optional-key :subgenre) (apply s/enum (vals genre-raw-to-subgenre))
   :format (apply s/enum (vals format-raw-to-format))
   :producer-brief (apply s/enum (set (vals producer-raw-to-producer)))
   (s/optional-key :duration) s/Int
   :rucksackbuch? s/Bool
   :produced-commercially? s/Bool
   (s/optional-key :narrator) s/Str
   :library-signature s/Str
   :price s/Str
   (s/optional-key :braille_music_grade) s/Str
   })

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

(defn normalize-name
  "Change a name from 'name, surname' to 'surname name'"
  [name]
  (when name
    (string/join " " (reverse (string/split name #",")))))

(defn clean-raw-item
  "Return a proper production based on a raw item, i.e.
  translate the language tag into proper ISO 639-1 codes"
  [{:keys [genre-raw genre-code language format-raw producer-raw
           produced-commercially? source-date general-note
           series-title-raw series-volume-raw
           braille-grade-raw narrator] :as item
    :or {genre-raw "x01"}}]
  (-> item
      (assoc-some
       :language (iso-639-2-to-iso-639-1 language)
       :genre (or (genre-raw-to-genre (subs genre-raw 0 1))
                  (genre-code-to-genre (subs genre-code 0 2)))
       :sub-genre (genre-raw-to-subgenre (subs genre-raw 0 3))
       :format (format-raw-to-format format-raw)
       :producer-brief (producer-raw-to-producer (Integer/parseInt producer-raw))
       :produced-commercially? (some? produced-commercially?)
       :rucksackbuch-number (when (and series-title-raw
                                       (re-find #"^Rucksackbuch" series-title-raw))
                              (Integer/parseInt series-volume-raw))
       :braille-grade (braille-grade-raw-to-braille-grade braille-grade-raw)
       :source-date (get-year source-date)
       :narrator (normalize-name narrator)
       :movie_country (when general-note (second (re-find #"^Originalversion: (.*)$" general-note))))
      (dissoc :genre-raw :genre-code :format-raw :producer-raw
              :series-title-raw :series-volume-raw)))

(defn order-and-group [items]
  (->>
   items
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
