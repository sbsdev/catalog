(ns catalog.web.views
  "Views for web application"
  (:require [catalog
             [validation :as validation]
             [vubis :as vubis]]
            [catalog.layout
             [common :refer [translations]]
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
            [schema.core :as s]
            [clojure.string :as string]))

(defn icon-button [href icon label]
  [:a.btn.btn-default {:href href :role "button" :aria-label label}
   [:span.glyphicon {:class (str "glyphicon-" icon) :aria-hidden "true"}] (str " " label)])

(defn home [request]
  (let [identity (friend/identity request)]
    (layout/common
     identity
     [:div.row
      [:div.col-md-6
       [:div.well
        [:h2 (translations :catalog-all)]
        (icon-button "/neu-im-sortiment.pdf" "download" "Download")]]
      [:div.col-md-6
       [:div.well
        [:h2 (translations :catalog-grossdruck)]
        (icon-button "/neu-in-grossdruck.pdf" "download" "Download")]]]
     [:div.row
      [:div.col-md-6
       [:div.well
        [:h2 (translations :catalog-braille)]
        (icon-button "/neu-in-braille.xml" "download" "Download")]]
      [:div.col-md-6
       [:div.well
        [:h2 (translations :catalog-hörbuch)]
        (icon-button "/neu-als-hörbuch.pdf" "download" "Download")
        (icon-button "/neu-als-hörbuch.ncc" "download" "NCC")
        (icon-button "/neu-als-hörbuch-toc.pdf" "download" "TOC")]]])))

(defn read-catalog [f]
  (-> f io/reader java.io.PushbackReader. edn/read))

(defn neu-im-sortiment []
  (let [temp-file (java.io.File/createTempFile "neu-im-sortiment" ".pdf")]
    (-> "catalog.edn"
        read-catalog
        (layout.fop/document :all-formats)
        (layout.fop/generate-pdf! temp-file))
    (-> temp-file
        response/response
        (response/content-type "application/pdf"))))

(defn neu-in-grossdruck []
  (let [temp-file (java.io.File/createTempFile "neu-in-grossdruck" ".pdf")]
    (-> "catalog.edn"
        read-catalog
        (layout.fop/document :grossdruck)
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

(defn neu-als-hörbuch []
  (let [temp-file (java.io.File/createTempFile "neu-als-hörbuch" ".pdf")]
    (-> "catalog.edn"
        read-catalog
        (layout.fop/document :hörbuch)
        (layout.fop/generate-pdf! temp-file))
    (-> temp-file
        response/response
        (response/content-type "application/pdf"))))

(defn upload-form [request & [errors]]
  (let [identity (friend/identity request)]
    (layout/common
     identity
     [:div.row
      [:div.col-md-6
       [:div.well
        [:h2 (format "Upload %" (translations :catalog-all))]
        (when (seq errors)
          [:p [:ul.alert.alert-danger (for [e errors] [:li e])]])
        (form/form-to
         {:enctype "multipart/form-data"
          :class "form-inline"}
         [:post "/upload-confirm"]
         (anti-forgery-field)
         (form/file-upload "file")
         " "
         (form/submit-button {:class "btn btn-default"} "Upload"))]]
      [:div.col-md-6
       [:div.well
        [:h2 "Upload Gesamtkatalog Hörfilm"]
        (when (seq errors)
          [:p [:ul.alert.alert-danger (for [e errors] [:li e])]])
        (form/form-to
         {:enctype "multipart/form-data"
          :class "form-inline"}
         [:post "/upload-confirm"]
         (anti-forgery-field)
         (form/file-upload "file")
         " "
         (form/submit-button {:class "btn btn-default"} "Upload"))]]]
     [:div.row
      [:div.col-md-6
       [:div.well
        [:h2 "Upload Gesamtkatalog Spiele"]
        (when (seq errors)
          [:p [:ul.alert.alert-danger (for [e errors] [:li e])]])
        (form/form-to
         {:enctype "multipart/form-data"
          :class "form-inline"}
         [:post "/upload-confirm"]
         (anti-forgery-field)
         (form/file-upload "file")
         " "
         (form/submit-button {:class "btn btn-default"} "Upload"))]]
      [:div.col-md-6
       [:div.well
        [:h2 "Upload Gesamtkatalog Taktile Bücher"]
        (when (seq errors)
          [:p [:ul.alert.alert-danger (for [e errors] [:li e])]])
        (form/form-to
         {:enctype "multipart/form-data"
          :class "form-inline"}
         [:post "/upload-confirm"]
         (anti-forgery-field)
         (form/file-upload "file")
         " "
         (form/submit-button {:class "btn btn-default"} "Upload"))]]])))

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

(defn editorial-form [request fmt]
  (let [identity (friend/identity request)]
    (layout/common
     identity
     [:h1 (format "Editorial und Buchtipps für %s" (string/capitalize fmt))]
     (form/form-to
      [:post "/editorial"]
      (anti-forgery-field)
      [:div.form-group
       (form/label "editorial" "Editorial:")
       (form/text-area {:class "form-control" :data-provide "markdown" :data-hidden-buttons "cmdImage cmdCode" :data-resize "vertical" :rows 30} "editorial")]
      [:div.form-group
       (form/label "recommended" "Buchtipps:")
       (form/text-area {:class "form-control" :data-provide "markdown" :data-hidden-buttons "cmdImage cmdCode" :data-resize "vertical" :rows 30} "recommended")]
      (form/submit-button {:class "btn btn-default"} "Submit")))))

(defn editorial [request editorial recommended]
  (response/redirect-after-post "/"))
