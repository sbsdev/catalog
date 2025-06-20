(ns catalog.repl
  "Convenience functions for manipulations at the repl"
  (:require [catalog.db.core :as db]
            [catalog.layout.fop :as layout.fop]
            [catalog.layout.text :as layout.text]
            [catalog.validation :as validation]
            [catalog.vubis :as vubis]
            [clojure.data.xml :as xml]
            [clojure.java.io :as io]
            [schema.core :as s]))

(defn neu-im-sortiment [year issue]
  (let [temp-file (java.io.File/createTempFile (format "nis-%s-%s" year issue) ".pdf")]
    (-> (db/read-catalog year issue)
        vubis/order
        vubis/duplicate-print-and-braille-items
        vubis/group
        (layout.fop/document :all-formats year issue nil nil)
        (layout.fop/generate-pdf! temp-file))))

(defn neu-in-grossdruck [year issue]
  (let [temp-file (java.io.File/createTempFile (format "nig-%s-%s" year issue) ".pdf")
        editorial (db/read-editorial year issue :grossdruck)
        recommendation (db/read-recommendation year issue :grossdruck)]
    (-> (db/read-catalog year issue)
        vubis/order-and-group
        (layout.fop/document :grossdruck year issue editorial recommendation)
        (layout.fop/generate-pdf! temp-file))))

(defn neu-im-sortiment-fop
  "Generate the xsl-fo XML for the *Neu im Sortiment* catalog in file
  `out` for given `year` and `issue`"
  [year issue out]
  (->
   (db/read-catalog year issue)
   vubis/order
   vubis/duplicate-print-and-braille-items
   vubis/group
   (layout.fop/generate-document :all-formats year issue nil nil out)))

(defn neu-in-grossdruck-fop
  "Generate the xsl-fo XML for the *Neu in Grossdruck* catalog in file
  `out` for given `year` and `issue`"
  [year issue out]
  (let [editorial (db/read-editorial year issue :grossdruck)
        recommendations (db/read-recommendation year issue :grossdruck)]
    (->
     (db/read-catalog year issue)
     vubis/order-and-group
     (layout.fop/generate-document :grossdruck year issue editorial recommendations out))))

(defn generate-pdf
  "Generate the pdf `pdf` for a given xsl-fo XML file `fo-file`"
  [fo-file pdf]
  (->
   (xml/parse (io/reader fo-file))
   (layout.fop/generate-pdf! (io/file pdf))))

(defn neu-im-sortiment-text
  "Generate plain text for the *Neu im Sortiment* catalog for given
  `year` and `issue`"
  [year issue]
  (->
   (db/read-catalog year issue)
   vubis/order
   vubis/duplicate-print-and-braille-items
   vubis/group
   (layout.text/text {:year year :issue issue})))

(defn custom-text
  "Generate plain text for the *Katalog nach Mass* catalog for given
  `year` and `issue`"
  [in query customer]
  (-> in
   vubis/read-file
   vubis/order
   (layout.text/text {:query query :customer customer})))

(defn validate [in]
  (let [checker (s/checker validation/CatalogItem)]
    (->>
     in
     vubis/read-file
     vubis/collate-all-duplicate-items
     (keep #(when-let [error (checker %)] [error %])))))
