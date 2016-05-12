(ns catalog.layout.obi
  "Render a structure of all catalog items as [DTBook
  XML](http://www.daisy.org/z3986/2005/Z3986-2005.html). This will be
  used to narrate the book in Obi later in the tool chain"
  (:require [catalog.layout.common :as layout]
            [catalog.layout.dtbook.common :as dtbook]
            [clj-time
             [coerce :as time.coerce]
             [core :as time.core]
             [format :as time.format]]
            [clojure.data.xml :as xml]))

(defn- entry-sexp [level {:keys [creator title]}]
  [(level-keyword level)
   [(heading-keyword level)
    (if creator (format "%s: %s" creator title) title)]
   [:p]])

(defn- level-sexp [items path path-to-numbers]
  (let [level (count path)]
    [(level-keyword level)
     [(heading-keyword level)
      (format "%s%s"
              (layout/section-numbers (get path-to-numbers path))
              (layout/translations (last path)))]
     (cond
       ;; handle the special case where the editorial or the recommendations are passed in the tree
       (#{:editorial :recommendations} (last path)) (dtbook/md-to-dtbook items path path-to-numbers)
       ;; handle a list of entries
       (vector? items) (map #(entry-sexp (inc level) %) items)
       :else (map #(level-sexp (get items %) (conj path %) path-to-numbers) (keys items)))]))

(defn document [items editorial recommendations &
                {:keys [creator date language]
                 :or {creator "SBS Schweizerische Bibliothek für Blinde, Seh- und Lesebehinderte"
                      date (time.coerce/to-date (time.core/today))
                      language "de"}}]
  (let [title (format "%s Nr.%s/%s"
                      (layout/translations :catalog-hörbuch)
                      (layout/volume-number date) (layout/year date))
        subitems (-> items
                     ;; remove all formats but fmt. Unfortunately we
                     ;; cannot use select-keys as we need to retain
                     ;; the sorted map.
                     (#(apply dissoc % (remove #{:hörbuch} (keys %))))
                     (assoc :editorial editorial
                            :recommendations recommendations))
        path-to-numbers (layout/path-to-number subitems)]
    [:dtbook {:xmlns "http://www.daisy.org/z3986/2005/dtbook/"
              :version "2005-3" :xml:lang language}
     [:head
      (for [[k v]
            {:dc:Title title
             :dc:Creator creator
             :dc:Subject ""
             :dc:Description ""
             :dc:Publisher creator
             :dc:Date (time.format/unparse (time.format/formatters :date) date)
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
