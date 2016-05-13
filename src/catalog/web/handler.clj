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
             [stacktrace :as stacktrace]]))


(defroutes app-routes
  "Main routes for the application"
  (GET "/" request (views/home request))

  (GET "/:year/:issue/neu-im-sortiment.pdf"
       [year :<< as-int issue :<< as-int]
       (views/neu-im-sortiment year issue))
  (GET "/:year/:issue/neu-in-grossdruck.pdf"
       [year :<< as-int issue :<< as-int]
       (views/neu-in-grossdruck year issue))
  (GET "/:year/:issue/neu-in-braille.xml"
       [year :<< as-int issue :<< as-int]
       (views/neu-in-braille year issue))
  (GET "/:year/:issue/neu-als-hörbuch.pdf"
       [year :<< as-int issue :<< as-int]
       (views/neu-als-hörbuch year issue))

  ;; editorials
  (GET "/:year/:issue/editorial/:fmt{grossdruck|braille|hörbuch}"
       [year :<< as-int issue :<< as-int fmt :as r]
       (views/editorial-form r fmt year issue))
  (POST "/:year/:issue/editorial/:fmt{grossdruck|braille|hörbuch}"
        [year :<< as-int issue :<< as-int fmt editorial recommended :as r]
        (views/editorial r fmt year issue editorial recommended))

  ;; upload catalog data
  (GET "/upload" request (views/upload-form request))
  (POST "/upload-confirm/:year/:issue"
        [year :<< as-int issue :<< as-int file :as r]
        (views/upload-confirm r year issue file))
  (POST "/upload/:year/:issue"
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

