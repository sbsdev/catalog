(ns catalog.core
  (:require [catalog.layout
             [dtbook :as layout.dtbook]
             [fop :as layout.fop]
             [latex :as layout.latex]]
            [catalog.vubis :as vubis]
            [clojure.java.io :as io]))

(defn neu-im-sortiment [in out]
  (-> in
   vubis/read-file
   vubis/order-and-group
   layout.fop/document
   (layout.fop/generate-pdf! out)))

(defn neu-im-sortiment-old [in]
  (layout.latex/generate-latex (vubis/order-and-group (vubis/read-file in)))
  (:err (layout.latex/generate-pdf)))

(defn neu-in-grossdruck [in out]
  (-> in
   vubis/read-file
   vubis/order-and-group
   (select-keys [:grossdruck])
   (layout.fop/document :title "Neue Grossdruckbücher":font "Tiresias" :font-size "17pt")
   (layout.fop/generate-pdf! out)))

(defn neu-in-braille [in]
  (->> in
   vubis/read-file
   vubis/order-and-group
   :braille
   layout.dtbook/dtbook
   (spit (io/file "/tmp/catalog.xml"))))
