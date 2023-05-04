(ns catalog.unicode-normalization
  (:import java.text.Normalizer
           java.text.Normalizer$Form))

(defn normalize
  "Normalize a unicode string using Normalization Form Canonical Composition"
  [s]
  (java.text.Normalizer/normalize s java.text.Normalizer$Form/NFC))
