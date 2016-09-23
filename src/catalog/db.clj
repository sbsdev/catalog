(ns catalog.db
  "Persistence for catalog items"
  (:require [clojure.edn :as edn]
            [yesql.core :refer [defqueries]]))

(def ^:private db {:name "java:jboss/datasources/catalog"})

#_(def ^:private db "jdbc:mysql://localhost:3306/catalog?user=catalog&serverTimezone=UTC")

(defqueries "catalog/queries.sql" {:connection db})

(defn read-catalog
  "Return a coll of items for given `year` and `issue`"
  [year issue]
  (-> {:year year :issue issue}
      catalog
      first
      :items
      edn/read-string))

(defn read-editorial
  "Return the editorial as a markdown string for given `year`, `issue`
  and catalog `type`"
  [year issue type]
  (-> {:year year :issue issue :catalog_type (name type)}
      editorial
      first
      :content))

(defn read-recommendation [year issue type]
  "Return the recommendation as a markdown string for given `year`,
  `issue` and catalog `type`"
  (-> {:year year :issue issue :catalog_type (name type)}
      recommendation
      first
      :content))

(defn read-full-catalog [year type]
  "Return a coll of items for the full catalog for given `year` and catalog `type`"
  (-> {:year year :catalog_type (name type)}
      full-catalog
      first
      :items
      edn/read-string))

(defn save-catalog! [year issue items]
  "Persist `items` with given `year` and `issue`"
  (-> {:year year :issue issue :items (prn-str items)}
      save-catalog-internal!))

(defn save-editorial! [year issue catalog_type content]
  "Persist `content` as editorial for given `year`, `issue` and `catalog_type`"
  (-> {:year year :issue issue :catalog_type catalog_type :content content}
      save-editorial-internal!))

(defn save-recommendation! [year issue catalog_type content]
  "Persist `content` as recommendation for given `year`, `issue` and `catalog_type`"
  (-> {:year year :issue issue :catalog_type catalog_type :content content}
      save-recommendation-internal!))

(defn save-full-catalog! [year catalog_type items]
  "Persist `items` for a full catalog with given `year`, `issue` and `catalog_type`"
  (-> {:year year :catalog_type catalog_type :items (prn-str items)}
      save-full-catalog-internal!))

