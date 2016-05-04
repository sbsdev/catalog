(ns catalog.core
  (:require [catalog
             [validation :as validation]
             [vubis :as vubis]]
            [catalog.layout
             [dtbook :as layout.dtbook]
             [fop :as layout.fop]
             [obi :as layout.obi]]
            [clojure.java.io :as io]
            [schema.core :as s]))

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

(defn neu-in-grossdruck-fop
  "Generate a FOP document `out` given the Marc21 XML `input` and the
  markdown `editorial` and `recommendations`. This can be used for
  debugging. Generally you're better off using `neu-in-grossdruck` as
  this produces a PDF directly."
  [in out editorial recommendations]
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
  (-> in
   vubis/read-file
   vubis/order-and-group
   :braille
   (layout.dtbook/dtbook (slurp editorial) (slurp recommendations))
   (->> (spit (io/file out)))))

(defn hörfilme-all [in out]
  (-> in
   vubis/read-file
   (vubis/order-and-group vubis/get-update-keys-hörfilm)
   (layout.fop/document :hörfilm nil nil)
   (layout.fop/generate-pdf! out)))

(defn ludo-all [in out]
  (-> in
   vubis/read-file
   (vubis/order-and-group vubis/get-update-keys-ludo)
   (layout.fop/document :ludo nil nil)
   (layout.fop/generate-pdf! out)))

(defn taktil-all [in out]
  (-> in
   vubis/read-file
   (vubis/order-and-group vubis/get-update-keys-taktil)
   (layout.fop/document :taktilesbuch nil nil)
   (layout.fop/generate-pdf! out)))

(defn ncc [in out editorial recommendations]
  (->
   in
   vubis/read-file
   (vubis/order-and-group vubis/get-update-keys-neu-als-hörbuch)
   (layout.obi/dtbook (slurp editorial) (slurp recommendations))
   (->> (spit (io/file out)))))

(defn validate [in]
  (let [checker (s/checker validation/CatalogItem)]
    (->>
     in
     vubis/read-file
     vubis/collate-all-duplicate-items
     (keep #(when-let [error (checker %)] [error %])))))
