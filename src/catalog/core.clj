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
   (layout.fop/document :all-formats)
   (layout.fop/generate-pdf! out)))

(defn neue-grossdruckbücher [in out]
  (-> in
   vubis/read-file
   vubis/order-and-group
   (layout.fop/document :grossdruck)
   (layout.fop/generate-pdf! out)))

(defn neue-hörbücher [in out]
  (-> in
   vubis/read-file
   vubis/order-and-group
   (layout.fop/document :hörbuch)
   (layout.fop/generate-pdf! out)))

(defn neue-braillebücher [in out]
  (->> in
   vubis/read-file
   vubis/order-and-group
   :braille
   layout.dtbook/dtbook
   (spit (io/file out))))

