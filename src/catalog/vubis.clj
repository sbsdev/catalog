(ns catalog.vubis
  "Import XML files from the library system"
  (:require [clojure.java.io :as io]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :refer [xml-> xml1-> attr= text]]))

(def ^:private param-mapping
  {:title [:datafield (attr= :tag "245") :subfield (attr= :code "a")]
   :subtitle [:datafield (attr= :tag "245") :subfield (attr= :code "b")]
   :creator [:datafield (attr= :tag "100") :subfield]
   :source [:datafield (attr= :tag "020") :subfield]
   :description [:datafield (attr= :tag "520") :subfield]
   :library_signature [:datafield (attr= :tag "091") :subfield (attr= :code "a")]
   :source_publisher [:datafield (attr= :tag "534") :subfield (attr= :code "c")]
   :source_date [:datafield (attr= :tag "534") :subfield (attr= :code "d")]
   :language [:datafield (attr= :tag "041") :subfield (attr= :code "a")]
   :general-note [:datafield (attr= :tag "500") :subfield (attr= :code "a")]
   :type [:datafield (attr= :tag "091") :subfield (attr= :code "c")]
   :genre [:datafield (attr= :tag "099") :subfield (attr= :code "b")]})

(def iso-639-2-to-iso-639-1
  "Mapping between three letter codes of [ISO
  639-2](http://en.wikipedia.org/wiki/ISO_639-2) and the two letter
  codes of [ISO 639-1](http://en.wikipedia.org/wiki/ISO_639-1)"
  {"ger" "de"
   "gsw" "de-CH"
   "fre" "fr"
   "eng" "en"})

(def code-to-genre
  "Mapping between genrecode and genre"
  {"b" :belletristik
   "s" :sachbücher
   "k" :kinder-und-jugendbücher})

(def code-to-subgenre
  "Mapping between genrecode and subgenre"
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

(defn get-subfield
  "Get the subfield text for the given `path` in the given `record`.
  Returns nil if there is no such subfield"
  [record path]
  (some-> (apply xml1-> record path) text))

(defn clean-raw-item
  "Return a proper production based on a raw item, i.e.
  translate the language tag into proper ISO 639-1 codes"
  [{genre :genre language :language :as item :or {genre "x01"}}]
  (-> item
      (assoc :language (iso-639-2-to-iso-639-1 language))
      (assoc :genre (code-to-genre (subs genre 0 1)))
      (assoc :sub-genre (code-to-subgenre (subs genre 0 3)))))

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
