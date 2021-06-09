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
             [obi :as layout.obi]
             [text :as layout.text]]
            [catalog.web.layout :as layout]
            [clj-time
             [core :as time.core]
             [format :as time.format]]
            [clojure
             [edn :as edn]
             [string :as string]]
            [hiccup
             [core :refer [h]]
             [form :as form]]
            [org.tobereplaced.nio.file :as nio.file]
            [ring.util
             [anti-forgery :refer [anti-forgery-field]]
             [response :as response]]
            [schema.core :as s]))

(defn- download-button
  ([href]
   (download-button href "Download"))
  ([href label]
   [:a.btn.btn-default {:href href :role "button" :aria-label label}
    [:span.glyphicon {:class "glyphicon-download" :aria-hidden "true"}] (str " " label)]))

(defn- download-url [& items]
  (str "/" (string/join "/" (remove nil? items))))

(defn- download-well
  [title year issue file-name]
  [:div.well
   [:h2 title]
   (download-button (download-url year issue file-name))])

(defn home
  ([request]
   (let [date (time.core/today)
         [year issue] (issue/issue-for date)]
     (response/redirect (format "/%s/%s" year issue))))
  ([request year issue]
   (layout/common
    year issue
    [:div.row
     [:div.col-md-6
      (download-well (translations :catalog-all) year issue "neu-im-sortiment.pdf")]
     [:div.col-md-6
      (download-well (translations :catalog-grossdruck) year issue "neu-in-grossdruck.pdf")]]
    [:div.row
     [:div.col-md-6
      (download-well (translations :catalog-braille) year issue "neu-in-braille.xml")]
     [:div.col-md-6
      [:div.well
       [:h2 (translations :catalog-hörbuch)]
       (download-button (download-url year issue "neu-als-hörbuch.pdf"))
       (download-button (download-url year issue "neu-als-hörbuch.ncc") "NCC")]]])))

(defn full-catalogs
  [request year issue]
  (layout/common
   year issue
   [:div.row
    [:div.col-md-6
     (download-well (translations :catalog-hörfilm) year nil "hörfilme-in-der-sbs.pdf")]
    [:div.col-md-6
     (download-well (translations :catalog-ludo) year nil "spiele-in-der-sbs.pdf")]]
   [:div.row
    [:div.col-md-6
     (download-well (translations :catalog-print-and-braille) year nil "print-und-braille-bücher-in-der-sbs.pdf")]
    [:div.col-md-6
     (download-well (translations :catalog-taktilesbuch) year nil "taktile-kinderbücher-der-sbs.pdf")]]))

