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

(defn home
  ([request]
   (home request 2016 3)) ;; FIXME: hard coded for now
  ([request year issue]
   (let [identity (friend/identity request)]
     (layout/common
      identity
      year issue
      [:div.row
       [:div.col-md-6
        [:div.well
         [:h2 (translations :catalog-all)]
         (icon-button (format "/%s/%s/neu-im-sortiment.pdf" year issue) "download" "Download")]]
       [:div.col-md-6
        [:div.well
         [:h2 (translations :catalog-grossdruck)]
         (icon-button (format "/%s/%s/neu-in-grossdruck.pdf" year issue) "download" "Download")]]]
      [:div.row
       [:div.col-md-6
        [:div.well
         [:h2 (translations :catalog-braille)]
         (icon-button (format "/%s/%s/neu-in-braille.xml" year issue) "download" "Download")]]
       [:div.col-md-6
        [:div.well
         [:h2 (translations :catalog-hörbuch)]
         (icon-button (format "/%s/%s/neu-als-hörbuch.pdf" year issue) "download" "Download")
         (icon-button (format "/%s/%s/neu-als-hörbuch.ncc" year issue) "download" "NCC")]]]))))

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
        editorial (db/read-editorial year issue :grossdruck)
        recommendation (db/read-recommendation year issue :grossdruck)]
    (-> (db/read-catalog year issue)
        vubis/order-and-group
        (layout.fop/document :grossdruck editorial recommendation)
        (layout.fop/generate-pdf! temp-file))
    (-> temp-file
        response/response
        (response/content-type "application/pdf"))))

(defn neu-in-braille [year issue]
  (let [editorial (db/read-editorial year issue :braille)
        recommendation (db/read-recommendation year issue :braille)]
    (-> (db/read-catalog year issue)
        vubis/order-and-group
        :braille
        (layout.dtbook/dtbook editorial recommendation)
        response/response
        (response/content-type "application/xml"))))

(defn neu-als-hörbuch [year issue]
  (let [temp-file (java.io.File/createTempFile (file-name :catalog-hörbuch year issue) ".pdf")
        editorial (db/read-editorial year issue :hörbuch)
        recommendation (db/read-recommendation year issue :hörbuch)]
    (-> (db/read-catalog year issue)
        (vubis/order-and-group vubis/get-update-keys-neu-als-hörbuch)
        (layout.fop/document :hörbuch editorial recommendation)
        (layout.fop/generate-pdf! temp-file))
    (-> temp-file
        response/response
        (response/content-type "application/pdf"))))

(defn upload-form [request year issue & [errors]]
  (let [identity (friend/identity request)]
    (layout/common
     identity
     year issue
     [:div.row
      [:div.col-md-6
       [:div.well
        [:h2 (format "Upload %s" (translations :catalog-all))]
        (when (seq errors)
          [:p [:ul.alert.alert-danger (for [e errors] [:li e])]])
        (form/form-to
         {:enctype "multipart/form-data"
          :class "form-inline"}
         [:post (format "/%s/%s/upload-confirm" year issue)]
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
        (layout/common
         identity
         year issue
         [:h1 "Confirm Upload"]
         [:table#productions.table.table-striped
          [:thead [:tr (map #(vec [:th %]) ["Record-id" "Title" "Field" "Value" "Error"])]]
          [:tbody
           (for [[errors {:keys [record-id title] :as item}] problems]
             (if (map? errors)
               (for [[k v] errors]
                 [:tr
                  [:td record-id]
                  [:td (h title)]
                  [:td k]
                  [:td (h (item k))]
                  [:td (str v)]])
               [:tr
                [:td record-id]
                [:td (h title)]
                [:td ]
                [:td ]
                [:td (pr-str errors)]]))]]
         (form/form-to
          {:enctype "multipart/form-data"}
          [:post (format "/%s/%s/upload" year issue)]
          (anti-forgery-field)
          (form/hidden-field "items" (prn-str items))
          (form/submit-button "Upload Anyway"))))
      (do
        ;; add the file
        ;; and redirect to the index
        (response/redirect-after-post (format "/%s/%s" year issue))))))

(defn upload [request year issue items]
  (db/save-catalog! year issue (edn/read-string items))
  (response/redirect-after-post (format "/%s/%s" year issue)))

(defn editorial-form [request fmt year issue]
  (let [identity (friend/identity request)
        editorial (db/read-editorial year issue fmt)
        recommendation (db/read-recommendation year issue fmt)]
    (layout/common
     identity
     year issue
     [:h1 (format "Editorial und Buchtipps für %s" (string/capitalize fmt))]
     (form/form-to
      [:post (format "/%s/%s/editorial/%s" year issue fmt)]
      (anti-forgery-field)
      [:div.form-group
       (form/label "editorial" "Editorial:")
       (form/text-area {:class "form-control" :data-provide "markdown" :data-hidden-buttons "cmdImage cmdCode" :data-resize "vertical" :rows 30} "editorial" editorial)]
      [:div.form-group
       (form/label "recommended" "Buchtipps:")
       (form/text-area {:class "form-control" :data-provide "markdown" :data-hidden-buttons "cmdImage cmdCode" :data-resize "vertical" :rows 30} "recommended" recommendation)]
      (form/submit-button {:class "btn btn-default"} "Submit")))))

(defn editorial [request fmt year issue editorial recommended]
  ;; FIXME: add a transaction around the two following saves
  (db/save-editorial! year issue fmt editorial)
  (db/save-recommendation! year issue fmt recommended)
  (response/redirect-after-post (format "/%s/%s" year issue)))
