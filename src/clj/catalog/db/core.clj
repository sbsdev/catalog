(ns catalog.db.core
  "Persistence for catalog items"
  (:require
    [next.jdbc.date-time]
    [next.jdbc.result-set]
    [clojure.edn :as edn]
    [clojure.tools.logging :as log]
    [conman.core :as conman]
    [catalog.config :refer [env]]
    [mount.core :refer [defstate]]))

(defstate ^:dynamic *db*
  :start (if-let [jdbc-url (env :database-url)]
           (conman/connect! {:jdbc-url jdbc-url})
           (do
             (log/warn "database connection URL was not found, please set :database-url in your config, e.g: dev-config.edn")
             *db*))
  :stop (conman/disconnect! *db*))

(conman/bind-connection *db* "sql/queries.sql")

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

(extend-protocol next.jdbc.result-set/ReadableColumn
  java.sql.Timestamp
  (read-column-by-label [^java.sql.Timestamp v _]
    (.toLocalDateTime v))
  (read-column-by-index [^java.sql.Timestamp v _2 _3]
    (.toLocalDateTime v))
  java.sql.Date
  (read-column-by-label [^java.sql.Date v _]
    (.toLocalDate v))
  (read-column-by-index [^java.sql.Date v _2 _3]
    (.toLocalDate v))
  java.sql.Time
  (read-column-by-label [^java.sql.Time v _]
    (.toLocalTime v))
  (read-column-by-index [^java.sql.Time v _2 _3]
    (.toLocalTime v)))

