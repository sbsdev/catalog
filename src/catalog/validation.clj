(ns catalog.validation
  (:require [catalog.vubis :as vubis]
            [schema.core :as s]
            [schema.experimental.abstract-map :as abstract-map]))

(s/defschema CatalogItem
  (abstract-map/abstract-map-schema
   :format
   {:record-id s/Str
    (s/optional-key :creator) s/Str
    :title s/Str
    (s/optional-key :subtitle) s/Str
    (s/optional-key :name-of-part) s/Str
    :source s/Str
    :source-publisher s/Str
    :source-date s/Inst
    :genre (apply s/enum (vals vubis/genre-raw-to-genre))
    :sub-genre (apply s/enum (vals vubis/genre-raw-to-subgenre))
    (s/optional-key :description) s/Str
    :producer-brief (apply s/enum (set (vals vubis/producer-raw-to-producer)))
    :library-signature s/Str
    (s/optional-key :product-number) s/Str
    (s/optional-key :price) s/Str
    (s/optional-key :accompanying_material) s/Str
    :language (apply s/enum (conj (vals vubis/iso-639-2-to-iso-639-1) "und"))
    }))

(abstract-map/extend-schema
 Hörbuch CatalogItem [:hörbuch]
 {:duration s/Int
  :narrators [s/Str]
  :produced-commercially? s/Bool})

(abstract-map/extend-schema
 Braille CatalogItem [:braille]
 {:rucksackbuch? s/Bool
  (s/optional-key :rucksackbuch-number) s/Int
  :braille-grade (apply s/enum (vals vubis/braille-grade-raw-to-braille-grade))
  :volumes s/Str})

(abstract-map/extend-schema
 Taktil CatalogItem [:taktilesbuch]
 {})

(abstract-map/extend-schema
 Musiknoten CatalogItem [:musiknoten]
 {})

(abstract-map/extend-schema
 Grossdruck CatalogItem [:grossdruck]
 {:volumes s/Str})

(abstract-map/extend-schema
 E-book CatalogItem [:e-book]
 {})

(abstract-map/extend-schema
 Hörfilm CatalogItem [:hörfilm]
 {:producer s/Str
  :personel-name s/Str
  :movie_country s/Str})

(abstract-map/extend-schema
 Spiel CatalogItem [:ludo]
 {:game-category s/Str
  :game-description s/Str
  :game-materials s/Str})

