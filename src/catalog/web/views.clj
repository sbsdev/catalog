(ns catalog.web.views
  "Views for web application"
  (:require [catalog
             [db :as db]
             [issue :as issue]
             [validation :as validation]
             [vubis :as vubis]]
            [catalog.layout
             [common :refer [translations]]
             [dtbook :as layout.dtbook]
             [fop :as layout.fop]
             [obi :as layout.obi]]
            [catalog.web.layout :as layout]
            [cemerick.friend :as friend]
            [clj-time.core :as time.core]
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


(defn- download-well
  [title url]
  [:div.well
   [:h2 title]
   (icon-button url "download" "Download")])

(defn home
  ([request]
   (let [date (time.core/today)
         [year issue] (issue/issue-for date)]
     (response/redirect (format "/%s/%s" year issue))))
  ([request year issue]
   (let [identity (friend/identity request)]
     (layout/common
      identity
      year issue
      [:div.row
       [:div.col-md-6
        (download-well (translations :catalog-all)
                       (format "/%s/%s/neu-im-sortiment.pdf" year issue))]
       [:div.col-md-6
        (download-well (translations :catalog-grossdruck)
                       (format "/%s/%s/neu-in-grossdruck.pdf" year issue))]]
      [:div.row
       [:div.col-md-6
        (download-well (translations :catalog-braille)
                       (format "/%s/%s/neu-in-braille.xml" year issue))]
       [:div.col-md-6
        [:div.well
         [:h2 (translations :catalog-hörbuch)]
         (icon-button (format "/%s/%s/neu-als-hörbuch.pdf" year issue) "download" "Download")
         (icon-button (format "/%s/%s/neu-als-hörbuch.ncc" year issue) "download" "NCC")]]]))))

(defn full-catalogs
  [request year issue]
  (let [identity (friend/identity request)]
    (layout/common
     identity
     year issue
     [:div.row
      [:div.col-md-6
       (download-well (translations :catalog-hörfilm)
                      (format "/%s/hörfilme-in-der-sbs.pdf" year))]
      [:div.col-md-6
       (download-well (translations :catalog-ludo)
                      (format "/%s/spiele-in-der-sbs.pdf" year))]]
     [:div.row
      [:div.col-md-6
       (download-well (translations :catalog-taktilesbuch)
                      (format "/%s/taktile-kinderbücher-der-sbs.pdf" year))]])))

(defn- file-name
  ([k year]
   (file-name k year nil))
  ([k year issue]
   (let [name (-> (k translations)
                  string/lower-case
                  (string/replace #"\s" "-"))]
     (if issue
       (format "%s-%s-%s" name year issue)
       (format "%s-%s" name year)))))

(defn neu-im-sortiment [year issue]
  (let [temp-file (java.io.File/createTempFile (file-name :catalog-all year issue) ".pdf")]
    (-> (db/read-catalog year issue)
        vubis/order-and-group
        (layout.fop/document :all-formats year issue nil nil)
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
        (layout.fop/document :grossdruck year issue editorial recommendation)
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
        (layout.dtbook/dtbook year issue editorial recommendation)
        response/response
        (response/content-type "application/xml"))))

(defn neu-als-hörbuch [year issue]
  (let [temp-file (java.io.File/createTempFile (file-name :catalog-hörbuch year issue) ".pdf")
        editorial (db/read-editorial year issue :hörbuch)
        recommendation (db/read-recommendation year issue :hörbuch)]
    (-> (db/read-catalog year issue)
        (vubis/order-and-group vubis/get-update-keys-neu-als-hörbuch)
        (layout.fop/document :hörbuch year issue editorial recommendation)
        (layout.fop/generate-pdf! temp-file))
    (-> temp-file
        response/response
        (response/content-type "application/pdf"))))

(defn neu-als-hörbuch-ncc [year issue]
  (let [editorial (db/read-editorial year issue :hörbuch)
        recommendation (db/read-recommendation year issue :hörbuch)]
    (-> (db/read-catalog year issue)
        (vubis/order-and-group vubis/get-update-keys-neu-als-hörbuch)
        (layout.obi/dtbook year issue editorial recommendation)
        response/response
        (response/content-type "application/xml"))))

(defn hörfilme [year]
  (let [temp-file (java.io.File/createTempFile (file-name :catalog-hörfilm year) ".pdf")]
    (-> (db/read-full-catalog year :hörfilm)
        (vubis/order-and-group vubis/get-update-keys-hörfilm)
        (layout.fop/document :hörfilm year nil nil nil)
        (layout.fop/generate-pdf! temp-file))
    (-> temp-file
        response/response
        (response/content-type "application/pdf"))))

(defn spiele [year]
  (let [temp-file (java.io.File/createTempFile (file-name :catalog-ludo year) ".pdf")]
    (-> (db/read-full-catalog year :ludo)
        (vubis/order-and-group vubis/get-update-keys-ludo)
        (layout.fop/document :ludo year nil nil nil)
        (layout.fop/generate-pdf! temp-file))
    (-> temp-file
        response/response
        (response/content-type "application/pdf"))))

(defn- upload-well
  [title url errors]
  [:div.well
   [:h2 title]
   (when (seq errors)
     [:p [:ul.alert.alert-danger (for [e errors] [:li e])]])
   (form/form-to
    {:enctype "multipart/form-data"
     :class "form-inline"}
    [:post url]
    (anti-forgery-field)
    (form/file-upload "file")
    " "
    (form/submit-button {:class "btn btn-default"} "Upload"))])

(defn upload-form [request year issue & [errors]]
  (let [identity (friend/identity request)]
    (layout/common
     identity
     year issue
     [:div.row
      [:div.col-md-12
       (upload-well (format "Upload %s" (translations :catalog-all))
                    (format "/%s/%s/%s/upload-confirm" year issue (name :all-formats))
                    errors)]])))

(defn upload-confirm [request year issue fmt file]
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
          [:post (format "/%s/%s/%s/upload" year issue (name fmt))]
          (anti-forgery-field)
          (form/hidden-field "items" (prn-str items))
          (form/submit-button "Upload Anyway"))))
      (do
        ;; add the file
        ;; and redirect to the index
        (response/redirect-after-post (format "/%s/%s" year issue))))))

