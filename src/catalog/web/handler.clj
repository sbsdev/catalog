(ns catalog.web.handler
  "Main entry points to the application"
  (:require [catalog.web.views :as views]
            [cemerick.friend :as friend]
            [cemerick.friend
             [credentials :as creds]
             [workflows :as workflows]]
            [compojure
             [core :refer [defroutes GET POST]]
             [route :as route]]
            [hiccup.middleware :refer [wrap-base-url]]
            [ring.middleware
             [defaults :refer [site-defaults wrap-defaults]]
             [stacktrace :as stacktrace]]
            [ring.util.response :as response]))

(defroutes app-routes
  "Main routes for the application"
  (GET "/" request (views/home request))

  (GET "/neu-im-sortiment.pdf" request (views/neu-im-sortiment))
  (GET "/neu-in-grossdruck.pdf" request (views/neu-in-grossdruck))
  (GET "/neu-in-braille.xml" request (views/neu-in-braille))
  (GET "/neu-als-hörbuch.pdf" request (views/neu-als-hörbuch))

  ;; editorials
  (GET "/editorial/:fmt{grossdruck|braille|hörbuch}"
       [request fmt] (views/editorial-form request fmt))
  (POST "/editorial/:fmt{grossdruck|braille|hörbuch}"
        [fmt editorial recommended :as r] (views/editorial r))

  ;; upload catalog data
  (GET "/upload" request (views/upload-form request))
  (POST "/upload-confirm" [file :as r] (views/upload-confirm r file))

  ;; resources and 404
  (route/resources "/")
  (route/not-found "Not Found"))

(def site
  "Main handler for the application"
  (-> app-routes
      (wrap-defaults (assoc-in site-defaults [:static :resources] false))
      stacktrace/wrap-stacktrace
      wrap-base-url))

