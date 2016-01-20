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
  (GET "/" request (friend/authenticated (views/home request)))

  (GET "/neu-im-sortiment.pdf" request (views/neu-im-sortiment))
  (GET "/neue-grossdruckbücher.pdf" request (views/neue-grossdruckbücher))
  (GET "/neue-braillebücher.xml" request (views/neue-braillebücher))
  (GET "/neue-hörbücher.pdf" request (views/neue-hörbücher))

  ;; editorials
  (GET "/editorial" request (views/editorial-form request))
  (POST "/editorial" [editorial recommended :as r]
        (friend/authorize #{:admin :it} (views/editorial r)))

  ;; upload catalog data
  (GET "/upload" request
       (friend/authorize #{:admin :it} (views/upload-form request)))
  (POST "/upload-confirm" [file :as r]
        (friend/authorize #{:admin :it} (views/upload-confirm r file)))

  ;; auth
  (GET "/login" [] (views/login-form))
  (GET "/logout" req (friend/logout* (response/redirect "/")))

  ;; resources and 404
  (route/resources "/")
  (route/not-found "Not Found"))

(def site
  "Main handler for the application"
  (-> app-routes
      (friend/authenticate
       {:credential-fn (partial creds/bcrypt-credential-fn users)
        :workflows [(workflows/interactive-form)]
        :unauthorized-handler views/unauthorized})
      (wrap-defaults (assoc-in site-defaults [:static :resources] false))
      stacktrace/wrap-stacktrace
      wrap-base-url))

