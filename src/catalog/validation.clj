(ns catalog.validation
  (:require [catalog.vubis :as vubis]
            [schema.core :as s]
            [schema.experimental.abstract-map :as abstract-map]))

(def Language
  (apply s/enum (conj (vals vubis/iso-639-2-to-iso-639-1) "und")))

(def Genre
  (apply s/enum (vals vubis/genre-raw-to-genre)))

(def SubGenre
  (apply s/enum (vals vubis/genre-raw-to-subgenre)))

(def ProducerBrief
  (apply s/enum (set (vals vubis/producer-raw-to-producer))))

(def BrailleGrade
  (apply s/enum (vals vubis/braille-grade-raw-to-braille-grade)))

(def SignatureRE #"(DS |GDB |BG |ED |BM |BK |PS|DY|GD)\d{4,6}")

(def SignatureTuple
  [(s/one SignatureRE "signature")
   (s/one (s/maybe BrailleGrade) "grade")
   (s/one (s/maybe s/Int) "volumes")
   (s/one (s/maybe s/Bool) "double-spaced?")
   (s/one (s/maybe s/Str) "accompanying-material")])

(def SignatureKey
  [(s/one (s/maybe (apply s/enum (vals vubis/braille-grade-raw-to-braille-grade))) "grade")
   (s/one s/Bool "double-spaced?")])

(def LibrarySignature
  (s/if map? {SignatureKey [SignatureTuple]} SignatureRE))

(s/defschema CatalogItem
  (abstract-map/abstract-map-schema
   :format
   {:record-id s/Str
    (s/optional-key :creator) s/Str
    :title s/Str
    (s/optional-key :subtitles) [s/Str]
    (s/optional-key :name-of-part) s/Str
    (s/optional-key :source) s/Str
    (s/optional-key :description) s/Str
    :library-signature LibrarySignature
    (s/optional-key :product-number) LibrarySignature
    (s/optional-key :price) s/Str
    (s/optional-key :language) Language
    }))

(abstract-map/extend-schema
 Hörbuch CatalogItem [:hörbuch]
 {:source-publisher s/Str
  :source-date s/Inst
  :genre Genre
  :sub-genre SubGenre
  :genre-text s/Str
  :producer-brief ProducerBrief
  :duration s/Int
  :narrators [s/Str]
  :produced-commercially? s/Bool})

(abstract-map/extend-schema
 Braille CatalogItem [:braille]
 {:source-publisher s/Str
  :source-date s/Inst
  :genre Genre
  :sub-genre SubGenre
  :genre-text s/Str
  :producer-brief ProducerBrief
  :rucksackbuch? s/Bool
  (s/optional-key :rucksackbuch-number) s/Int})

(abstract-map/extend-schema
 Taktil CatalogItem [:taktilesbuch]
 {:source-publisher s/Str
  :source-date s/Inst
  :genre Genre
  :sub-genre SubGenre
  :genre-text s/Str
  :producer-brief ProducerBrief})

(abstract-map/extend-schema
 Musiknoten CatalogItem [:musiknoten]
 {:source-publisher s/Str
  :source-date s/Inst
  :producer-brief ProducerBrief
  :genre-text s/Str
  (s/optional-key :volumes) s/Int})

(abstract-map/extend-schema
 Grossdruck CatalogItem [:grossdruck]
 {:source-publisher s/Str
  :source-date s/Inst
  :genre Genre
  :sub-genre SubGenre
  :genre-text s/Str
  :producer-brief ProducerBrief
  :volumes s/Int})

(abstract-map/extend-schema
 E-book CatalogItem [:e-book]
 {:source-publisher s/Str
  :source-date s/Inst
  :producer-brief ProducerBrief
  :genre Genre
  :sub-genre SubGenre
  :genre-text s/Str})

(abstract-map/extend-schema
 Hörfilm CatalogItem [:hörfilm]
 {:genre (apply s/enum (vals vubis/genre-code-to-genre))
  :genre-text s/Str
  :producer s/Str
  :directed-by [s/Str]
  :actors [s/Str]
  :movie_country s/Str})

(abstract-map/extend-schema
 Spiel CatalogItem [:ludo]
 {:source-publisher s/Str
  :source-date s/Inst
  :genre (s/eq :spiel)
  :genre-text s/Str
  (s/optional-key :accompanying-material) s/Str
  :game-description s/Str})

(defn distinct-titles? [items]
  (and (> (count items) 1)
       (->> items
            (map :title)
            (apply distinct?))))