(defn- clean-string
  "Clean a string so that it can be used to generate a file name"
  [s]
  (-> s
   string/lower-case
   (string/replace #"\s" "-")
   (string/replace #"&" "und")))

;; FIXME: we should probably use time zone that is defined in the
;; browser of the user. For simplicity sake we just hard code it here.
(def ^:private local-time-zone-id "Europe/Zurich")

(defn- local-time
  "Return a string representing the local time that can be used for
  file names."
  []
  (let [time-zone (time.core/time-zone-for-id local-time-zone-id)
        formatter (time.format/formatter "yyyy-MM-dd-kk-mm-ss" time-zone)]
    (time.format/unparse formatter (time.core/now))))

(defn- file-name
  "Return a file-name for a given key `k` a value `v` (usually the
  year) and optionaly an `issue`. The key is looked up in the
  translation map and cleaned to have a clean file name. In the case
  of custom catalogs the date and the value (in this case the
  customer) is added to the file name."
  ([k v]
   (file-name k v nil))
  ([k v issue]
   (let [name (clean-string (k translations))]
     (cond
       (= k :catalog-custom) (format "%s-%s-%s" name (clean-string v) (local-time))
       issue (format "%s-%s-%s" name v issue)
       :default (format "%s-%s" name v)))))

(defn- content-disposition
  "Returns an updated Ring response with the Content-Disposition
  header set to \"attachment\" and the filename corresponding to the
  given `filename`."
  [resp filename]
  (response/header resp "Content-Disposition" (format "attachment; filename=%s" filename)))

(defn- download-response
  "Create a response with proper content-type and content-disposition
  for given `file`, `mime-type` and `filename`"
  [file mime-type filename]
  (-> file
   response/response
   (response/content-type mime-type)
   (content-disposition filename)))

(defn- pdf-response
  "Create a response with proper content-type and content-disposition
  for given pdf `file` and `filename`"
  [file filename]
  (download-response file "application/pdf" (str filename ".pdf")))

(defn- xml-response
  "Create a response with proper content-type and content-disposition
  for given `body` and `filename`"
  [body filename]
  (download-response body "application/xml" (str filename ".xml")))

(defn- text-response
  "Create a response with proper content-type and content-disposition
  for given `body` and `filename`"
  [body filename]
  (download-response body "text/plain" (str filename ".txt")))

(defn neu-im-sortiment [year issue]
  (let [filename (file-name :catalog-all year issue)
        temp-file (java.io.File/createTempFile filename ".pdf")]
    (-> (db/read-catalog year issue)
        vubis/order
        vubis/duplicate-print-and-braille-items
        vubis/group
        (layout.fop/document :all-formats year issue nil nil)
        (layout.fop/generate-pdf! temp-file))
    (pdf-response temp-file filename)))

(defn neu-in-grossdruck [year issue]
  (let [filename (file-name :catalog-grossdruck year issue)
        temp-file (java.io.File/createTempFile filename ".pdf")
        editorial (db/read-editorial year issue :grossdruck)
        recommendation (db/read-recommendation year issue :grossdruck)]
    (-> (db/read-catalog year issue)
        vubis/order-and-group
        (layout.fop/document :grossdruck year issue editorial recommendation)
        (layout.fop/generate-pdf! temp-file))
    (pdf-response temp-file filename)))

(defn neu-in-braille [year issue]
  (let [filename (file-name :catalog-braille year issue)
        editorial (db/read-editorial year issue :braille)
        recommendation (db/read-recommendation year issue :braille)]
    (-> (db/read-catalog year issue)
        vubis/order
        vubis/duplicate-print-and-braille-items
        vubis/group
        :braille
        (layout.dtbook/dtbook {:year year :issue issue
                               :editorial editorial :recommendation recommendation})
        (xml-response filename))))

(defn neu-als-hörbuch [year issue]
  (let [filename (file-name :catalog-hörbuch year issue)
        temp-file (java.io.File/createTempFile filename ".pdf")
        editorial (db/read-editorial year issue :hörbuch)
        recommendation (db/read-recommendation year issue :hörbuch)]
    (-> (db/read-catalog year issue)
        (vubis/order-and-group vubis/get-update-keys-neu-als-hörbuch)
        (layout.fop/document :hörbuch year issue editorial recommendation)
        (layout.fop/generate-pdf! temp-file))
    (pdf-response temp-file filename)))

(defn neu-als-hörbuch-ncc [year issue]
  (let [filename (file-name :catalog-hörbuch year issue)
        editorial (db/read-editorial year issue :hörbuch)
        recommendation (db/read-recommendation year issue :hörbuch)]
    (-> (db/read-catalog year issue)
        (vubis/order-and-group vubis/get-update-keys-neu-als-hörbuch)
        (layout.obi/dtbook year issue editorial recommendation)
        (xml-response filename))))

(defn hörfilme [year]
  (let [filename (file-name :catalog-hörfilm year)
        temp-file (java.io.File/createTempFile filename ".pdf")]
    (-> (db/read-full-catalog year :hörfilm)
        (vubis/order-and-group vubis/get-update-keys-hörfilm)
        (layout.fop/document :hörfilm year nil nil nil)
        (layout.fop/generate-pdf! temp-file))
    (pdf-response temp-file filename)))

(defn spiele [year]
  (let [filename (file-name :catalog-ludo year)
        temp-file (java.io.File/createTempFile filename ".pdf")]
    (-> (db/read-full-catalog year :ludo)
        (vubis/order-and-group vubis/get-update-keys-ludo)
        (layout.fop/document :ludo year nil nil nil)
        (layout.fop/generate-pdf! temp-file))
    (pdf-response temp-file filename)))

(defn taktile-bücher [year]
  (let [filename (file-name :catalog-taktilesbuch year)
        temp-file (java.io.File/createTempFile filename ".pdf")]
    (-> (db/read-full-catalog year :taktilesbuch)
        (vubis/order-and-group vubis/get-update-keys-taktil)
        (layout.fop/document :taktilesbuch year nil nil nil)
        (layout.fop/generate-pdf! temp-file))
    (pdf-response temp-file filename)))

(defn print-and-braille-bücher [year]
  (let [filename (file-name :catalog-print-and-braille year)
        temp-file (java.io.File/createTempFile filename ".pdf")]
    (-> (db/read-full-catalog year :print-and-braille)
        (vubis/order-and-group vubis/get-update-keys-print-and-braille)
        (layout.fop/document :print-and-braille year nil nil nil)
        (layout.fop/generate-pdf! temp-file))
    (pdf-response temp-file filename)))

(defn- upload-well
  [title url errors]
  [:div.well
   [:h2 title]
   (when (seq errors)
     [:p [:ul.alert.alert-danger (for [e errors] [:li e])]])
   (form/form-to
    {:enctype "multipart/form-data"}
    [:post url]
    (anti-forgery-field)
    [:div.form-group
     (form/label {:class "sr-only"} "file" "File:")
     (form/file-upload {:required "required"} "file")]
    (form/submit-button {:class "btn btn-default"} "Upload"))])

(defn upload-form [request year issue & [errors]]
  (layout/common
   year issue
   [:div.row
    [:div.col-md-12
     (upload-well (format "Upload %s" (translations :catalog-all))
                  (format "/%s/%s/%s/upload-confirm" year issue (name :all-formats))
                  errors)]]))

(defn- error-table
  [problems]
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
         [:td (pr-str errors)]]))]])


