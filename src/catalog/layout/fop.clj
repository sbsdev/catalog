(ns catalog.layout.fop
  (:require [catalog.layout.common :as layout :refer [wrap]]
            [clojure
             [set :as set]
             [string :as string]
             [walk :as walk]]
            [clojure.data.xml :as xml]
            [clojure.java.io :as io]
            [endophile.core :as endophile])
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
   :h1 {:font-size "26.05pt" :font-weight "bold" :space-after "26pt" :keep-with-next "always" :break-before "page" :role "H1"}
   :h2 {:font-size "19.55pt" :font-weight "bold" :space-after "11pt" :space-before "25pt" :keep-with-next "always" :role "H2"}
   :h3 {:font-size "14.66pt" :font-weight "bold" :space-after "14pt" :space-before "14pt" :keep-with-next "always" :role "H3"}
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

(defn heading [level title path]
  [:fo:block (style level
                    {:id (hash path)})
   (layout/translations title)])

(def ^:private running-header-class-name "running-header")

(defn- retrieve-marker []
  [:fo:inline
   [:fo:retrieve-marker {:retrieve-class-name running-header-class-name
                         :retrieve-position "last-ending-within-page"
                         :retrieve-boundary "page-sequence"}]])

(defn- set-marker [title]
  [:fo:block {:keep-with-next "always"}
   [:fo:marker {:marker-class-name running-header-class-name} title]])

(defn- order
  "Return `items` ordered. Checks first if `items` are either a subset
  of `layout/formats`, `layout/braille-genres` or `layout/subgenres`
  and consequently orders accordingly. Return nil if `items` are not a
  subset of either."
  [items]
  (let [items (set (remove nil? items))]
    (cond
      (set/subset? items (set layout/formats))
      (keep items layout/formats)
      (set/subset? items (set layout/braille-genres))
      (keep items layout/braille-genres)
      (set/subset? items (set layout/subgenres))
      (keep items layout/subgenres))))

(defn- toc-entry [items path to-depth]
  (let [depth (count path)]
    [:fo:block (-> {:text-align-last "justify" :role "TOCI"}
                   (cond-> (<= depth 2) (assoc :start-indent "1em")))
     [:fo:basic-link {:internal-destination (hash path)}
      (inline {:role "Reference"} (layout/translations (last path)))
      " " [:fo:leader {:leader-pattern "dots" :role "NonStruct"}] " "
      [:fo:page-number-citation {:ref-id (hash path) :role "Reference"}]]
     (when (and (map? items) (< depth to-depth))
       (keep #(toc-entry (% items) (conj path %) to-depth) (order (keys items))))]))

(defn toc [items path to-depth & {:keys [heading? editorial? recommendation-at-end? single-recommendation?]}]
  (when (map? items)
    [:fo:block {:line-height "150%"
                :role "TOC"}
     (when heading? (heading :h1 :inhalt []))
     (let [entries (order (keys items))
           entries (cond
                     recommendation-at-end? (concat [:editorial] entries [(if single-recommendation? :recommendation :recommendations)])
                     editorial? (concat [:editorial (if single-recommendation? :recommendation :recommendations)] entries)
                     :else entries)]
       (keep #(toc-entry (get items %) (conj path %) to-depth) entries))]))

(defn- to-url
  "Return an url given a `record-id`"
  [record-id]
  (let [api-key "c97386a2-914a-40c2-bd8d-df4c273175e6"]
    (format "http://online.sbs.ch/iguana/www.main.cls?v=%s&amp;sUrl=search%%23RecordId=%s"
            api-key
            (string/replace record-id "/" "."))))

(defn- external-link [url title]
  [:fo:basic-link
   {:external-destination url :indicate-destination true}
   title])

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

(defn- narrators-sexp [narrators]
  (let [narrator (first narrators)]
    (if (> (count narrators) 1)
      (wrap narrator "gelesen von: " " u.a. " false)
      (wrap narrator "gelesen von: "))))

(defn- entry-heading-sexp
  [creator record-id title subtitles name-of-part source-publisher source-date]
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
           (bold "Verkauf:") " " price ", " (layout/braille-signatures product-number))))

(defmulti entry-sexp (fn [{fmt :format}] fmt))

(defmethod entry-sexp :hörbuch
  [{:keys [creator record-id title subtitles name-of-part source-publisher
           source-date genre-text description duration narrators producer-brief
           produced-commercially? library-signature product-number price]}]
  (list-item
   (entry-heading-sexp creator record-id title subtitles name-of-part source-publisher source-date)
   (block (wrap genre-text "Genre: "))
   (block (wrap description))
   (block (wrap duration "" " Min., " false) (narrators-sexp narrators))
   (block producer-brief (if produced-commercially? ", Hörbuch aus dem Handel" "") ".")
   (ausleihe library-signature)
   (when product-number
     (block {:keep-with-previous "always"} (bold "Verkauf:") " " product-number ", " price))))

(defmethod entry-sexp :braille
  [{:keys [creator record-id title subtitles name-of-part source-publisher source-date
           genre-text description producer-brief rucksackbuch? rucksackbuch-number
           library-signature product-number price]}]
  (list-item
   (entry-heading-sexp creator record-id title subtitles name-of-part source-publisher source-date)
   (block (wrap genre-text "Genre: "))
   (block (wrap description))
   (block producer-brief (if rucksackbuch? (str ", Rucksackbuch Nr. " rucksackbuch-number) "") ".")
   (ausleihe-multi library-signature)
   (verkauf product-number price)))

(defmethod entry-sexp :grossdruck
  [{:keys [creator record-id title subtitles name-of-part source-publisher source-date
           genre-text description library-signature volumes product-number price]}]
  (list-item
   (entry-heading-sexp creator record-id title subtitles name-of-part source-publisher source-date)
   (block (wrap genre-text "Genre: "))
   (block (wrap description))
   (when library-signature
     (block {:keep-with-previous "always"}
            (bold "Ausleihe:") " " library-signature (wrap volumes ", " " Bd. " false)))
   (when product-number
     (block {:keep-with-previous "always"} (bold "Verkauf:") " " price))))

(defmethod entry-sexp :e-book
  [{:keys [creator record-id title subtitles name-of-part source-publisher source-date
           genre-text description library-signature]}]
  (list-item
   (entry-heading-sexp creator record-id title subtitles name-of-part source-publisher source-date)
   (block (wrap genre-text "Genre: "))
   (block (wrap description))
   (ausleihe library-signature)))

(defmethod entry-sexp :hörfilm
  [{:keys [record-id title subtitles directed-by actors movie_country genre-text
           description producer library-signature]}]
  (list-item
   (block {:keep-with-next "always"}
     (bold (link-to-online-catalog record-id (layout/periodify title)))
     (layout/render-subtitles subtitles))
   (block (wrap directed-by "Regie: " "") (wrap actors " Schauspieler: " ""))
   (block (wrap movie_country))
   (block (wrap genre-text))
   (block (wrap description))
   (block (wrap producer))
   (ausleihe library-signature)))

(defmethod entry-sexp :ludo
  [{:keys [record-id title subtitles creator source-publisher genre-text description
           game-description accompanying-material library-signature]}]
  (list-item
   (block {:keep-with-next "always"}
     (bold (link-to-online-catalog record-id (layout/periodify title)))
     (layout/render-subtitles subtitles)
     (layout/wrap creator " "))
   (block (wrap source-publisher))
   (block (wrap genre-text))
   (block (wrap description))
   (block (wrap game-description))
   (ausleihe library-signature)))

(defmethod entry-sexp :musiknoten
  [{:keys [creator record-id title subtitles name-of-part source-publisher source-date
           description producer-brief library-signature product-number price]}]
  (list-item
   (entry-heading-sexp creator record-id title subtitles name-of-part source-publisher source-date)
   (block (wrap description))
   (block (wrap producer-brief))
   (ausleihe-multi library-signature)
   (verkauf product-number price)))

(defmethod entry-sexp :taktilesbuch
  [{:keys [creator record-id title subtitles name-of-part source-publisher source-date
           description producer-brief library-signature product-number price]}]
  (list-item
   (entry-heading-sexp creator record-id title subtitles name-of-part source-publisher source-date)
   (block (wrap description))
   (block (wrap producer-brief))
   (ausleihe-multi library-signature)
   (verkauf product-number price)))

(defn entries-sexp [items]
  [:fo:list-block
   (for [item items] (entry-sexp item))])

(defn- level-to-h [level]
  (keyword (str "h" level)))

(defn subgenre-sexp [items fmt genre subgenre level]
  (when-let [items (subgenre items)]
    [(heading (level-to-h level) subgenre [fmt genre subgenre])
     (when-not (#{:kinder-und-jugendbücher} genre)
       (set-marker (layout/translations subgenre)))
     (entries-sexp items)]))

(defn subgenres-sexp [items fmt genre level]
  (mapcat #(subgenre-sexp items fmt genre % level) (order (keys items))))

(defn genre-sexp [items fmt genre level]
  (when-let [items (genre items)]
    [(heading (level-to-h level) genre [fmt genre])
     (set-marker (layout/translations genre))
     (cond
       (#{:kinder-und-jugendbücher} genre) (subgenres-sexp items fmt genre (inc level))
       (#{:hörbuch} fmt) (subgenres-sexp items fmt genre (inc level))
       :else (entries-sexp items))]))

(defn format-sexp [items fmt level]
  (when-let [items (fmt items)]
    [(heading (level-to-h level) fmt [fmt])
     (toc items [fmt] 3)
     (set-marker (layout/translations fmt))
     (case fmt
       (:hörfilm :ludo) (entries-sexp items)
       (mapcat #(genre-sexp items fmt % (inc level)) (order (keys items))))]))

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
   (simple-page-master {:master-name "recto"})
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

(defn- bullet-list [& body]
  [:fo:list-item
   [:fo:list-item-label
    {:end-indent "label-end()"}
    [:fo:block (inline {:font-family "Symbol"} "•")]]
   [:fo:list-item-body
    {:start-indent "body-start()"}
    (block body)]])

(defmulti to-fop (fn [{:keys [tag attrs content]}] tag))
(defmethod to-fop :p [{content :content}] (block {:space-before "17pt"} content))
(defmethod to-fop :a [{content :content {href :href} :attrs}] (apply #(external-link href %) content))
(defmethod to-fop :h1 [{content :content}] [:fo:block (style :h1) content])
(defmethod to-fop :h2 [{content :content}] [:fo:block (style :h2) content])
(defmethod to-fop :h3 [{content :content}] [:fo:block (style :h3) content])
(defmethod to-fop :ul [{content :content}] [:fo:list-block content])
(defmethod to-fop :li [{content :content}] (bullet-list content))
(defmethod to-fop :em [{content :content}] (inline {:font-style "italic"} content))
(defmethod to-fop :strong [{content :content}] (inline {:font-weight "bold"} content))
(defmethod to-fop :br [_] [:fo:block ])
(defmethod to-fop :default [{content :content}] (apply block content)) ; just assume :p

(defn- node? [node]
  (and (map? node) (or (set/subset? #{:tag :content} (set (keys node))) (= (:tag node) :br))))

(defn- visitor [node]
  (if (node? node)
    (to-fop node)
    node))

(defn md-to-fop [markdown]
  (->>
   markdown
   endophile/mp
   endophile/to-clj
   (walk/postwalk visitor)))

(defmulti document-sexp (fn [items fmt options] fmt))

(defmethod document-sexp :grossdruck
  [items fmt {:keys [description date]}]
  (binding [*stylesheet* large-print-stylesheet]
    [:fo:root (style :font
                     {:xmlns:fo "http://www.w3.org/1999/XSL/Format"
                      :line-height "130%"
                      :xml:lang "de"})
     (layout-master-set)
     (declarations (layout/translations :grossdruck) description)
     [:fo:page-sequence {:master-reference "main"
                         :initial-page-number "1"
                         :language "de"}
      (header :recto)
      (header :verso)

      (let [subitems (get items fmt)]
        [:fo:flow {:flow-name "xsl-region-body"}
         (toc subitems [fmt] 3 :heading? true :editorial? true :single-recommendation? true)
         (heading :h1 :editorial [fmt :editorial])
         (md-to-fop (slurp "/home/eglic/src/catalog/samples/editorial.md"))
         (heading :h1 :recommendation [fmt :recommendation])
         (md-to-fop (slurp "/home/eglic/src/catalog/samples/recommendations.md"))
         (mapcat #(genre-sexp subitems fmt % 1) (order (keys subitems)))])]]))

(defmethod document-sexp :hörbuch
  [items fmt {:keys [description date]}]
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

    (let [subitems (get items fmt)]
      [:fo:flow {:flow-name "xsl-region-body"}
       (toc subitems [fmt] 3 :heading? true :editorial? true :recommendation-at-end? true)
       (block {:break-before "odd-page"}) ;; the very first format should start on recto
       (heading :h1 :editorial [fmt :editorial])
       (heading :h1 :hörbuch [fmt :hörbuch])
       (mapcat #(genre-sexp subitems fmt % 1) (order (keys subitems)))
       (heading :h1 :recommendations [fmt :recommendations])])]])

(defmethod document-sexp :all-formats
  [items _ {:keys [description date]}]
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
     (toc items [] 1 :heading? true)
     (block {:break-before "odd-page"}) ;; the very first format should start on recto
     (mapcat #(format-sexp items % 1) (order (keys items)))]]])

(defn document [items fmt & args]
  (-> items
      (document-sexp fmt args)
      xml/sexp-as-element))

(defn generate-document [items fmt out]
  (with-open [out (io/writer out)]
    (-> items
        (document fmt)
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
