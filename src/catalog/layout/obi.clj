(ns catalog.layout.obi
  "Render a structure of all catalog items as [DTBook
  XML](http://www.daisy.org/z3986/2005/Z3986-2005.html). This will be
  used to narrate the book in Obi later in the tool chain"
  (:require [catalog.layout.common :as layout]
            [clj-time.core :as time.core]
            [clojure.data.xml :as xml]))

(defn- heading-keyword [level]
  (keyword (str "h" level)))

(defn- level-keyword [level]
  (keyword (str "level" level)))

(defn- entry-sexp [level {:keys [creator title]}]
  [(level-keyword level)
   [(heading-keyword level)
    (if creator (format "%s: %s" creator title) title)]
   [:p]])

(defn- md-to-obi [markdown path path-to-numbers]
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
      [:p])))

(defn- level-sexp [items path path-to-numbers]
  (let [level (count path)]
    (println level (last path))
    [(level-keyword level)
     [(heading-keyword level)
      (format "%s%s"
              (layout/section-numbers (get path-to-numbers path))
              (layout/translations (last path)))]
     (cond
       ;; handle the special case where the editorial or the recommendations are passed in the tree
       (#{:editorial :recommendations} (last path)) (md-to-obi items path path-to-numbers)
       ;; handle a list of entries
       (vector? items) (map #(entry-sexp (inc level) %) items)
       :else (map #(level-sexp (get items %) (conj path %) path-to-numbers) (keys items)))]))

(defn document [items editorial recommendations &
                {:keys [creator date language]
                 :or {creator "SBS Schweizerische Bibliothek für Blinde, Seh- und Lesebehinderte"
                      date (time.core/today)
                      language "de"}}]
  (let [title (format "Neu als Hörbuch Nr.%s/%s" (layout/volume-number date) (layout/year date))
        subitems (-> items
                     ;; remove all formats but fmt. Unfortunately we
                     ;; cannot use select-keys as we need to retain
                     ;; the sorted map.
                     (#(apply dissoc % (remove #{:hörbuch} (keys %))))
                     (assoc :editorial editorial
                            :recommendations recommendations))
        path-to-numbers (layout/path-to-number subitems)]
    [:dtbook {:xmlns "http://www.daisy.org/z3986/2005/dtbook/"
              :xmlns:brl "http://www.daisy.org/z3986/2009/braille/"
              :version "2005-3-sbs-minimal" :xml:lang language}
     [:head
      (for [[k v]
            {:dc:Title title
             :dc:Creator creator
             :dc:Subject ""
             :dc:Description ""
             :dc:Publisher creator
             :dc:Date (format "%s-%s-%s" (time.core/year date) (time.core/month date) (time.core/day date))
             :dc:Format "ANSI/NISO Z39.86-2005"
             :dc:Language language}]
        [:meta {:name (name k) :content v}])]
     [:book
      [:frontmatter
       [:doctitle title]
       [:docauthor creator]
       [:level1
        [:h1 "Zu diesem Katalog"]
        [:level2
         [:h2 "Produktion"] [:p]]
        [:level2
         [:h2 "Es lesen"] [:p]]]
       [:level1 [:h1 "Struktur dieser Ausgabe"] [:p]]]
      [:bodymatter
       [:level1 [:h1 "Inhaltsverzeichnis"] [:p]]
       (map #(level-sexp (get subitems %) [%] path-to-numbers) (keys subitems))]
      [:rearmatter
       [:level1 [:h1 "Impressum"] [:p]]
       [:level1 [:h1 (format "Ende von %s" title)] [:p]]]]]))

(defn dtbook [items editorial recommendations]
  (-> (document items editorial recommendations)
      xml/sexp-as-element
      xml/indent-str))
