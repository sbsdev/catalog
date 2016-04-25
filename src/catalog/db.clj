(ns catalog.db
  (:require [clojure.edn :as edn]
            [yesql.core :refer [defqueries]]))

#_(def ^:private db {:factory factory :name "java:jboss/datasources/catalog"})

(def ^:private db "jdbc:mysql://localhost:3306/catalog?user=catalog&serverTimezone=UTC")

(defqueries "catalog/queries.sql" {:connection db})

(defn read-catalog [year issue]
  (-> {:year year :issue issue}
      catalog
      first
      :items
      edn/read-string))

(defn read-editorial [year issue type]
  (-> {:year year :issue issue :catalog_type type}
      editorial
      first
      :content))

(defn read-recommendation [year issue type]
  (-> {:year year :issue issue :catalog_type type}
      recommendation
      first
      :content))

(defn save-catalog! [year issue items]
  (-> {:year year :issue issue :items (prn-str items)}
      save-catalog-internal!))

(defn save-editorial! [year issue catalog_type content]
  (-> {:year year :issue issue :catalog_type catalog_type :content content}
      save-editorial-internal!))

(defn save-recommendation! [year issue catalog_type content]
  (-> {:year year :issue issue :catalog_type catalog_type :content content}
      save-recommendation-internal!))
