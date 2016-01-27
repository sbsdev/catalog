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
   (layout.fop/document :all-formats nil nil)
   (layout.fop/generate-pdf! out)))

(defn neu-in-grossdruck [in out editorial recommendations]
  (-> in
   vubis/read-file
   vubis/order-and-group
   (layout.fop/document :grossdruck (slurp editorial) (slurp recommendations))
   (layout.fop/generate-pdf! out)))

(defn neu-in-grossdruck [in out editorial recommendations]
  (-> in
   vubis/read-file
   vubis/order-and-group
   (layout.fop/generate-document :grossdruck (slurp editorial) (slurp recommendations) out)))

(defn neu-als-hörbuch [in out editorial recommendations]
  (-> in
   vubis/read-file
   (vubis/order-and-group vubis/get-update-keys-neu-als-hörbuch)
   (layout.fop/document :hörbuch (slurp editorial) (slurp recommendations))
   (layout.fop/generate-pdf! out)))

(defn neu-in-braille [in out editorial recommendations]
  (->> in
   vubis/read-file
   vubis/order-and-group
   :braille
   layout.dtbook/dtbook (slurp editorial) (slurp recommendations)
   (spit (io/file out))))

