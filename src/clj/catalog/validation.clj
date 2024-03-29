(ns catalog.validation
  "Validation for catalog data

  Items come from the library system and are converted into clj data
  structures from XML. This tree is then validated."
  (:require [catalog.vubis :as vubis]
            [schema.core :as s]
            [schema.experimental.abstract-map :as abstract-map]))

(def Language
  (apply s/enum (conj (vals vubis/iso-639-2-to-iso-639-1) "und")))

(def Genre
  (apply s/enum (vals vubis/genre-raw-to-genre)))

(def LudoGenre
  (apply s/enum (vals vubis/genre-raw-to-ludo-genre)))

(def SubGenre
  (apply s/enum (vals vubis/genre-raw-to-subgenre)))

(def ProducerBrief
  (apply s/enum (set (vals vubis/producer-raw-to-producer))))

(def TargetAudience
  (apply s/enum (set (vals vubis/target-audience-raw-to-target-audience))))

(def BrailleGrade (s/enum :kurzschrift :vollschrift :schwarzschrift))

(def SignatureRE #"(DS |GDB |BG |ED |BM |BK |PS|DY|GD)\d{4,6}|LUD \d{1,3}|BK \d{3}|VI \d{1,3}|TB \d{1,3}|TH \d{1,6}")

(def SignatureTuple
  [(s/one SignatureRE "signature")
   (s/one (s/maybe BrailleGrade) "grade")
   (s/one (s/maybe s/Int) "volumes")
   (s/one (s/maybe s/Bool) "double-spaced?")
   (s/one (s/maybe s/Str) "accompanying-material")])

(def SignatureKey
  [(s/one (s/maybe BrailleGrade) "grade")
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
    (s/optional-key :source-date) s/Inst
    (s/optional-key :description) s/Str
    :library-signature LibrarySignature
    (s/optional-key :product-number) LibrarySignature
    (s/optional-key :price) s/Str
    (s/optional-key :price-on-request?) s/Bool
    (s/optional-key :language) Language
    }))

(abstract-map/extend-schema
 Hörbuch CatalogItem [:hörbuch]
 {:source-publisher s/Str
  :genre Genre
  :sub-genre SubGenre
  :genre-text s/Str
  :producer-brief ProducerBrief
  :target-audience TargetAudience
  :duration s/Int
  :narrators [s/Str]
  :produced-commercially? s/Bool})

(abstract-map/extend-schema
 TextHörbuch CatalogItem [:text-hörbuch]
 {:source-publisher s/Str
  :genre Genre
  :sub-genre SubGenre
  :genre-text s/Str
  :producer-brief ProducerBrief
  :target-audience TargetAudience
  :duration s/Int
  :narrators [s/Str]
  (s/optional-key :accompanying-material) s/Str})

(abstract-map/extend-schema
 Braille CatalogItem [:braille]
 {:source-publisher s/Str
  :genre Genre
  :sub-genre SubGenre
  :genre-text s/Str
  :producer-brief ProducerBrief
  :target-audience TargetAudience
  :rucksackbuch? s/Bool
  (s/optional-key :rucksackbuch-number) s/Int
  (s/optional-key :print-and-braille?) s/Bool})

(abstract-map/extend-schema
 Taktil CatalogItem [:taktilesbuch]
 {:source-publisher s/Str
  :genre Genre
  :sub-genre SubGenre
  :genre-text s/Str
  :producer-brief ProducerBrief
  :target-audience TargetAudience
  (s/optional-key :print-and-braille?) s/Bool})

(abstract-map/extend-schema
 Musiknoten CatalogItem [:musiknoten]
 {:source-publisher s/Str
  :producer-brief ProducerBrief
  :genre-text s/Str
  (s/optional-key :volumes) s/Int})

(abstract-map/extend-schema
 Grossdruck CatalogItem [:grossdruck]
 {:source-publisher s/Str
  :genre Genre
  :sub-genre SubGenre
  :genre-text s/Str
  :producer-brief ProducerBrief
  :target-audience TargetAudience
  :volumes s/Int})

(abstract-map/extend-schema
 E-book CatalogItem [:e-book]
 {:source-publisher s/Str
  :producer-brief ProducerBrief
  :target-audience TargetAudience
  :genre Genre
  :sub-genre SubGenre
  :genre-text s/Str
  (s/optional-key :accompanying-material) s/Str})

(abstract-map/extend-schema
 Hörfilm CatalogItem [:hörfilm]
 {:genre (apply s/enum (vals vubis/genre-code-to-genre))
  :genre-text s/Str
  :target-audience TargetAudience
  :producer s/Str
  :producer-brief s/Str ;; for :hörfilm allow any string as :producer-brief
  :directed-by [s/Str]
  :actors [s/Str]
  :personel-text s/Str
  :movie_country s/Str})

(abstract-map/extend-schema
 Spiel CatalogItem [:ludo]
 {:source-publisher s/Str
  :producer-brief ProducerBrief
  :target-audience TargetAudience
  :genre LudoGenre
  :genre-text s/Str
  (s/optional-key :accompanying-material) s/Str
  :game-description s/Str})

(defn distinct-titles? [items]
  (and (> (count items) 1)
       (->> items
            (map :title)
            (apply distinct?))))
