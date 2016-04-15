(ns catalog.layout.dtbook
  "Render catalog items as [DTBook
  XML](http://www.daisy.org/z3986/2005/Z3986-2005.html) to be
  converted to Braille later in the tool chain"
  (:require [catalog.layout.common :as layout
             :refer [empty-or-blank? wrap]]
            [clj-time
             [core :as time.core]
             [format :as time.format]]
            [clojure.data.xml :as xml]
            [clojure.string :as string]))

(def translations (merge layout/translations
                         {[:kurzschrift false] "K"
                          [:vollschrift false] "V"
                          [:kurzschrift true] "wtz."
                          [:vollschrift true] "wtz."}))

(defn catalog-entry [{:keys [creator record-id title subtitles name-of-part source-publisher
                             source-date genre genre-text description producer-brief
                             rucksackbuch? rucksackbuch-number
                             library-signature product-number price]}]
  [:div {:brl:class "ps"}
   [:p {:brl:class "tit"} (wrap creator "" ": " false)
    (->> [(wrap title) (for [s subtitles] (wrap s)) (wrap name-of-part)]
         flatten
         (remove empty-or-blank?)
         (string/join " "))
    " - "
    (wrap source-publisher "" ", " false) (wrap (layout/year source-date))]
   (when ((set layout/genres) genre)
     ;; don't show the genre for :musiknoten and :taktilesbuch
     [:p {:brl:class "gen"} (wrap genre-text "Genre: ")])
   [:p {:brl:class "ann"} (wrap description)]
   (if rucksackbuch?
     [:p {:brl:class "pro"} (wrap producer-brief "" ", " false) (wrap rucksackbuch-number "Rucksackbuch Nr. ")]
     [:p {:brl:class "pro"} (wrap producer-brief)])
   (when library-signature
     [:p {:brl:class "aus"} "Ausleihe: "
      (binding [layout/translations translations] (layout/braille-signatures library-signature))])
   (when product-number
     [:p {:brl:class "ver"} "Verkauf: " (wrap price "" ". " false)
      (binding [layout/translations translations] (layout/braille-signatures product-number))])])

(defn catalog-entries [items]
  [:div {:brl:class "list"}
   (map catalog-entry items)])

(defn subgenre-entry [[subgenre items]]
  [:level3
   [:h3 (layout/translations subgenre "FIXME")]
   (catalog-entries items)])

(defn subgenre-entries [items]
  (map subgenre-entry items))

(defn genre-entry [[genre items]]
  [:level2
   [:h2 (layout/translations genre "FIXME")]
   (cond
     (#{:kinder-und-jugendbücher} genre) (subgenre-entries items)
     :else (catalog-entries items))])

(defn document [items title editorial recommendations &
                {:keys [creator volume date language]
                 :or {creator "SBS Schweizerische Bibliothek für Blinde, Seh- und Lesebehinderte"
                      volume (layout/volume-number (time.core/today))
                      date (time.core/now)
                      language "de"}}]
  [:dtbook {:xmlns "http://www.daisy.org/z3986/2005/dtbook/"
            :xmlns:brl "http://www.daisy.org/z3986/2009/braille/"
            :version "2005-3-sbs-minimal" :xml:lang language}
   [:head
    (for [[k v]
          {:dc:Title title
           :dc:Creator creator
           :dc:Publisher creator
           :dc:Date (time.format/unparse (time.format/formatters :date) date)
           :dc:Format "ANSI/NISO Z39.86-2005"
           :dc:Language language}]
      [:meta {:name (name k) :content v}])]
   [:book
    [:frontmatter
     [:doctitle title]
     ;; apparently a long docauthor is annoying, as it wraps around and
     ;; messes up the sbsform macros. So, as this isn't shown anyway
     ;; just use a fake value
     [:docauthor "SBS"]
     [:level1
      [:p {:brl:class (format "nr_%s" volume)}]
      [:p {:brl:class (format "jr_%s" (layout/year date))}]]]
    [:bodymatter
     [:level1 [:h1 (translations :editorial)] [:p "..."]]
     [:level1 [:h1 (translations :recommendations)] [:p "..."]]
     (when-let [items (not-empty
                       (dissoc items :musiknoten :taktilesbuch))]
       [:level1 [:h1 (translations :braille)]
        (map genre-entry items)])
     (when-let [items (not-empty (:musiknoten items))]
       [:level1 [:h1 (format "Neue %s" (translations :musiknoten))]
        (catalog-entries items)])
     (when-let [items (not-empty (:taktilesbuch items))]
       [:level1 [:h1 (format "Neue %s" (translations :taktilesbuch))]
        (catalog-entries items)])]]])

(defn dtbook
  [items editorial recommendations]
  (-> (document items (translations :catalog-braille) editorial recommendations)
      xml/sexp-as-element
      xml/indent-str))