(defn upload-full-form [request year issue & [errors]]
  (layout/common
   year issue
   [:div.row
    [:div.col-md-6
     (upload-well "Upload Gesamtkatalog Hörfilm"
                  (format "/%s/%s/%s/upload-confirm" year issue (name :hörfilm))
                  (:hörfilm errors))]
    [:div.col-md-6
     (upload-well "Upload Gesamtkatalog Spiele"
                  (format "/%s/%s/%s/upload-confirm" year issue (name :ludo))
                  (:ludo errors))]]
   [:div.row
    [:div.col-md-6
     (upload-well "Upload Gesamtkatalog Print & Braille"
                  (format "/%s/%s/%s/upload-confirm" year issue (name :print-and-braille))
                  (:print-and-braille errors))]
    [:div.col-md-6
     (upload-well "Upload Gesamtkatalog Taktile Bücher"
                  (format "/%s/%s/%s/upload-confirm" year issue (name :taktilesbuch))
                  (:taktilesbuch errors))]]))

(defn upload [request year issue fmt items]
  (case fmt
    (:hörfilm
     :ludo
     :taktilesbuch
     :print-and-braille) (db/save-full-catalog! year (name fmt) (edn/read-string items))
    (db/save-catalog! year issue (edn/read-string items)))
  (response/redirect-after-post (format "/%s/%s" year issue)))

