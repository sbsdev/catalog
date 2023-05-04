(ns catalog.layout.text
  "Render catalog items as plain text"
  (:require [catalog.layout.common :as layout :refer [empty-or-blank? wrap]]
            [clojure.set :as set]
            [clojure.string :as string]
            [clojure.walk :as walk]
            [endophile.core :as endophile]
            [java-time :as time]
            [medley.core :refer [assoc-some]]))

(def ^:private dos-newline "\r\n")
(def ^:private double-newline (str dos-newline dos-newline))

(defmulti to-text (fn [{:keys [tag attrs content]}] tag))

(defmethod to-text :a [{content :content {href :href} :attrs}]
  (if (not-empty content) content href))
(defmethod to-text :h1 [{content :content}] (str content double-newline))
(defmethod to-text :h2 [{content :content}] (str  content double-newline))
(defmethod to-text :h3 [{content :content}] (str  content double-newline))
(defmethod to-text :ul [{content :content}] [:list {:type "ul"} content])
(defmethod to-text :li [{content :content}] [:li content])
(defmethod to-text :br [_] dos-newline)
(defmethod to-text :default [{content :content}] content) ; just assume :p

(defn- node? [node]
  (and (map? node) (or (set/subset? #{:tag :content} (set (keys node))) (= (:tag node) :br))))

(defn- visitor [node]
  (if (node? node)
    (to-text node)
    node))

(defn md-to-text [markdown]
  (->>
   markdown
   endophile/mp
   endophile/to-clj
   (walk/postwalk #(visitor %))
   string/join))

(def ^:private line-length 72)

(defn wrap-line [text]
  ;; see http://rosettacode.org/wiki/Word_wrap#Clojure
  (string/join dos-newline (re-seq (re-pattern (str ".{1," line-length "}(?:\\s|\\z)")) text)))

(defn- entry-heading-str
  [{:keys [creator title subtitles name-of-part source-publisher source-date]}]
  (wrap-line
   (str
    (wrap creator "" ": " false)
    (->> [(wrap title) (for [s subtitles] (wrap s)) (wrap name-of-part)]
         flatten
         (remove empty-or-blank?)
         (string/join " "))
    (when (or source-publisher source-date) " - ")
    (wrap source-publisher "" (if source-date ", " "") false) (wrap (layout/year source-date)))))

(defmulti entry-str
  "Return a rendered string for a given item."
  (fn [{fmt :format print-and-braille? :print-and-braille?}]
    (cond
      ;; render print-and-braille items the same way as a :braille item
      print-and-braille? :braille
      :else fmt)))

(defn- ausleihe
  [library-signature]
  (when library-signature
    (wrap-line
     (str "Ausleihe: " (layout/braille-signatures library-signature)))))

(defn- ausleihe-simple
  [library-signature]
  (when library-signature
    (wrap library-signature "Ausleihe: " "" false)))

(defn- verkauf
  [{:keys [product-number price price-on-request?]}]
  (when (or product-number price-on-request?)
    (wrap-line
     (str "Verkauf: "
          (wrap price "" ". " false)
          (layout/braille-signatures product-number)))))

(defn- description-str
  [description]
  (wrap-line (wrap description)))

(defn- producer-str
  ([producer-brief]
   (wrap producer-brief))
  ([producer-brief rucksackbuch-number]
   (wrap producer-brief "" ", " false) (wrap rucksackbuch-number "Rucksackbuch Nr. ")))

(defn- genre-str
  [genre-text]
  (wrap genre-text "Genre: "))

(defn- join [& more]
  (->> more
   (remove empty-or-blank?)
   (string/join dos-newline)))

(defmethod entry-str :braille
  [{:keys [genre-text description producer-brief
           rucksackbuch? rucksackbuch-number
           library-signature] :as item}]
  (join
   (entry-heading-str item)
   (genre-str genre-text)
   (description-str description)
   (if rucksackbuch?
     (producer-str producer-brief rucksackbuch-number)
     (producer-str producer-brief))
   (ausleihe library-signature)
   (verkauf item)))

(defmethod entry-str :musiknoten
  [{:keys [description producer-brief library-signature] :as item}]
  (join
   (entry-heading-str item)
   (description-str description)
   (producer-str producer-brief)
   (ausleihe library-signature)
   (verkauf item)))

(defmethod entry-str :taktilesbuch
  [{:keys [description producer-brief library-signature] :as item}]
  (join
   (entry-heading-str item)
   (description-str description)
   (producer-str producer-brief)
   (ausleihe library-signature)
   (verkauf item)))

(defmethod entry-str :hörbuch
  [{:keys [genre-text description duration narrators producer-brief
           produced-commercially? library-signature
           product-number price price-on-request?] :as item}]
  (join
   (entry-heading-str item)
   (genre-str genre-text)
   (description-str description)
   (str (wrap duration "" " Min., " false) (layout/render-narrators narrators))
   (if produced-commercially?
     (wrap producer-brief "" ", Hörbuch aus dem Handel")
     (producer-str producer-brief))
   (ausleihe-simple library-signature)
   (when (or product-number price-on-request?)
     (str "Verkauf: " (when product-number (str product-number ", ")) price))))

(defmethod entry-str :text-hörbuch
  [{:keys [genre-text description duration narrators producer-brief
           library-signature accompanying-material] :as item}]
  (join
   (entry-heading-str item)
   (genre-str genre-text)
   (description-str description)
   (str (wrap duration "" " Min., " false) (layout/render-narrators narrators))
   (when library-signature
     (str "Ausleihe: " library-signature (wrap accompanying-material ", " "" false)))))

(defmethod entry-str :grossdruck
  [{:keys [genre-text description library-signature volumes
           product-number price price-on-request?] :as item}]
  (join
   (entry-heading-str item)
   (genre-str genre-text)
   (description-str description)
   (when library-signature
     (str "Ausleihe: " library-signature (wrap volumes ", " " Bd. " false)))
   (when (or product-number price-on-request?)
     (str "Verkauf: " price))))

(defmethod entry-str :e-book
  [{:keys [genre-text description library-signature
           accompanying-material] :as item}]
  (join
   (entry-heading-str item)
   (genre-str genre-text)
   (description-str description)
   (when library-signature
     (str "Ausleihe: " library-signature (wrap accompanying-material ", " "" false)))))

(defmethod entry-str :hörfilm
  [{:keys [personel-text movie_country genre-text
           description producer library-signature target-audience] :as item}]
  (join
   (entry-heading-str item)
   (wrap personel-text)
   (wrap movie_country)
   (let [age-limit (get layout/target-audience-to-age-limit target-audience "(ab 18)")]
     (genre-str (str genre-text " " age-limit)))
   (description-str description)
   (producer-str producer)
   (ausleihe-simple library-signature)))

(defmethod entry-str :ludo
  [{:keys [source-publisher genre-text description
           game-description library-signature] :as item}]
  (join
   (entry-heading-str item)
   (wrap source-publisher)
   (genre-str genre-text)
   (description-str description)
   (wrap game-description)
   (ausleihe library-signature)
   (verkauf item)))

(defn- level-str [items path path-to-numbers]
  (str
   (when-let [section-title (last path)]
     (format "%s%s%s"
             (layout/section-numbers (get path-to-numbers path))
             (layout/translations section-title "FIXME")
             double-newline))
   (cond
     ;; handle the special case where the editorial or the recommendations are passed in the tree
     (#{:editorial :recommendation} (last path)) (md-to-text items)
     ;; handle a list of entries
     (sequential? items) (string/join double-newline (map entry-str items))
     :else (string/join double-newline (map #(level-str (get items %) (conj path %) path-to-numbers) (keys items))))))

(defn- standard-catalog?
  "If `items` is a map, i.e. are grouped by genre, then we assume that
  we are dealing with a standard catalog. Otherwise if the items are
  just a flat list then we have a custom catalog."
  [items]
  (map? items))

(defmulti document-str (fn [items _] (if (standard-catalog? items) :standard :custom)))

(defn- impressum []
  (let [creator (layout/translations :sbs)]
    (string/join
     dos-newline
     ["Herausgeber:"
      creator
      "Grubenstrasse 12"
      "CH-8045 Zürich"
      "Fon +41 43 333 32 32"
      "www.sbs.ch"
      "nutzerservice@sbs.ch"
      (format "© %s" creator)])))

(defmethod document-str :standard
  [items {:keys [year issue editorial recommendation]}]
  (let [title (layout/translations :catalog-all)
        ;; add editorial and recommendation unless they are blank
        items (assoc-some items
               :editorial (layout/empty-string-to-nil editorial)
               :recommendation (layout/empty-string-to-nil recommendation))
        path-to-numbers (layout/path-to-number items)]
    (string/join
     double-newline
     (concat
      [title
       (format "%s-%s" year issue)
       (impressum)]
      (map #(level-str (get items %) [%] path-to-numbers) (keys items))))))

(defmethod document-str :custom
  [items {:keys [query customer]}]
  (let [title (layout/translations :catalog-custom)
        path-to-numbers (layout/path-to-number items)]
    (string/join double-newline
     [title
      (format "Stand %s" (time/format "dd.MM.YYYY" (time/local-date)))
      query
      (format "für %s" customer)
      (impressum)
      (level-str items [] path-to-numbers)])))

(defn text
  [items options]
  (document-str items options))