(defn upload [request year issue fmt items]
  (case fmt
    (:hörfilm :ludo :taktilesbuch) (db/save-full-catalog! year (name fmt) (edn/read-string items))
    (db/save-catalog! year issue (edn/read-string items)))
  (response/redirect-after-post (format "/%s/%s" year issue)))

(defn upload-full-form [request year issue & [errors]]
  (let [identity (friend/identity request)]
    (layout/common
     identity
     year issue
     [:div.row
      [:div.col-md-6
       (upload-well "Upload Gesamtkatalog Hörfilm"
                    (format "/%s/%s/%s/upload-confirm" year issue (name :hörfilm))
                    errors)]
      [:div.col-md-6
       (upload-well "Upload Gesamtkatalog Spiele"
                    (format "/%s/%s/%s/upload-confirm" year issue (name :ludo))
                    errors)]]
     [:div.row
      [:div.col-md-6
       (upload-well "Upload Gesamtkatalog Taktile Bücher"
                    (format "/%s/%s/%s/upload-confirm" year issue (name :taktilesbuch))
                    errors)]])))

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
       (form/text-area {:class "form-control" :data-provide "markdown"
                        :data-hidden-buttons "cmdImage cmdCode"
                        :data-resize "vertical" :rows 30} "editorial" editorial)]
      [:div.form-group
       (form/label "recommended" "Buchtipps:")
       (form/text-area {:class "form-control" :data-provide "markdown"
                        :data-hidden-buttons "cmdImage cmdCode"
                        :data-resize "vertical" :rows 30} "recommended" recommendation)]
      (form/submit-button {:class "btn btn-default"} "Submit")))))

(defn editorial [request fmt year issue editorial recommended]
  ;; FIXME: add a transaction around the two following saves
  (db/save-editorial! year issue fmt editorial)
  (db/save-recommendation! year issue fmt recommended)
  (response/redirect-after-post (format "/%s/%s" year issue)))
