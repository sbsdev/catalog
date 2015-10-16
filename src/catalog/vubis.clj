(ns catalog.vubis
  "Import XML files from the library system"
  (:require [clojure.java.io :as io]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :refer [xml-> xml1-> attr= text]]
            [medley.core :refer [assoc-some]]))

(def ^:private param-mapping
  "Mapping from Marc21 XML to parameters. See [MARC 21 Format for Bibliographic Data](http://www.loc.gov/marc/bibliographic/)"
  {:record-id [:controlfield (attr= :tag "001")]
   :title [:datafield (attr= :tag "245") :subfield (attr= :code "a")]
   :subtitle [:datafield (attr= :tag "245") :subfield (attr= :code "b")]
   :creator [:datafield (attr= :tag "100") :subfield]
   :source [:datafield (attr= :tag "020") :subfield]
   :description [:datafield (attr= :tag "520") :subfield]
   :library_signature [:datafield (attr= :tag "091") :subfield (attr= :code "a")]
   :source_publisher [:datafield (attr= :tag "534") :subfield (attr= :code "c")]
   :source_date [:datafield (attr= :tag "534") :subfield (attr= :code "d")]
   :language [:datafield (attr= :tag "041") :subfield (attr= :code "a")]
   :general-note [:datafield (attr= :tag "500") :subfield (attr= :code "a")]
   :personel-name [:datafield (attr= :tag "700") :subfield (attr= :code "a")]
   :personel-relator-term [:datafield (attr= :tag "700") :subfield (attr= :code "e")]
   :narrator [:datafield (attr= :tag "709") :subfield (attr= :code "a")]
   :duration [:datafield (attr= :tag "391") :subfield (attr= :code "a")]
   :producer [:datafield (attr= :tag "260") :subfield (attr= :code "b")]
   :producer_place [:datafield (attr= :tag "260") :subfield (attr= :code "a")]
   :produced_date [:datafield (attr= :tag "260") :subfield (attr= :code "c")]
   :produced_commercially [:datafield (attr= :tag "260") :subfield (attr= :code "d")]
   :format-raw [:datafield (attr= :tag "091") :subfield (attr= :code "c")]
   :genre-raw [:datafield (attr= :tag "099") :subfield (attr= :code "b")]
   :price [:datafield (attr= :tag "024") :subfield (attr= :code "c")]})

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
   "MN" :musiknoten})

(defn get-subfield
  "Get the subfield text for the given `path` in the given `record`.
  Returns nil if there is no such subfield"
  [record path]
  (some-> (apply xml1-> record path) text))

(defn clean-raw-item
  "Return a proper production based on a raw item, i.e.
  translate the language tag into proper ISO 639-1 codes"
  [{:keys [genre-raw language format-raw] :as item :or {genre-raw "x01"}}]
  (-> item
      (assoc-some
       :language (iso-639-2-to-iso-639-1 language)
       :genre (genre-raw-to-genre (subs genre-raw 0 1))
       :sub-genre (genre-raw-to-subgenre (subs genre-raw 0 3))
       :format (format-raw-to-format format-raw))
      (dissoc :genre-raw :format-raw)))

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
