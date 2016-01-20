(ns catalog.web.views
  "Views for web application"
  (:require [catalog
             [validation :as validation]
             [vubis :as vubis]]
            [catalog.layout
             [dtbook :as layout.dtbook]
             [fop :as layout.fop]]
            [catalog.web.layout :as layout]
            [cemerick.friend :as friend]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [hiccup
             [core :refer [h]]
             [form :as form]]
            [ring.util
             [anti-forgery :refer [anti-forgery-field]]
             [response :as response]]
            [schema.core :as s]))

(defn icon-button [href icon label]
  [:a.btn.btn-default {:href href :role "button" :aria-label label}
   [:span.glyphicon {:class (str "glyphicon-" icon) :aria-hidden "true"}] (str " " label)])

(defn home [request]
  (let [identity (friend/identity request)]
    (layout/common
     identity
     [:div.row
      (form/form-to
      {:enctype "multipart/form-data"
       :class "form-inline"}
      [:post "/upload-confirm"]
      (anti-forgery-field)
      (form/file-upload #_{:title "Browse..." } "file")
      " "
      (form/submit-button {:class "btn btn-default"} "Upload"))]
     [:div.row
      [:div.col-md-4
       [:h2 "Neu im Sortiment"]
       (icon-button "/neu-im-sortiment.pdf" "download" "Download")]
      [:div.col-md-4
       [:h2 "Neu in Grossdruck"]
       (icon-button "/editorial" "pencil" "Edit")
       (icon-button "/neu-in-grossdruck.pdf" "download" "Download")]
      [:div.col-md-4
       [:h2 "Neu in Braille "]
       (icon-button "/neu-in-braille.xml" "download" "Download")]]
     )))

(defn read-catalog [f]
  (-> f io/reader java.io.PushbackReader. edn/read))

(defn neu-im-sortiment []
  (let [temp-file (java.io.File/createTempFile "neu-im-sortiment" ".pdf")]
    (-> "catalog.edn"
        read-catalog
        layout.fop/document
        (layout.fop/generate-pdf! temp-file))
    (-> temp-file
        response/response
        (response/content-type "application/pdf"))))

(defn neu-in-grossdruck []
  (let [temp-file (java.io.File/createTempFile "neu-im-grossdruck" ".pdf")]
    (-> "catalog.edn"
        read-catalog
        (select-keys [:grossdruck])
        (layout.fop/document :title "Neue Grossdruckbücher")
        (layout.fop/generate-pdf! temp-file))
    (-> temp-file
        response/response
        (response/content-type "application/pdf"))))

(defn neu-in-braille []
  (-> "catalog.edn"
      read-catalog
      :braille
      layout.dtbook/dtbook
      response/response
      (response/content-type "application/xml")))

(defn upload-form [request & [errors]]
  (let [identity (friend/identity request)]
    (layout/common identity
     [:h1 "Upload"]
     (when (seq errors)
       [:p [:ul.alert.alert-danger (for [e errors] [:li e])]])
     (form/form-to
      {:enctype "multipart/form-data"
       :class "form-inline"}
      [:post "/upload-confirm"]
      (anti-forgery-field)
      (form/file-upload "file")
      " "
      (form/submit-button {:class "btn btn-default"} "Upload")))))

(defn upload-confirm [request file]
  (let [{tempfile :tempfile} file
        path (.getPath tempfile)
        items (vubis/collate-all-duplicate-items (vubis/read-file path))
        checker (s/checker validation/CatalogItem)
        problems (keep #(when-let [error (checker %)] [error %]) items)]
    (if (seq problems)
      (let [identity (friend/identity request)]
        (layout/common identity
                       [:h1 "Confirm Upload"]
                       [:table#productions.table.table-striped
                        [:thead [:tr (map #(vec [:th %]) ["Record-id" "Title" "Field" "Value" "Error"])]]
                        [:tbody
                         (for [[errors {:keys [record-id title] :as item}] problems]
                           (for [[k v] errors]
                             [:tr
                              [:td record-id]
                              [:td (h title)]
                              [:td k]
                              [:td (h (item k))]
                              [:td (str v)]]))]]
                       (form/form-to
                        {:enctype "multipart/form-data"}
                        [:post "/upload"]
                        (anti-forgery-field)
                        (form/submit-button "Upload Anyway"))))
      (do
        ;; add the file
        ;; and redirect to the index
        (response/redirect-after-post "/")))))

(defn editorial-form [request]
  (let [identity (friend/identity request)]
    (layout/common
     identity
     [:h1 "Editorial"]
     (form/form-to
      [:post "/editorial"]
      (anti-forgery-field)
      [:div.form-group
       (form/label "editorial" "Editorial:")
       (form/text-area {:class "form-control" :data-provide "markdown" :data-hidden-buttons "cmdImage cmdCode" :data-resize "vertical" :rows 20} "editorial")]
      [:div.form-group
       (form/label "recommended" "Buchtipps:")
       (form/text-area {:class "form-control" :data-provide "markdown" :data-hidden-buttons "cmdImage cmdCode" :data-resize "vertical" :rows 20} "recommended")]
      (form/submit-button {:class "btn btn-default"} "Submit")))))

(defn editorial [request editorial recommended]
  (response/redirect-after-post "/"))

(defn login-form []
  (layout/common nil
   [:h3 "Login"]
   (form/form-to
    [:post "/login"]
    (anti-forgery-field)
    [:div.form-group
     (form/label "username" "Username:")
     (form/text-field {:class "form-control"} "username")]
    [:div.form-group
     (form/label "password" "Password:")
     (form/password-field {:class "form-control"} "password")]
    (form/submit-button {:class "btn btn-default"} "Login"))))

(defn unauthorized [request]
  (let [identity (friend/identity request)]
    (->
     (layout/common identity
      [:h2
       [:div.alert.alert-danger
        "Sorry, you do not have sufficient privileges to access "
        (:uri request)]]
      [:p "Please ask an administrator for help"])
     response/response
     (response/status 401))))