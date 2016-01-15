(ns catalog.web.main
  "Main entry point.

  Set up web server and queues."
  (:gen-class)
  (:require [catalog.web.handler :as handler]
            [immutant.web :as web]))

(defn -main []
  ;; start web server
  (web/run-dmc handler/site))