(defn upload-confirm [request year issue fmt file]
  (let [{tempfile :tempfile} file
        path (.getPath tempfile)]
    (if-not (= (nio.file/size path) 0)
      (let [items (vubis/read-file path)
            items-collated (vubis/collate-all-duplicate-items items)
            checker (s/checker validation/CatalogItem)
            problems (keep #(when-let [error (checker %)] [error %]) items-collated)]
        (if (seq problems)
          (layout/common
           year issue
           [:h1 "Confirm Upload"]
           (error-table problems)
           (form/form-to
            {:enctype "multipart/form-data"}
            [:post (format "/%s/%s/%s/upload" year issue (name fmt))]
            (anti-forgery-field)
            (form/hidden-field "items" (prn-str items))
            (layout/button (format "/%s/%s/%s"
                                   year issue
                                   (if (= fmt :all-formats) "upload" "upload-full")) "Cancel")
            (form/submit-button {:class "btn btn-default"} "Upload Anyway")))
          ;; if there are no problems just upload the file.
          (upload request year issue fmt
                   ;; Repackage the items as edn as the api of the upload function expects
                   ;; it that way. Wasteful I know, but simple.
                  (prn-str items))))
      ;; if the file is empty go back to the upload form
      (if (= fmt :all-formats)
        (upload-form request year issue ["No file selected"])
        (upload-full-form request year issue {fmt ["No file selected"]})))))

(defn editorial-form [request fmt year issue]
  (let [editorial (db/read-editorial year issue fmt)
        recommendation (db/read-recommendation year issue fmt)]
    (layout/common
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

(defn- query-field
  [query]
  [:div.form-group
   (form/label "query" "Query:")
   (form/text-area {:class "form-control"
                    :rows 3
                    :placeholder "Description of the catalog"}
                   "query" query)])

(defn- customer-field
  [customer]
  [:div.form-group
   (form/label "customer" "Customer:")
   (form/text-field {:class "form-control"
                     :placeholder "Name of the customer that will receive the catalog"}
                    "customer" customer)])

(defn- format-field
  [selected]
  [:div.form-group
   (form/label "fmt" "Format:")
   (form/drop-down
    {:class "form-control"} "fmt"
    [["PDF" :pdf]
     ["Grossdruck" :grossdruck]
     ["Braille" :braille]
     ["Text" :plain-text]]
    selected)])

(defn custom-form [request year issue & [errors]]
  (layout/common
   year issue
   [:div.well
    [:h2 "Katalog nach Mass"]
    (when (seq errors)
      [:p [:ul.alert.alert-danger (for [e errors] [:li e])]])
    (form/form-to
     {:enctype "multipart/form-data"}
     [:post (format "/%s/%s/custom-confirm" year issue)]
     (anti-forgery-field)
     [:div.form-group
      (form/label "file" "File:")
      (form/file-upload {:required "required"} "file")]
     (query-field nil)
     (customer-field nil)
     (format-field :pdf)
     (form/submit-button {:class "btn btn-default"} "Submit"))]))

(defmulti custom (fn [_ _ _ _ _ fmt _] fmt))

(defmethod custom :pdf [request year issue query customer fmt items]
  (let [filename (file-name :catalog-custom customer)
        temp-file (java.io.File/createTempFile filename ".pdf")]
    (-> items
        edn/read-string
        vubis/order
        (layout.fop/document :custom year issue nil nil :query query :customer customer)
        (layout.fop/generate-pdf! temp-file))
    (pdf-response temp-file filename)))

(defmethod custom :grossdruck [request year issue query customer fmt items]
  (let [filename (file-name :catalog-custom customer)
        temp-file (java.io.File/createTempFile filename ".pdf")]
    (-> items
        edn/read-string
        vubis/order
        (layout.fop/document :custom-grossdruck year issue nil nil :query query :customer customer)
        (layout.fop/generate-pdf! temp-file))
    (pdf-response temp-file filename)))

(defmethod custom :braille [request year issue query customer fmt items]
  (-> items
      edn/read-string
      vubis/order
      (layout.dtbook/dtbook {:query query :customer customer})
      (xml-response (file-name :catalog-custom customer))))

(defmethod custom :plain-text [request year issue query customer fmt items]
  (-> items
      edn/read-string
      vubis/order
      (layout.text/text {:query query :customer customer})
      (text-response (file-name :catalog-custom customer))))

(defn custom-confirm [request year issue query customer fmt file]
  (let [{tempfile :tempfile} file
        path (.getPath tempfile)]
    (if-not (= (nio.file/size path) 0)
      (let [items (vubis/read-file path)
            items-collated (vubis/collate-all-duplicate-items items)
            checker (s/checker validation/CatalogItem)
            problems (keep #(when-let [error (checker %)] [error %]) items-collated)]
        (if (seq problems)
          (layout/common
           year issue
           [:h1 "Confirm Katalog nach Mass"]
           (error-table problems)
           (form/form-to
            {:enctype "multipart/form-data"}
            [:post (format "/%s/%s/custom" year issue)]
            (anti-forgery-field)
            (form/hidden-field "items" (prn-str items))
            (form/hidden-field "query" query)
            (form/hidden-field "customer" customer)
            (form/hidden-field "fmt" fmt)
            (layout/button (format "/%s/%s/custom" year issue) "Cancel")
            (form/submit-button {:class "btn btn-default"} "Upload Anyway")))
          ;; if there are no problems just produce a response with a
          ;; custom catalog.
          (custom request year issue query customer fmt
                  ;;  Repackage the items as edn as the api of the custom function expects
                  ;;  it that way. Wasteful, I know, but simple.
                  (prn-str items))))
      ;; if the file is empty go back to the upload form
      (custom-form request year issue ["No file selected"]))))
