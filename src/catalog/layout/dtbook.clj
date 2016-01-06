(ns catalog.layout.dtbook
  "Render catalog items as [DTBook
  XML](http://www.daisy.org/z3986/2005/Z3986-2005.html) to be
  converted to Braille later in the tool chain"
  (:require [catalog.layout.common :as layout :refer [wrap]]
            [clj-time.coerce :as time.coerce]
            [clj-time.core :as time.core]
            [clojure.data.xml :as xml]
            [clojure.string :as string]))

(def translations (merge layout/translations
                         {[:kurzschrift false] "K"
                          [:vollschrift false] "V"
                          [:kurzschrift true] "wtz."
                          [:vollschrift true] "wtz."}))

(defn empty-or-blank? [s]
  (or (nil? s)
      (and (coll? s) (empty? s))
      (and (string? s) (string/blank? s))
      false))

(defn catalog-entry [{:keys [creator record-id title subtitles name-of-part source-publisher
                             source-date genre-text description instrument producer-brief
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
   [:p {:brl:class "gen"} (wrap genre-text "Genre: ")]
   [:p {:brl:class "ann"} (wrap description)]
   [:p {:brl:class "ins"} (wrap instrument)]
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
   (for [item items] (catalog-entry item))])

(defn subgenre-entry [subgenre items]
  (when-let [items (subgenre items)]
    [:level2
     [:h2 (layout/translations subgenre "FIXME")]
     (catalog-entries items)]))

(defn subgenre-entries [items]
  (for [subgenre layout/subgenres] (subgenre-entry subgenre items)))

(defn genre-entries [genre items]
  (when-let [items (genre items)]
    [:level1
     [:h1 (layout/translations genre "FIXME")]
     (cond
       (#{:kinder-und-jugendbücher} genre) (subgenre-entries items)
       :else (catalog-entries items))]))

(defn document [title items &
                {:keys [creator volume date language]
                 :or {creator "SBS Schweizerische Bibliothek für Blinde, Seh- und Lesebehinderte"
                      volume (layout/volume-number (time.core/today))
                      date (time.coerce/to-date (time.core/today))
                      language "de"}}]
  [:dtbook {:xmlns "http://www.daisy.org/z3986/2005/dtbook/"
            :xmlns:brl "http://www.daisy.org/z3986/2009/braille/"
            :version "2005-3-sbs-minimal" :xml:lang language}
   [:head
    (for [[k v]
          {:dc:Title title
           :dc:Creator creator
           :dc:Publisher creator
           :dc:Date "FIXME"
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
     [:level1 [:h1 "Editorial"] [:p "..."]]
     (for [genre layout/braille-genres] (genre-entries genre items))]]])

(defn dtbook
  [items]
  (-> (document "Neu in Braille" items)
      xml/sexp-as-element
      xml/indent-str))
