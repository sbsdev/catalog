(ns user
  (:require [ragtime.jdbc :as jdbc]
            [ragtime.repl :as repl]))

(defn load-config []
  {:datastore  (jdbc/sql-database "jdbc:mysql://localhost/catalog?user=catalog")
   :migrations (jdbc/load-resources "migrations")})

(defn migrate []
  (repl/migrate (load-config)))

(defn rollback []
  (repl/rollback (load-config)))
