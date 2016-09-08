(ns catalog.web.main
  "Main entry point.

  Set up web server."
  (:gen-class)
  (:require [catalog.web.handler :as handler]
            [immutant.web :as web]))

(defn -main []
  ;; start web server
  (web/run handler/site))
