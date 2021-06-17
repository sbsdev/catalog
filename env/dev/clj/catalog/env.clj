(ns catalog.env
  (:require
    [selmer.parser :as parser]
    [clojure.tools.logging :as log]
    [catalog.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[catalog started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[catalog has shut down successfully]=-"))
   :middleware wrap-dev})
