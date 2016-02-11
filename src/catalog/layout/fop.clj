(ns catalog.layout.fop
  (:require [catalog.layout.common :as layout :refer [wrap]]
            [clojure
             [set :as set]
             [string :as string]
             [walk :as walk]]
            [clojure.data.xml :as xml]
            [clojure.java.io :as io]
            [endophile.core :as endophile]
            [clj-time.core :as time.core])
  (:import java.io.StringReader
           javax.xml.transform.sax.SAXResult
           javax.xml.transform.stream.StreamSource
           javax.xml.transform.TransformerFactory
           [org.apache.fop.apps FopFactory MimeConstants]))

(def ^:private region-body-margin-top 15)

(defn- to-mm [n]
  (format "%smm" n))

;; 3:4 perfect fourth scale (http://www.modularscale.com/?11&pt&1.333&web&table)
(def ^:private default-stylesheet
  {:font {:font-family "Verdana" :font-size "11pt"}
   :block {:text-align "start" :hyphenate "false"}
   :h1 {:font-size "26.05pt" :font-weight "bold" :space-after "26pt" :keep-with-next "always" :break-before "page" :role "H1" :line-height "110%"}
   :h2 {:font-size "19.55pt" :font-weight "bold" :space-after "11pt" :space-before "25pt" :keep-with-next "always" :role "H2" :line-height "110%"}
   :h3 {:font-size "14.66pt" :font-weight "bold" :space-after "14pt" :space-before "14pt" :keep-with-next "always" :role "H3" :line-height "110%"}
   :header {:text-align-last "justify" :font-weight "bold" :border-bottom "thin solid"}})

;; 3:4 perfect fourth scale (http://www.modularscale.com/?17&pt&1.333&web&table)
(def ^:private large-print-stylesheet
  {:font {:font-family "Tiresias" :font-size "17pt"}
   :block {:text-align "start" :hyphenate "false"}
   :h1 {:font-size "40.3pt" :font-weight "bold" :keep-with-next "always" :space-after "30pt" :role "H1" :line-height "110%" :break-before "odd-page" }
   :h2 {:font-size "30.2pt" :font-weight "bold" :keep-with-next "always" :space-after "30pt" :role "H2" :line-height "110%"}
   :h3 {:font-size "22.6pt" :font-weight "bold" :keep-with-next "always" :space-after "30pt" :role "H3" :line-height "110%"}
   :header {:text-align-last "justify" :font-weight "bold" :border-bottom "thin solid"}})

(def ^:private ^:dynamic *stylesheet* default-stylesheet)

(defn style
  ([class]
   (style class {}))
  ([class attrs]
   (merge (*stylesheet* class {}) attrs)))

(defn- section-numbers [numbers]
  (when (not-empty numbers)
    (str (string/join "." numbers) ". ")))

(defn heading
  ([level path]
   (heading level path []))
  ([level path path-to-numbers]
   [:fo:block (style level
                     {:id (hash path)})
    (when-let [numbers (get path-to-numbers path)]
      (section-numbers numbers))
    (let [title (last path)]
      (if (keyword? title) (layout/translations title) title))]))

(def ^:private running-header-class-name "running-header")

(defn- retrieve-marker []
  [:fo:inline
   [:fo:retrieve-marker {:retrieve-class-name running-header-class-name
                         :retrieve-position "last-ending-within-page"
                         :retrieve-boundary "page-sequence"}]])

(defn- set-marker [title]
  [:fo:block {:keep-with-next "always"}
   [:fo:marker {:marker-class-name running-header-class-name} title]])

(defn- external-link [url title]
  [:fo:basic-link
   {:external-destination url :indicate-destination true}
   title])

(defn- to-url
  "Return an url given a `record-id`"
  [record-id]
  (let [api-key "c97386a2-914a-40c2-bd8d-df4c273175e6"]
    (format "http://online.sbs.ch/iguana/www.main.cls?v=%s&amp;sUrl=search%%23RecordId=%s"
            api-key
            (string/replace record-id "/" "."))))

(defn- link-to-online-catalog [record-id title]
  (external-link (to-url record-id) title))

(defn- block [& args]
  (if (map? (first args))
    [:fo:block (style :block (first args)) (rest args)]
    [:fo:block (style :block) args]))

(defn- inline [& args]
  (let [default {}]
    (if (map? (first args))
      [:fo:inline (merge default (first args)) (rest args)]
      [:fo:inline default args])))

(defn- list-item [& args]
  [:fo:list-item {:space-after "1em"}
   [:fo:list-item-label [:fo:block]]
   [:fo:list-item-body
    args]])

(defn- bold [& args]
  (inline {:font-weight "bold"} args))

(defn- bullet-list [& body]
  [:fo:list-item
   [:fo:list-item-label
    {:end-indent "label-end()"}
    [:fo:block (inline {:font-family "Symbol"} "•")]]
   [:fo:list-item-body
    {:start-indent "body-start()"}
    (block body)]])

(defmulti to-fop (fn [{:keys [tag attrs content]} _ _] tag))
(defmethod to-fop :p [{content :content} _ _] (block {:space-before "17pt"} content))
(defmethod to-fop :a [{content :content {href :href} :attrs} _ _]
  (if (not-empty content)
    (apply #(external-link href %) content)
    (external-link href href)))
(defmethod to-fop :h1 [{content :content} path path-to-numbers]
  (heading :h1 (conj path (string/join content)) path-to-numbers))
(defmethod to-fop :h2 [{content :content} path path-to-numbers]
  (heading :h2 (conj path (string/join content)) path-to-numbers))
(defmethod to-fop :h3 [{content :content} path path-to-numbers]
  (heading :h3 (conj path (string/join content)) path-to-numbers))
(defmethod to-fop :ul [{content :content} _ _] [:fo:list-block content])
(defmethod to-fop :li [{content :content} _ _] (bullet-list content))
(defmethod to-fop :em [{content :content} _ _] (inline {:font-style "italic"} content))
(defmethod to-fop :strong [{content :content} _ _] (inline {:font-weight "bold"} content))
(defmethod to-fop :br [_ _ _] [:fo:block ])
(defmethod to-fop :default [{content :content} _ _] (apply block content)) ; just assume :p

(defn- node? [node]
  (and (map? node) (or (set/subset? #{:tag :content} (set (keys node))) (= (:tag node) :br))))

(defn- visitor [node path path-to-numbers]
  (if (node? node)
    (to-fop node path path-to-numbers)
    node))

(defn md-to-fop [markdown path path-to-numbers]
  (->>
   markdown
   endophile/mp
   endophile/to-clj
   (walk/postwalk #(visitor % path path-to-numbers))))

(defn- md-extract-headings [markdown]
  (->>
   markdown
   endophile/mp
   endophile/to-clj
   (filter #(#{:h1 :h2 :h3 :h4} (:tag %)))
   (map (comp string/join :content))))

(defn- toc-entry
  ([items path path-to-numbers depth]
   (toc-entry items path path-to-numbers depth 1))
  ([items path path-to-numbers depth current-depth]
   [:fo:block {:text-align-last "justify" :role "TOCI"
               :start-indent (str (- current-depth 1) "em")}
    [:fo:basic-link {:internal-destination (hash path)}
     (inline {:role "Reference"}
             (when-let [numbers (get path-to-numbers path)] (section-numbers numbers))
             (let [title (last path)]
               (if (keyword? title) (layout/translations title) title)))
     " " [:fo:leader {:leader-pattern "dots" :role "NonStruct"}] " "
     [:fo:page-number-citation {:ref-id (hash path) :role "Reference"}]]
    (cond
      (and (map? items) (< current-depth depth))
      (map #(toc-entry (get items %) (conj path %) path-to-numbers depth (inc current-depth))
           (keys items))
      ;; check for headings in markdown
      (and (string? items)
           ;; only show in the toc if there are multiple headings (I
           ;; guess this is a weird way of saying we don't want any
           ;; subheadings in the toc for :grossdruck)
           (< 1 (count (md-extract-headings items))))
      (map #(toc-entry % (conj path %) path-to-numbers depth (inc current-depth))
           (md-extract-headings items)))]))

(defn toc [items path depth path-to-numbers & {:keys [heading?]}]
  (when (map? items)
    [:fo:block {:line-height "150%"
                :role "TOC"}
     (when heading? (heading :h1 [:inhalt]))
     (map #(toc-entry (get items %) (conj path %) path-to-numbers depth) (keys items))]))

(defn- narrators-sexp [narrators]
  (let [narrator (first narrators)]
    (if (> (count narrators) 1)
      (wrap narrator "gelesen von: " " u.a. " false)
      (wrap narrator "gelesen von: "))))

(defmulti entry-heading-sexp (fn [{fmt :format}] fmt))
(defmethod entry-heading-sexp :ludo
  [{:keys [creator record-id title subtitles]}]
  (block {:keep-with-next "always"}
         (bold (link-to-online-catalog record-id (layout/periodify title)))
         (when subtitles " ")
         (layout/render-subtitles subtitles)
         (wrap creator " " "" false)))

(defmethod entry-heading-sexp :default
  [{:keys [creator record-id title subtitles name-of-part source-publisher source-date]}]
  (block {:keep-with-next "always"}
         (bold (wrap creator "" ": " false)
               (link-to-online-catalog record-id (layout/periodify title)))
         (when subtitles " ")
         (layout/render-subtitles subtitles)
         (wrap name-of-part " ")
         (when (or source-publisher source-date) " - ")
         (wrap source-publisher "" (if source-date ", " "") false)
         (wrap (layout/year source-date))))

(defn- ausleihe [signature]
  (when signature
    (block {:keep-with-previous "always"} (bold "Ausleihe:") " " signature)))

(defn- ausleihe-multi [signatures]
  (when signatures
    (block {:keep-with-previous "always"}
           (bold "Ausleihe:") " " (layout/braille-signatures signatures))))

(defn- verkauf [product-number price]
  (when product-number
    (block {:keep-with-previous "always"}
           (bold "Verkauf:") " " price ". " (layout/braille-signatures product-number))))

(defmulti entry-sexp (fn [{fmt :format}] fmt))

(defmethod entry-sexp :hörbuch
  [{:keys [genre-text description duration narrators producer-brief
           produced-commercially? library-signature product-number price] :as item}]
  (list-item
   (entry-heading-sexp item)
   (block (wrap genre-text "Genre: "))
   (block (wrap description))
   (block (wrap duration "" " Min., " false) (narrators-sexp narrators))
   (block producer-brief (if produced-commercially? ", Hörbuch aus dem Handel" "") ".")
   (ausleihe library-signature)
   (when product-number
     (block {:keep-with-previous "always"} (bold "Verkauf:") " " product-number ", " price))))

(defmethod entry-sexp :braille
  [{:keys [genre-text description producer-brief rucksackbuch? rucksackbuch-number
           library-signature product-number price] :as item}]
  (list-item
   (entry-heading-sexp item)
   (block (wrap genre-text "Genre: "))
   (block (wrap description))
   (block (wrap producer-brief "" (if rucksackbuch?
                                    (str ", Rucksackbuch Nr. " rucksackbuch-number)
                                    "")))
   (ausleihe-multi library-signature)
   (verkauf product-number price)))

(defmethod entry-sexp :grossdruck
  [{:keys [genre-text description library-signature volumes product-number price] :as item}]
  (list-item
   (entry-heading-sexp item)
   (block (wrap genre-text "Genre: "))
   (block (wrap description))
   (when library-signature
     (block {:keep-with-previous "always"}
            (bold "Ausleihe:") " " library-signature (wrap volumes ", " " Bd. " false)))
   (when product-number
     (block {:keep-with-previous "always"} (bold "Verkauf:") " " price))))

(defmethod entry-sexp :e-book
  [{:keys [genre-text description library-signature] :as item}]
  (list-item
   (entry-heading-sexp item)
   (block (wrap genre-text "Genre: "))
   (block (wrap description))
   (ausleihe library-signature)))

(defmethod entry-sexp :hörfilm
  [{:keys [directed-by actors movie_country genre-text
           description producer library-signature] :as item}]
  (list-item
   (entry-heading-sexp item)
   (block (wrap directed-by "Regie: " "") (wrap actors " Schauspieler: " ""))
   (block (wrap movie_country))
   (block (wrap genre-text))
   (block (wrap description))
   (block (wrap producer))
   (ausleihe library-signature)))

(defmethod entry-sexp :ludo
  [{:keys [source-publisher genre-text description
           game-description accompanying-material library-signature] :as item}]
  (list-item
   (entry-heading-sexp item)
   (block (wrap source-publisher))
   (block (wrap genre-text))
   (block (wrap description))
   (block (wrap game-description))
   (ausleihe library-signature)))

(defmethod entry-sexp :musiknoten
  [{:keys [description producer-brief library-signature product-number price] :as item}]
  (list-item
   (entry-heading-sexp item)
   (block (wrap description))
   (block (wrap producer-brief))
   (ausleihe-multi library-signature)
   (verkauf product-number price)))

(defmethod entry-sexp :taktilesbuch
  [{:keys [description producer-brief library-signature product-number price] :as item}]
  (list-item
   (entry-heading-sexp item)
   (block (wrap description))
   (block (wrap producer-brief))
   (ausleihe-multi library-signature)
   (verkauf product-number price)))

(defn entries-sexp [items]
  [:fo:list-block
   (for [item items] (entry-sexp item))])

(defn- level-to-h [level]
  (keyword (str "h" level)))

(defn subgenre-sexp [items fmt genre subgenre level path-to-numbers]
  [(heading (level-to-h level) [fmt genre subgenre] path-to-numbers)
   (when-not (#{:kinder-und-jugendbücher} genre)
     (set-marker (layout/translations subgenre)))
   (entries-sexp items)])

(defn subgenres-sexp [items fmt genre level path-to-numbers]
    (mapcat #(subgenre-sexp (get items %) fmt genre % level path-to-numbers) (keys items)))

(defn genre-sexp
  ([items fmt genre level]
   (genre-sexp items fmt genre level nil))
  ([items fmt genre level path-to-numbers]
   [(heading (level-to-h level) [fmt genre] path-to-numbers)
    (set-marker (layout/translations genre))
    (cond
      ;; handle the special case where the editorial or the recommendations are passed in the tree
      (#{:editorial :recommendations :recommendation} genre) (md-to-fop items
                                                                        [fmt genre] path-to-numbers)
      (#{:kinder-und-jugendbücher} genre) (subgenres-sexp items fmt genre (inc level) path-to-numbers)
      (#{:hörbuch} fmt) (subgenres-sexp items fmt genre (inc level) path-to-numbers)
      :else (entries-sexp items))]))

(defn format-sexp
  ([items fmt level]
   (format-sexp items fmt level nil true))
  ([items fmt level path-to-numbers with-toc?]
   [(heading (level-to-h level) [fmt] path-to-numbers)
    (when with-toc?
      (toc items [fmt] 2 nil))
    (set-marker (layout/translations fmt))
    (case fmt
      ;; handle the special case where the editorial or the recommendations are passed in the tree
      (:editorial :recommendations :recommendation) (md-to-fop items [fmt] path-to-numbers)
      (:hörfilm :ludo) (entries-sexp items)
      (mapcat #(genre-sexp (get items %) fmt % (inc level) path-to-numbers) (keys items)))]))

(defn- page-number []
  [:fo:inline
   [:fo:page-number]])

(defn- header [side]
  (let [recto? (= side :recto)]
    [:fo:static-content {:flow-name (if recto? "xsl-region-before-recto" "xsl-region-before-verso")
                         :role "artifact"}
     [:fo:block (style :header)
      (if recto? (retrieve-marker) (page-number))
      [:fo:leader]
      (if recto? (page-number) (retrieve-marker))]]))

(defn- simple-page-master
  [{:keys [master-name page-height page-width
           margin-bottom margin-top
           margin-left margin-right]
    :or {page-height "297mm" page-width "210mm"
         margin-bottom "20mm" margin-top "20mm"
         margin-left "25mm" margin-right "25mm"}}]
  [:fo:simple-page-master {:master-name master-name
                           :page-height page-height
                           :page-width page-width
                           :margin-bottom margin-bottom
                           :margin-top margin-top
                           :margin-left margin-left
                           :margin-right margin-right}
   [:fo:region-body {:margin-top (to-mm region-body-margin-top)}]
   [:fo:region-before {:region-name (format "xsl-region-before-%s" master-name)
                       ;; extent should be smaller than margin-top of region-body
                       :extent (to-mm (dec region-body-margin-top))}]])

(defn- layout-master-set []
  [:fo:layout-master-set
   (simple-page-master {:master-name "recto" :margin-left "28mm" :margin-right "22mm"})
   (simple-page-master {:master-name "verso"})
   (simple-page-master {:master-name "blank"})
   (simple-page-master {:master-name "first"})
   [:fo:page-sequence-master {:master-name "main"}
    [:fo:repeatable-page-master-alternatives
     [:fo:conditional-page-master-reference
      {:master-reference "first" :page-position "first"}]
     [:fo:conditional-page-master-reference
      {:master-reference "verso" :odd-or-even "even" :blank-or-not-blank "not-blank"}]
     [:fo:conditional-page-master-reference
      {:master-reference "recto" :odd-or-even "odd" :blank-or-not-blank "not-blank"}]
     [:fo:conditional-page-master-reference
      {:master-reference "blank" :blank-or-not-blank "blank"}]]]])

(defn- declarations
  [title description]
  [:fo:declarations
    [:x:xmpmeta {:xmlns:x "adobe:ns:meta/"}
     [:rdf:RDF {:xmlns:rdf"http://www.w3.org/1999/02/22-rdf-syntax-ns#"}
      [:rdf:Description {:rdf:about ""
                         :xmlns:dc "http://purl.org/dc/elements/1.1/"}
       ;; Dublin Core properties
       [:dc:title title]
       [:dc:creator (layout/translations :sbs)]
       [:dc:description description]]
      [:rdf:Description {:rdf:about ""
                         :xmlns:xmp "http://ns.adobe.com/xap/1.0/"}
       ;; XMP properties
       [:xmp:CreatorTool "Apache FOP"]]]]])

(defn- branch? [node]
  (map? node))

(defn- mapcat-indexed [f coll]
  (apply concat (map-indexed f coll)))

(defn- tree-walk
  "Walk a tree and build a list of path and corresponding numbering
  pairs."
  [node path numbers]
  (cond
    (branch? node)
    (concat
     [path numbers]
     ;; walk the tree of catalog items
     (mapcat-indexed
      (fn [n k] (tree-walk (get node k) (conj path k) (conj numbers (inc n))))
      (keys node)))
    (and (string? node) (not-empty (md-extract-headings node)))
    ;; walk the tree with markdown
    (concat
     [path numbers]
     (mapcat-indexed
      (fn [n heading] (tree-walk heading (conj path heading) (conj numbers (inc n))))
      (md-extract-headings node)))
    :else [path numbers]))

(defn- path-to-number
  "Return a map of path to number vector mapping. This can be used to
  look up a section number for a given path."
  [tree]
  (apply hash-map (tree-walk tree [] [])))

(defmulti document-sexp (fn [items fmt editorial recommendations options] fmt))

(defmethod document-sexp :grossdruck
  [items fmt editorial recommendations {:keys [description date]}]
  (binding [*stylesheet* large-print-stylesheet]
    [:fo:root (style :font
                     {:xmlns:fo "http://www.w3.org/1999/XSL/Format"
                      :line-height "130%"
                      :xml:lang "de"})
     (layout-master-set)
     (declarations (layout/translations :grossdruck) description)
     [:fo:page-sequence {:master-reference "main"
                         :initial-page-number "1"
                         :force-page-count "end-on-even"
                         :language "de"}
      (header :recto)
      (header :verso)

      (let [subitems (-> items
                         (get fmt)
                         (assoc :editorial editorial
                                :recommendation recommendations))]
        [:fo:flow {:flow-name "xsl-region-body"}
         (toc subitems [fmt] 2 nil :heading? true)
         ;; FIXME: there is a subtle bug to do with bindings and lazy
         ;; sequences. Since we return a lazy seq here the mapcat
         ;; might be realized outside the binding scope and might get
         ;; the wrong *stylesheet*. So as a workaround we make sure
         ;; the sequence is fully realized with `doall` (see also
         ;; http://cemerick.com/2009/11/03/be-mindful-of-clojures-binding/).
         ;; Maybe the binding solution isn't the bees knees after all.
         (doall
          (mapcat #(genre-sexp (get subitems %) fmt % 1) (keys subitems)))])]]))

(defmethod document-sexp :hörbuch
  [items fmt editorial recommendations {:keys [description date]}]
  [:fo:root (style :font
                   {:xmlns:fo "http://www.w3.org/1999/XSL/Format"
                    :line-height "130%"
                    :xml:lang "de"})
   (layout-master-set)
   (declarations (layout/translations :hörbuch) description)
   [:fo:page-sequence {:master-reference "main"
                       :initial-page-number "1"
                       :language "de"}
    (header :recto)
    (header :verso)

    (let [subitems (-> items
                       ;; remove all formats but fmt. Unfortunately we
                       ;; cannot use select-keys as we need to retain
                       ;; the sorted map.
                       (#(apply dissoc % (remove #{fmt} (keys %))))
                       (assoc :editorial editorial
                              :recommendations recommendations))
          path-to-numbers (path-to-number subitems)]
      [:fo:flow {:flow-name "xsl-region-body"}
       ;; Cover page
       [:fo:block (style :h1) (format "Neu als Hörbuch Nr. %s" date)]
       (block {:break-before "odd-page"}) ;; toc should start on recto
       (toc subitems [] 3 path-to-numbers :heading? true)
       (block {:break-before "odd-page"}) ;; the very first format should start on recto
       (mapcat #(format-sexp (get subitems %) % 1 path-to-numbers false) (keys subitems))])]])

(defn- logo []
  [:fo:block {:space-before "100mm" :text-align "right" :end-indent "20mm"}
   [:fo:external-graphic
    {:src (io/resource "images/sbs_logo.jpg")
     :height "35mm"
     :scaling "uniform"
     :content-width "scale-to-fit"
     :content-height "scale-to-fit"}]])

(defn- impressum []
  (let [creator "SBS Schweizerische Bibliothek für Blinde, Seh- und Lesebehinderte"]
    [:fo:block-container {:space-before "10mm"}
     (block "Herausgeber:")
     (block creator)
     (block "Grubenstrasse 12")
     (block "CH-8045 Zürich")
     (block "Fon +41 43 333 32 32")
     (block "Fax +41 43 333 32 33")
     (block (external-link "http://www.sbs.ch" "www.sbs.ch"))
     (block (external-link "mailto:nutzerservice@sbs.ch" "nutzerservice@sbs.ch"))
     (block {:space-before "5mm"} (format "© %s" creator))]))

(defn- cover-page [titles date]
  [:fo:block-container
   (map #(block (style :h1 {:break-before "auto" :space-after "5pt"}) %) titles)
   (block (style :h2 {:break-before "auto" :space-after "5pt"})
          (format "Gesamtkatalog, Stand 1.1.%s" (layout/year date)))
   (logo)
   (impressum)])

(defmethod document-sexp :hörfilm
  [items fmt _ _ {:keys [description date]
                  :or {date (time.core/today)}}]
  [:fo:root (style :font
                   {:xmlns:fo "http://www.w3.org/1999/XSL/Format"
                    :line-height "130%"
                    :xml:lang "de"})
   (layout-master-set)
   (declarations (layout/translations fmt) description)
   [:fo:page-sequence {:master-reference "main"
                       :initial-page-number "1"
                       :language "de"}
    (header :recto)
    (header :verso)

    (let [subitems (get items fmt)]
      [:fo:flow {:flow-name "xsl-region-body"}
       (cover-page ["Hörfilme in der SBS"
                    "Filme mit Audiodeskription"]
                   date)
       (block {:break-before "odd-page"}) ;; toc should start on recto
       (toc subitems [fmt] 1 nil :heading? true)
       (block {:break-before "odd-page"}) ;; the very first format should start on recto
       (mapcat #(genre-sexp (get subitems %) fmt % 1) (keys subitems))])]])

(defmethod document-sexp :ludo
  [items fmt _ _ {:keys [description date]
                  :or {date (time.core/today)}}]
  [:fo:root (style :font
                   {:xmlns:fo "http://www.w3.org/1999/XSL/Format"
                    :line-height "130%"
                    :xml:lang "de"})
   (layout-master-set)
   (declarations (layout/translations fmt) description)
   [:fo:page-sequence {:master-reference "main"
                       :initial-page-number "1"
                       :language "de"}
    (header :recto)
    (header :verso)

    (let [subitems (get items fmt)]
      [:fo:flow {:flow-name "xsl-region-body"}
       (cover-page ["Spiele in der SBS"]
                   date)
       (block {:break-before "odd-page"}) ;; toc should start on recto
       (toc subitems [fmt] 1 nil :heading? true)
       (block {:break-before "odd-page"}) ;; the very first format should start on recto
       (mapcat #(genre-sexp (get subitems %) fmt % 1) (keys subitems))])]])

(defmethod document-sexp :taktilesbuch
  [items fmt _ _ {:keys [description date]
                  :or {date (time.core/today)}}]
  [:fo:root (style :font
                   {:xmlns:fo "http://www.w3.org/1999/XSL/Format"
                    :line-height "130%"
                    :xml:lang "de"})
   (layout-master-set)
   (declarations (layout/translations fmt) description)
   [:fo:page-sequence {:master-reference "main"
                       :initial-page-number "1"
                       :language "de"}
    (header :recto)
    (header :verso)

    (let [subitems (get items fmt)]
      [:fo:flow {:flow-name "xsl-region-body"}
       (cover-page ["Taktile Kinderbücher in der SBS"]
                   date)
       (block {:break-before "odd-page"}) ;; toc should start on recto
       (toc subitems [fmt] 1 nil :heading? true)
       (block {:break-before "odd-page"}) ;; the very first format should start on recto
       (mapcat #(genre-sexp (get subitems %) fmt % 1) (keys subitems))])]])

(defmethod document-sexp :all-formats
  [items _ _ _ {:keys [description date]}]
  [:fo:root (style :font
                   {:xmlns:fo "http://www.w3.org/1999/XSL/Format"
                    :line-height "130%"
                    :xml:lang "de"})
   (layout-master-set)
   (declarations (layout/translations :all-formats) description)
   [:fo:page-sequence {:master-reference "main"
                       :initial-page-number "1"
                       :language "de"}
    (header :recto)
    (header :verso)

    [:fo:flow {:flow-name "xsl-region-body"}
     (toc items [] 1 nil :heading? true)
     (block {:break-before "odd-page"}) ;; the very first format should start on recto
     (mapcat #(format-sexp (get items %) % 1) (keys items))]]])

(defn document [items fmt editorial recommendations & args]
  (-> items
      (document-sexp fmt editorial recommendations args)
      xml/sexp-as-element))

(defn generate-document [items fmt editorial recommendations out]
  (with-open [out (io/writer out)]
    (-> items
        (document fmt editorial recommendations)
        (xml/indent out))))

(defn generate-pdf! [document out]
  ;; generate PDF according to https://xmlgraphics.apache.org/fop/2.0/embedding.html#basics
  (with-open [out (io/output-stream out)]
    (let [;; Construct a FopFactory by specifying a reference to the configuration file
          factory (FopFactory/newInstance (io/file (io/resource "fop.xconf")))
          ;; Construct fop with desired output format
          fop (.newFop factory MimeConstants/MIME_PDF out)
          ;; Setup JAXP using identity transformer
          transformer (.newTransformer (TransformerFactory/newInstance))
          ;; Setup input and output for XSLT transformation
          source (StreamSource. (StringReader. (xml/emit-str document)))
          ;; Resulting SAX events (the generated FO) must be piped through to FOP
          saxresult (SAXResult. (.getDefaultHandler fop))]
      ;; Start XSLT transformation and FOP processing
      (.transform transformer source saxresult))))
