(ns catalog.repl
  "Convenience functions for manipulations at the repl"
  (:require [catalog
             [db :as db]
             [validation :as validation]
             [vubis :as vubis]]
            [catalog.layout.fop :as layout.fop]
            [schema.core :as s]))

(defn neu-im-sortiment-fop
  "Generate the xsl-fo XML for the *Neu im Sortiment* catalog in file
  `out` for given `year` and `issue`"
  [year issue out]
  (->
   (db/read-catalog year issue)
   vubis/order-and-group
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

(defn validate [in]
  (let [checker (s/checker validation/CatalogItem)]
    (->>
     in
     vubis/read-file
     vubis/collate-all-duplicate-items
     (keep #(when-let [error (checker %)] [error %])))))
