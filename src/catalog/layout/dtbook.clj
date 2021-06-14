(ns catalog.layout.dtbook
  "Render catalog items as [DTBook
  XML](http://www.daisy.org/z3986/2005/Z3986-2005.html) to be
  converted to Braille later in the tool chain"
  (:require [catalog.layout.common :as layout :refer [empty-or-blank? wrap]]
            [clj-time
             [core :as time.core]
             [format :as time.format]]
            [clojure
             [set :as set]
             [string :as string]
             [walk :as walk]]
            [clojure.data.xml :as xml]
            [endophile.core :as endophile]))

(def translations (merge layout/translations
                         {[:kurzschrift false] "K"
                          [:vollschrift false] "V"
                          [:kurzschrift true] "wtz."
                          [:vollschrift true] "wtz."}))

(defmulti to-dtbook (fn [{:keys [tag attrs content]}] tag))
(defmethod to-dtbook :p [{content :content}] [:p content])
(defmethod to-dtbook :a [{content :content {href :href} :attrs}]
  [:a {:href href :external "true"} (if (not-empty content) content href)])
;; make sure the md headings are lower than h1. That way they will
;; fit properly into the hierarchy, where :editorial is h1 and the
;; actual md content is below
(defmethod to-dtbook :h1 [{content :content}] [:h2 content])
(defmethod to-dtbook :h2 [{content :content}] [:h3 content])
(defmethod to-dtbook :h3 [{content :content}] [:h4 content])
(defmethod to-dtbook :ul [{content :content}] [:list {:type "ul"} content])
(defmethod to-dtbook :li [{content :content}] [:li content])
(defmethod to-dtbook :em [{content :content}] [:em content])
(defmethod to-dtbook :strong [{content :content}] [:strong content])
(defmethod to-dtbook :br [_] [:br])
(defmethod to-dtbook :default [{content :content}] [:p content]) ; just assume :p

