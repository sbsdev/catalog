(ns catalog.core
  (:require [catalog.layout
             [dtbook :as layout.dtbook]
             [fop :as layout.fop]]
            [catalog.vubis :as vubis]
            [clojure.java.io :as io]))

(defn neu-im-sortiment [in out]
  (-> in
   vubis/read-file
   vubis/order-and-group
   layout.fop/document
   (layout.fop/generate-pdf! out)))

(defn neu-in-grossdruck [in out]
  (-> in
   vubis/read-file
   vubis/order-and-group
   (select-keys [:grossdruck])
   (layout.fop/document :title "Neue Grossdruckbücher")
   (layout.fop/generate-pdf! out)))

(defn neu-in-braille [in out]
  (->> in
   vubis/read-file
   vubis/order-and-group
   :braille
   layout.dtbook/dtbook
   (spit (io/file out))))
