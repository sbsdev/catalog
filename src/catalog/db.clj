(ns catalog.db
  (:require [clojure.java.jdbc :as jdbc]
            [yesql.core :refer [defqueries]]))

#_(def ^:private db {:factory factory :name "java:jboss/datasources/catalog"})

(def ^:private db "jdbc:mysql://localhost:3306/catalog?user=catalog&serverTimezone=UTC")

(defqueries "catalog/queries.sql" {:connection db})

