(ns catalog.web.views
  "Views for web application"
  (:require [catalog
             [db :as db]
             [validation :as validation]
             [vubis :as vubis]]
            [catalog.layout
             [common :refer [translations]]
             [dtbook :as layout.dtbook]
             [fop :as layout.fop]]
            [catalog.web.layout :as layout]
            [cemerick.friend :as friend]
            [clojure
             [edn :as edn]
             [string :as string]]
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

(defn- file-name [k year issue]
  (let [name (-> (k translations)
                 string/lower-case
                 (string/replace #"\s" "-"))]
    (format "%s-%s-%s" name year issue)))

(defn neu-im-sortiment [year issue]
  (let [temp-file (java.io.File/createTempFile (file-name :catalog-all year issue) ".pdf")]
    (-> (db/read-catalog year issue)
        vubis/order-and-group
        (layout.fop/document :all-formats nil nil)
        (layout.fop/generate-pdf! temp-file))
    (-> temp-file
        response/response
        (response/content-type "application/pdf"))))

(defn neu-in-grossdruck [year issue]
  (let [temp-file (java.io.File/createTempFile (file-name :catalog-grossdruck year issue) ".pdf")
        editorial (db/read-editorial year issue "grossdruck")
        recommendation (db/read-recommendation year issue "grossdruck")]
    (-> (db/read-catalog year issue)
        (layout.fop/document :grossdruck editorial recommendation)
        (layout.fop/generate-pdf! temp-file))
    (-> temp-file
        response/response
        (response/content-type "application/pdf"))))

(defn neu-in-braille [year issue]
  (-> (db/read-catalog year issue)
      :braille
      layout.dtbook/dtbook
      response/response
      (response/content-type "application/xml")))

(defn neu-als-hörbuch [year issue]
  (let [temp-file (java.io.File/createTempFile (file-name :catalog-hörbuch year issue) ".pdf")
        editorial (db/read-editorial year issue "hörbuch")
        recommendation (db/read-recommendation issue "hörbuch")]
    (-> (db/read-catalog year issue)
        (layout.fop/document :hörbuch editorial recommendation)
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
        [:h2 (format "Upload %s" (translations :catalog-all))]
        (when (seq errors)
          [:p [:ul.alert.alert-danger (for [e errors] [:li e])]])
        (form/form-to
         {:enctype "multipart/form-data"
          :class "form-inline"}
         [:post (format "/upload-confirm/%s/%s" 2016 03)] ;; FIXME: hardcoded for now
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

(defn upload-confirm [request year issue file]
  (let [{tempfile :tempfile} file
        path (.getPath tempfile)
        items (vubis/read-file path)
        items-collated (vubis/collate-all-duplicate-items items)
        checker (s/checker validation/CatalogItem)
        problems (keep #(when-let [error (checker %)] [error %]) items-collated)]
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
                        [:post (format "/upload/%s/%s" year issue)]
                        (anti-forgery-field)
                        (form/hidden-field "items" (prn-str items))
                        (form/submit-button "Upload Anyway"))))
      (do
        ;; add the file
        ;; and redirect to the index
        (response/redirect-after-post "/")))))

(defn upload [request year issue items]
  (db/save-catalog! year issue (edn/read-string items))
  (response/redirect-after-post "/"))

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
