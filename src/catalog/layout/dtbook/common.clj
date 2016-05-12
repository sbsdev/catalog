(ns catalog.layout.dtbook.common
  (:require [catalog.layout.common :as layout]))

(defn- heading-keyword [level]
  (keyword (str "h" level)))

(defn- level-keyword [level]
  (keyword (str "level" level)))

(defn md-to-dtbook
  ([markdown level]
   (md-to-dtbook markdown (repeat level "") {}))
  ([markdown path path-to-numbers]
   (let [level (inc (count path))
        headers (layout/md-extract-headings markdown)]
    (if (seq headers)
      (for [h headers]
        [(level-keyword level)
         [(heading-keyword level)
          (format "%s%s" (layout/section-numbers (get path-to-numbers (conj path h))) h)]
         [:p]])
      ;; if there are no headers then just emit an empty <p/> to make
      ;; sure the generated dtbook is valid
      [:p]))))

