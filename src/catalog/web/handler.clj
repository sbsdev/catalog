(ns catalog.web.handler
  "Main entry points to the application"
  (:require [catalog.web.views :as views]
            [compojure
             [coercions :refer [as-int]]
             [core :refer [defroutes GET POST]]
             [route :as route]]
            [hiccup.middleware :refer [wrap-base-url]]
            [ring.middleware
             [defaults :refer [site-defaults wrap-defaults]]
             [stacktrace :as stacktrace]]
            [ring.util.codec :refer [url-encode]]))


(def hörbuch-encoded (url-encode "hörbuch"))

(defroutes app-routes
  "Main routes for the application"
  (GET "/" request (views/home request))
  (GET "/:year/:issue" [year :<< as-int issue :<< as-int :as r] (views/home r year issue))

  ;; Neu im Sortiment
  (GET "/:year/:issue/neu-im-sortiment.pdf"
       [year :<< as-int issue :<< as-int]
       (views/neu-im-sortiment year issue))

  ;; Neu in Grossdruck
  (GET "/:year/:issue/neu-in-grossdruck.pdf"
       [year :<< as-int issue :<< as-int]
       (views/neu-in-grossdruck year issue))

  ;; Neu in Braille
  (GET "/:year/:issue/neu-in-braille.xml"
       [year :<< as-int issue :<< as-int]
       (views/neu-in-braille year issue))

  ;; Neu als Hörbuch
  (GET (format "/:year/:issue/neu-als-%s.pdf" hörbuch-encoded)
       [year :<< as-int issue :<< as-int]
       (views/neu-als-hörbuch year issue))
  (GET (format "/:year/:issue/neu-als-%s.ncc" hörbuch-encoded)
       [year :<< as-int issue :<< as-int]
       (views/neu-als-hörbuch-ncc year issue))

  ;; editorials
  (GET (format "/:year/:issue/editorial/:fmt{grossdruck|braille|%s}" hörbuch-encoded)
       [year :<< as-int issue :<< as-int fmt :as r]
       (views/editorial-form r fmt year issue))
  (POST (format "/:year/:issue/editorial/:fmt{grossdruck|braille|%s}" hörbuch-encoded)
        [year :<< as-int issue :<< as-int fmt editorial recommended :as r]
        (views/editorial r fmt year issue editorial recommended))

  ;; upload catalog data
  (GET "/:year/:issue/upload"
       [year :<< as-int issue :<< as-int file :as r]
       (views/upload-form r year issue))
  (POST "/:year/:issue/upload-confirm"
        [year :<< as-int issue :<< as-int file :as r]
        (views/upload-confirm r year issue file))
  (POST "/:year/:issue/upload"
        [year :<< as-int issue :<< as-int items :as r]
        (views/upload r year issue items))

  ;; resources and 404
  (route/resources "/")
  (route/not-found "Not Found"))

(def site
  "Main handler for the application"
  (-> app-routes
      (wrap-defaults (assoc-in site-defaults [:static :resources] false))
      stacktrace/wrap-stacktrace
      wrap-base-url))