(defn- node? [node]
  (and (map? node) (or (set/subset? #{:tag :content} (set (keys node))) (= (:tag node) :br))))

(defn- visitor [node]
  (if (node? node)
    (to-dtbook node)
    node))

(defn md-to-dtbook [markdown]
  (->>
   markdown
   endophile/mp
   endophile/to-clj
   (walk/postwalk #(visitor %))))

(defn- entry-heading-sexp
  [{:keys [creator title subtitles name-of-part source-publisher source-date]}]
  [:p {:brl:class "tit"} (wrap creator "" ": " false)
   (->> [(wrap title) (for [s subtitles] (wrap s)) (wrap name-of-part)]
        flatten
        (remove empty-or-blank?)
        (string/join " "))
   (when (or source-publisher source-date) " - ")
   (wrap source-publisher "" (if source-date ", " "") false) (wrap (layout/year source-date))])

(defmulti entry-sexp
  "Return a hiccup style sexp for a given item."
  (fn [{fmt :format print-and-braille? :print-and-braille?}]
    (cond
      ;; render print-and-braille items the same way as a :braille item
      print-and-braille? :braille
      :else fmt)))

(defn- ausleihe
  [library-signature]
  (when library-signature
    [:p {:brl:class "aus"} "Ausleihe: "
     (binding [layout/translations translations] (layout/braille-signatures library-signature))]))

(defn- ausleihe-simple
  [library-signature]
  (when library-signature
    [:p {:brl:class "aus"} (wrap library-signature "Ausleihe: ")]))

(defn- verkauf
  [{:keys [product-number price price-on-request?]}]
  (when (or product-number price-on-request?)
    [:p {:brl:class "ver"} "Verkauf: " (wrap price "" ". " false)
     (binding [layout/translations translations] (layout/braille-signatures product-number))]))

(defn- description-sexp
  [description]
  [:p {:brl:class "ann"} (wrap description)])

(defn- producer-sexp
  ([producer-brief]
   [:p {:brl:class "pro"} (wrap producer-brief)])
  ([producer-brief rucksackbuch-number]
   [:p {:brl:class "pro"} (wrap producer-brief "" ", " false) (wrap rucksackbuch-number "Rucksackbuch Nr. ")]))

(defn- genre-sexp
  [genre-text]
  [:p {:brl:class "gen"} (wrap genre-text "Genre: ")])

(defmethod entry-sexp :braille
  [{:keys [creator title subtitles name-of-part source-publisher
           source-date genre-text description producer-brief
           rucksackbuch? rucksackbuch-number
           library-signature] :as item}]
  [:div {:brl:class "ps"}
   (entry-heading-sexp item)
   (genre-sexp genre-text)
   (description-sexp description)
   (if rucksackbuch?
     (producer-sexp producer-brief rucksackbuch-number)
     (producer-sexp producer-brief))
   (ausleihe library-signature)
   (verkauf item)])

(defmethod entry-sexp :musiknoten
  [{:keys [creator title subtitles name-of-part source-publisher
           source-date genre-text description producer-brief
           library-signature] :as item}]
  [:div {:brl:class "ps"}
   (entry-heading-sexp item)
   (description-sexp description)
   (producer-sexp producer-brief)
   (ausleihe library-signature)
   (verkauf item)])

(defmethod entry-sexp :taktilesbuch
  [{:keys [creator title subtitles name-of-part source-publisher
           source-date genre-text description producer-brief
           library-signature] :as item}]
  [:div {:brl:class "ps"}
   (entry-heading-sexp item)
   (description-sexp description)
   (producer-sexp producer-brief)
   (ausleihe library-signature)
   (verkauf item)])

(defmethod entry-sexp :hörbuch
  [{:keys [genre-text description duration narrators producer-brief
           produced-commercially? library-signature product-number price price-on-request?] :as item}]
  [:div {:brl:class "ps"}
   (entry-heading-sexp item)
   (genre-sexp genre-text)
   (description-sexp description)
   [:p {:brl:class "duration"} (wrap duration "" " Min., " false) (layout/render-narrators narrators)]
   (if produced-commercially?
     [:p {:brl:class "pro"} (wrap producer-brief "" ", Hörbuch aus dem Handel")]
     (producer-sexp producer-brief))
   (ausleihe-simple library-signature)
   (when (or product-number price-on-request?)
     [:p {:brl:class "ver"} "Verkauf: " product-number ", " price])])

(defmethod entry-sexp :grossdruck
  [{:keys [genre-text description library-signature volumes
           product-number price price-on-request?] :as item}]
  [:div {:brl:class "ps"}
   (entry-heading-sexp item)
   (genre-sexp genre-text)
   (description-sexp description)
   (when library-signature
     [:p {:brl:class "aus"} "Ausleihe: " library-signature (wrap volumes ", " " Bd. " false)])
   (when (or product-number price-on-request?)
     [:p {:brl:class "ver"} "Verkauf: " price])])

(defmethod entry-sexp :e-book
  [{:keys [genre-text description library-signature
           accompanying-material] :as item}]
  [:div {:brl:class "ps"}
   (entry-heading-sexp item)
   (genre-sexp genre-text)
   (description-sexp description)
   (when library-signature
     [:p {:brl:class "aus"} "Ausleihe: " library-signature (wrap accompanying-material ", " "" false)])])

(defmethod entry-sexp :hörfilm
  [{:keys [personel-text movie_country genre-text
           description producer library-signature target-audience] :as item}]
  [:div {:brl:class "ps"}
   (entry-heading-sexp item)
   [:p {:brl:class "personel"} (wrap personel-text)]
   [:p {:brl:class "country"} (wrap movie_country)]
   (let [age-limit (get layout/target-audience-to-age-limit target-audience "(ab 18)")]
     (genre-sexp (str genre-text " " age-limit)))
   (description-sexp description)
   (producer-sexp producer)
   (ausleihe-simple library-signature)])

(defmethod entry-sexp :ludo
  [{:keys [source-publisher genre-text description
           game-description accompanying-material library-signature] :as item}]
  [:div {:brl:class "ps"}
   (entry-heading-sexp item)
   [:p {:brl:class "publisher"} (wrap source-publisher)]
   (genre-sexp genre-text)
   (description-sexp description)
   [:p {:brl:class "game-desc"} (wrap game-description)]
   (ausleihe library-signature)
   (verkauf item)])

(defn catalog-entries [items]
  [:div {:brl:class "list"}
   (map entry-sexp items)])

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

(defn- document-head
  [title creator date language]
  [:head
   (for [[k v]
         {:dc:Title title
          :dc:Creator creator
          :dc:Publisher creator
          :dc:Date (time.format/unparse (time.format/formatters :date) date)
          :dc:Format "ANSI/NISO Z39.86-2005"
          :dc:Language language}]
     [:meta {:name (name k) :content v}])])

(defn- document-frontmatter
  [title & body]
  [:frontmatter
   [:doctitle title]
   ;; apparently a long docauthor is annoying, as it wraps around and
   ;; messes up the sbsform macros. So, as this isn't shown anyway
   ;; just use a fake value
   [:docauthor "SBS"]
   body])

(defn- standard-catalog?
  "If `items` is a map, i.e. are grouped by genre, then we assume that
  we are dealing with a standard catalog. Otherwise if the items are
  just a flat list then we have a custom catalog."
  [items]
  (map? items))

(defmulti document-sexp (fn [items _] (if (standard-catalog? items) :standard :custom)))

(defmethod document-sexp :standard
  [items {:keys [year issue editorial recommendation]}]
  (let [title (translations :catalog-braille)
        creator "SBS Schweizerische Bibliothek für Blinde, Seh- und Lesebehinderte"
        date (time.core/now)
        language "de"]
    [:dtbook {:xmlns "http://www.daisy.org/z3986/2005/dtbook/"
              :xmlns:brl "http://www.daisy.org/z3986/2009/braille/"
              :version "2005-3-sbs-full" :xml:lang language}
     (document-head title creator date language)
     [:book
      (document-frontmatter title
       [:level1
        [:p {:brl:class (format "nr_%s" issue)}]
        [:p {:brl:class (format "jr_%s" year)}]])
      [:bodymatter
       (when-not (string/blank? editorial)
         [:level1
          [:h1 (translations :editorial)]
          (md-to-dtbook editorial)])
       (when-not (string/blank? recommendation)
         [:level1
          [:h1 (translations :recommendations)]
          [:level2
           (md-to-dtbook recommendation)]])
       (when-let [items (not-empty
                         (dissoc items :musiknoten :taktilesbuch))]
         [:level1 [:h1 (translations :braille)]
          (map genre-entry items)])
       (when-let [items (not-empty (:musiknoten items))]
         [:level1 [:h1 (format "Neue %s" (translations :musiknoten))]
          (catalog-entries items)])
       (when-let [items (not-empty (:taktilesbuch items))]
         [:level1 [:h1 (format "Neue %s" (translations :taktilesbuch))]
          (catalog-entries items)])]]]))

(defmethod document-sexp :custom
  [items {:keys [query customer]}]
  (let [title (translations :catalog-custom)
        creator "SBS Schweizerische Bibliothek für Blinde, Seh- und Lesebehinderte"
        date (time.core/now)
        language "de"]
    [:dtbook {:xmlns "http://www.daisy.org/z3986/2005/dtbook/"
              :xmlns:brl "http://www.daisy.org/z3986/2009/braille/"
              :version "2005-3-sbs-full" :xml:lang language}
     (document-head title creator date language)
     [:book
      (document-frontmatter title
       [:level1
        [:p {:brl:class "query"} query]
        [:p {:brl:class "customer"} (format "für %s" customer)]])
      [:bodymatter
       [:level1
        (catalog-entries items)]]]]))

(defn dtbook
  [items options]
  (-> (document-sexp items options)
      xml/sexp-as-element
      xml/indent-str))
