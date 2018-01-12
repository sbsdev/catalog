(ns catalog.layout.fop
  "Functionality to render a tree of catalog items as accessible PDF"
  (:require [catalog.layout.common :as layout]
            [clj-time
             [core :as time.core]
             [format :as time.format]]
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
           [org.apache.fop.apps FopFactory MimeConstants]
           [org.apache.fop.events EventFormatter EventListener]))

(def ^:private region-body-margin-top 15)

(defn- to-mm [n]
  (format "%smm" n))

;; 3:4 perfect fourth scale (http://www.modularscale.com/?11&pt&1.333&web&table)
(def ^:private default-stylesheet
  {:font {:font-family "Verdana" :font-size "11pt"}
   :block {:text-align "start" :hyphenate "false"}
   :h1 {:font-size "26.05pt" :font-weight "bold" :space-after "26pt" :keep-with-next.within-column "always" :break-before "page" :role "H1" :line-height "110%"}
   :h2 {:font-size "19.55pt" :font-weight "bold" :space-after "11pt" :space-before "25pt" :keep-with-next.within-column "always" :role "H2" :line-height "110%"}
   :h3 {:font-size "14.66pt" :font-weight "bold" :space-after "14pt" :space-before "14pt" :keep-with-next.within-column "always" :role "H3" :line-height "110%"}
   :header {:text-align-last "justify" :font-weight "bold" :border-bottom "thin solid"}})

;; 3:4 perfect fourth scale (http://www.modularscale.com/?17&pt&1.333&web&table)
(def ^:private large-print-stylesheet
  {:font {:font-family "Tiresias,Verdana" :font-size "17pt"}
   :block {:text-align "start" :hyphenate "false"}
   :h1 {:font-size "40.3pt" :font-weight "bold" :keep-with-next.within-column "always" :space-after "0.75em" :role "H1" :break-before "odd-page" :line-height "1.2"}
   :h2 {:font-size "30.2pt" :font-weight "bold" :keep-with-next.within-column "always" :space-after "0.75em" :role "H2" :line-height "1.2"}
   :h3 {:font-size "22.6pt" :font-weight "bold" :keep-with-next.within-column "always" :space-after "0.75em" :role "H3" :line-height "1.2"}
   :header {:text-align-last "justify" :font-weight "bold" :border-bottom "thin solid"}})

(def ^:private ^:dynamic *stylesheet* default-stylesheet)

(defn style
  "Return a map of style attributes for given `class`. The attributes
  are looked up in the `*stylesheet*` dynamic var. Optionally additional
  `attrs` can be defined that will be merged with the attrs of given
  `class`"
  ([class]
   (style class {}))
  ([class attrs]
   (merge (*stylesheet* class {}) attrs)))

(defn heading
  "Return a heading block for given `level` and `path`.

  The last part of `path` is used as the title of the section. If
  `path` is found in `path-to-numbers` then the heading is numbered.
  If the last part of path is a keyword then this is looked up in the
  translations and the actual translation is used as the title
  instead."
  [level path {:keys [path-to-numbers]}]
  [:fo:block (style level {:id (hash path)})
   (when-let [numbers (get path-to-numbers path)]
     (layout/section-numbers numbers))
   (let [title (last path)]
     (if (keyword? title) (layout/translations title) title))])

(def ^:private running-header-class-name "running-header")

(defn- retrieve-marker []
  [:fo:inline
   [:fo:retrieve-marker {:role "artifact"
                         :retrieve-class-name running-header-class-name
                         :retrieve-position "last-ending-within-page"
                         :retrieve-boundary "page-sequence"}]])

(defn- set-marker [title]
  [:fo:block {:keep-with-next.within-column "always" :role "NonStruct"}
   [:fo:marker {:marker-class-name running-header-class-name} title]])

(defn- insert-zero-width-space
  "Add a zero with space after each sequence of white space in `s`.
  This seems to improve the FOP output for screen readers"
  [s]
  (string/replace s #"(\S)\s+(\S)" "$1 ​$2"))

(defn- external-link [url title alt-text]
  [:fo:basic-link
   {:external-destination url :indicate-destination true :fox:alt-text alt-text} title])

(defn- to-url
  "Return an url given a `record-id`"
  [record-id]
  (let [api-key "c97386a2-914a-40c2-bd8d-df4c273175e6"]
    (format "http://online.sbs.ch/iguana/www.main.cls?v=%s&amp;sUrl=search%%23RecordId=%s"
            api-key
            (string/replace record-id "/" "."))))

(defn- link-to-online-catalog [record-id title]
  ;; FIXME: The use of the alt-text isn't quite clear. Apparently when
  ;; using Acrobat Reader with the standard settings the screen reader
  ;; will just read the alt text and not read the title. So we add the
  ;; title to the alt-text.
  (external-link (to-url record-id) (insert-zero-width-space title)
                 (format "%s (Link zum Online-Katalog)" title)))

(defn- block [& args]
  (cond
    (layout/empty-or-blank? args) nil
    (map? (first args)) [:fo:block (style :block (first args)) (rest args)]
    :else [:fo:block (style :block) args]))

(defn- inline [& args]
  (let [default {}]
    (if (map? (first args))
      [:fo:inline (merge default (first args)) (rest args)]
      [:fo:inline default args])))

(defn- bold [& args]
  (inline {:font-weight "bold"} args))

(defn- bullet-list [& body]
  [:fo:list-item
   [:fo:list-item-label
    {:end-indent "label-end()"}
    [:fo:block (inline "•")]]
   [:fo:list-item-body
    {:start-indent "body-start()"}
    (block body)]])

(defmulti to-fop
  "Return a hiccup style sexp containing the fo markup for given
  node at `path` and `opts`."
  (fn [{:keys [tag attrs content]} _ _] tag))

(defmethod to-fop :p [{content :content} _ _] (block {:space-before "17pt"} content))
(defmethod to-fop :a [{content :content {href :href} :attrs} _ _]
  (if (not-empty content)
    (apply #(external-link href % (format "Link zu %s" content)) content)
    (external-link href href (format "Link zu %s" href))))
(defmethod to-fop :h1 [{content :content} path opts]
  (heading :h2 (conj path (string/join content)) opts))
(defmethod to-fop :h2 [{content :content} path opts]
  (heading :h3 (conj path (string/join content)) opts))
(defmethod to-fop :h3 [{content :content} path opts]
  (heading :h4 (conj path (string/join content)) opts))
(defmethod to-fop :ul [{content :content} _ _] [:fo:list-block content])
(defmethod to-fop :li [{content :content} _ _] (bullet-list content))
(defmethod to-fop :em [{content :content} _ _] (inline {:font-style "italic"} content))
(defmethod to-fop :strong [{content :content} _ _] (inline {:font-weight "bold"} content))
(defmethod to-fop :br [_ _ _] [:fo:block ])
(defmethod to-fop :default [{content :content} _ _] (apply block content)) ; just assume :p

(defn- node? [node]
  (and (map? node) (or (set/subset? #{:tag :content} (set (keys node))) (= (:tag node) :br))))

(defn- visitor [node path opts]
  (if (node? node)
    (to-fop node path opts)
    node))

(defn md-to-fop
  "Return a hiccup style sexp for given `markdown` at `path` with
  `opts`.

  The `markdown` is parsed, converted to a clojure data structure and
  finally converted to a hiccup style sexp by the [[to-fop]]
  multimethod."
  [markdown path opts]
  (->>
   markdown
   endophile/mp
   endophile/to-clj
   (walk/postwalk #(visitor % path opts))))

(defn- toc-entry
  [items path depth current-depth {:keys [path-to-numbers] :as opts}]
  [:fo:block {:text-align-last "justify" :role "TOCI"}
   [:fo:basic-link {:internal-destination (hash path)
                    :fox:alt-text (format "Link nach %s" (layout/translations (last path))) }
    (inline {:role "Lbl"}
            (when-let [numbers (get path-to-numbers path)] (layout/section-numbers numbers))
            (let [title (last path)]
              (if (keyword? title) (layout/translations title) title)))
    " " [:fo:leader {:leader-pattern "dots" :role "NonStruct"}] " "
    [:fo:page-number-citation {:ref-id (hash path) :role "Reference"}]]
   (cond
     (and (map? items) (< current-depth depth))
     (block {:role "TOC"
             :start-indent (str current-depth " * 1em")}
      (map #(toc-entry (get items %) (conj path %) depth (inc current-depth) opts)
           (keys items)))
     ;; check for headings in markdown
     (and (string? items)
          ;; only show in the toc if there are multiple headings (I
          ;; guess this is a weird way of saying we don't want any
          ;; subheadings in the toc for :grossdruck)
          (< 1 (count (layout/md-extract-headings items))))
     (block {:role "TOC"
             :start-indent (str current-depth " * 1em")}
      (map #(toc-entry % (conj path %) depth (inc current-depth) opts)
           (layout/md-extract-headings items))))])

(defn toc
  "Return a hiccup style sexp for a fo TOC for given `items`, `path`
  and `depth`.

  Optionally a TOC heading (the actual value is looked up in the
  translation map) can be enabled through `opts`."
  [items path depth {:keys [heading?] :as opts}]
  (when (map? items)
    [:fo:block (if heading?
                 ;; if the toc has a heading we assume that it is the
                 ;; first toc. The following content should start on
                 ;; an odd page
                 {:role "NonStruct" :break-after "odd-page" :break-before "odd-page"}
                 {:role "NonStruct"})
     (when heading? (heading :h1 [:inhalt] opts))
     [:fo:block {:line-height "1.5"
                 :role "TOC"}
      (map #(toc-entry (get items %) (conj path %) depth 1 opts) (keys items))]]))

(def wrap
  "Same as [[layout/wrap]] but insert zero with spaces.
  See [[insert-zero-width-space]]"
  (comp insert-zero-width-space layout/wrap))

(def braille-signatures
  "Same as [[layout/braille-signatures]] but insert zero with spaces.
  See [[insert-zero-width-space]]"
  (comp insert-zero-width-space layout/braille-signatures))

(def render-narrators
  "Same as [[layout/render-narrators]] but insert zero with spaces.
  See [[insert-zero-width-space]]"
  (comp insert-zero-width-space layout/render-narrators))

(defmulti entry-heading-sexp
  "Return a hiccup style sexp for a heading for a given item."
  (fn [{fmt :format}] fmt))

(defmethod entry-heading-sexp :ludo
  [{:keys [creator record-id title subtitles]}]
  (block {:keep-with-next.within-column "always" :role "Lbl"}
         (bold (link-to-online-catalog record-id (layout/periodify title)))
         (when subtitles " ")
         (layout/render-subtitles subtitles)
         (wrap creator " " "" false)))

(defmethod entry-heading-sexp :default
  [{:keys [creator record-id title subtitles name-of-part source-publisher source-date]}]
  (block {:keep-with-next.within-column "always" :role "Lbl"}
         (bold (wrap creator "" ": " false)
               (link-to-online-catalog record-id (layout/periodify title)))
         (when subtitles " ")
         (layout/render-subtitles subtitles)
         (wrap name-of-part " ")
         (when (or source-publisher source-date) " ​- ​")
         (wrap source-publisher "" (if source-date ", ​" "") false)
         (wrap (layout/year source-date))))

(defn- ausleihe [signature]
  (when signature
    (block {:keep-with-previous.within-column "always"} (bold "Ausleihe:") " " signature)))

(defn- ausleihe-multi [signatures]
  (when signatures
    (block {:keep-with-previous.within-column "always"}
           (bold "Ausleihe:") " " (braille-signatures signatures))))

(defn- verkauf [{:keys [product-number price price-on-request?]}]
  (when (or product-number price-on-request?)
    (block {:keep-with-previous.within-column "always"}
           (bold "Verkauf:") " " price ". " (braille-signatures product-number))))

(defn- list-body [& args]
  (block {:role "LBody"} args))

(defn- list-item [heading & args]
  (block {:space-after "1em" :role "LI"}
   heading
   (list-body args)))

(defmulti entry-sexp
  "Return a hiccup style sexp for a given item."
  (fn [{fmt :format} opts] fmt))

(defmethod entry-sexp :hörbuch
  [{:keys [genre-text description duration narrators producer-brief
           produced-commercially? library-signature product-number price price-on-request?] :as item}
   {:keys [show-genre?] :or {show-genre? true}}]
  (list-item
   (entry-heading-sexp item)
   (when show-genre?
     (block (wrap genre-text "Genre: ")))
   (block (wrap description))
   (block (wrap duration "" " Min., " false) (render-narrators narrators))
   (block producer-brief (if produced-commercially? ", Hörbuch aus dem Handel" "") ".")
   (ausleihe library-signature)
   (when (or product-number price-on-request?)
     (block {:keep-with-previous "always"} (bold "Verkauf:") " " product-number ", " price))))

(defmethod entry-sexp :braille
  [{:keys [genre-text description producer-brief rucksackbuch? rucksackbuch-number
           library-signature] :as item}
   {:keys [show-genre?] :or {show-genre? true}}]
  (list-item
   (entry-heading-sexp item)
   (when show-genre?
     (block (wrap genre-text "Genre: ")))
   (block (wrap description))
   (block
    (if rucksackbuch?
      (wrap producer-brief "" (str ", Rucksackbuch Nr. " rucksackbuch-number) false)
      (wrap producer-brief)))
   (ausleihe-multi library-signature)
   (verkauf item)))

(defmethod entry-sexp :grossdruck
  [{:keys [genre-text description library-signature volumes product-number price price-on-request?] :as item}
   {:keys [show-genre?] :or {show-genre? true}}]
  (list-item
   (entry-heading-sexp item)
   (when show-genre?
     (block (wrap genre-text "Genre: ")))
   (block (wrap description))
   (when library-signature
     (block {:keep-with-previous "always"}
            (bold "Ausleihe:") " " library-signature (wrap volumes ", " " Bd. " false)))
   (when (or product-number price-on-request?)
     (block {:keep-with-previous "always"} (bold "Verkauf:") " " price))))

(defmethod entry-sexp :e-book
  [{:keys [genre-text description library-signature] :as item}
   {:keys [show-genre?] :or {show-genre? true}}]
  (list-item
   (entry-heading-sexp item)
   (when show-genre?
     (block (wrap genre-text "Genre: ")))
   (block (wrap description))
   (ausleihe library-signature)))

(defmethod entry-sexp :hörfilm
  [{:keys [personel-text movie_country genre-text
           description producer library-signature] :as item}
   {:keys [show-genre?] :or {show-genre? true}}]
  (list-item
   (entry-heading-sexp item)
   (block (wrap personel-text))
   (block (wrap movie_country))
   (when show-genre?
     (block (wrap genre-text)))
   (block (wrap description))
   (block (wrap producer))
   (ausleihe library-signature)))

(defmethod entry-sexp :ludo
  [{:keys [source-publisher genre-text description
           game-description accompanying-material library-signature] :as item}
   {:keys [show-genre?] :or {show-genre? true}}]
  (list-item
   (entry-heading-sexp item)
   (block (wrap source-publisher))
   (when show-genre?
     (block (wrap genre-text)))
   (block (wrap description))
   (block (wrap game-description))
   (ausleihe-multi library-signature)
   (verkauf item)))

(defmethod entry-sexp :musiknoten
  [{:keys [description producer-brief library-signature] :as item} opts]
  (list-item
   (entry-heading-sexp item)
   (block (wrap description))
   (block (wrap producer-brief))
   (ausleihe-multi library-signature)
   (verkauf item)))

(defmethod entry-sexp :taktilesbuch
  [{:keys [genre-text description producer-brief library-signature] :as item}
   {:keys [show-genre?] :or {show-genre? true}}]
  (list-item
   (entry-heading-sexp item)
   (when show-genre?
     (block (wrap genre-text "Genre: ")))
   (block (wrap description))
   (block (wrap producer-brief))
   (ausleihe-multi library-signature)
   (verkauf item)))

(defn entries-sexp
  "Return a hiccup style sexp for given `items` and `opts`"
  [items opts]
  [:fo:block {:role "L"}
   (map #(entry-sexp % opts) items)])

(defn- level-to-h [level]
  (keyword (str "h" level)))

(defn subgenre-sexp
  "Return a hiccup style sexp for a subgenre"
  [items fmt genre subgenre level opts]
  [(heading (level-to-h level) [fmt genre subgenre] opts)
   (when-not (#{:kinder-und-jugendbücher} genre)
     (set-marker (layout/translations subgenre)))
   (entries-sexp items opts)])

(defn subgenres-sexp
  "Return a hiccup style sexp for all subgenres for given `genre`"
  [items fmt genre level opts]
  (mapcat #(subgenre-sexp (get items %) fmt genre % level opts) (keys items)))

(defn genre-sexp
  "Return a hiccup style sexp for a genre"
  [items fmt genre level opts]
  [(heading (level-to-h level) [fmt genre] opts)
   (set-marker (layout/translations genre))
   (cond
     ;; special case where the editorial or the recommendations are passed in the tree
     (#{:editorial :recommendations :recommendation} genre) (md-to-fop items [fmt genre] opts)
     ;; special case when printing the whole catalog of tactile and print-and-braille books
     (#{:taktilesbuch :print-and-braille} fmt) (entries-sexp items opts)
     (#{:kinder-und-jugendbücher} genre) (subgenres-sexp items fmt genre (inc level) opts)
     (#{:hörbuch} fmt) (subgenres-sexp items fmt genre (inc level) opts)
     :else (entries-sexp items opts))])

(defn format-sexp
  "Return a hiccup style sexp for a format"
  [items fmt level {:keys [with-toc?] :or {with-toc? true} :as opts}]
  [(heading (level-to-h level) [fmt] opts)
   (when with-toc?
     (toc items [fmt] 2 {}))
   (set-marker (layout/translations fmt))
   (case fmt
     ;; handle the special case where the editorial or the recommendations are passed in the tree
     (:editorial :recommendations :recommendation) (md-to-fop items [fmt] opts)
     (:hörfilm :ludo) (entries-sexp items opts)
     (mapcat #(genre-sexp (get items %) fmt % (inc level) opts) (keys items)))])

(defn- page-number []
  [:fo:wrapper {:role "artifact"}
   [:fo:page-number]])

(defn- header [side]
  (let [recto? (= side :recto)]
    [:fo:static-content {:flow-name (if recto? "xsl-region-before-recto" "xsl-region-before-verso")}
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
         margin-left "25mm" margin-right "25mm"}} & body]
  (let [attrs {:master-name master-name
               :page-height page-height
               :page-width page-width
               :margin-bottom margin-bottom
               :margin-top margin-top
               :margin-left margin-left
               :margin-right margin-right}]
    (if body
      [:fo:simple-page-master attrs body]
      [:fo:simple-page-master attrs
       [:fo:region-body {:margin-top (to-mm region-body-margin-top)}]
       [:fo:region-before {:region-name (format "xsl-region-before-%s" master-name)
                           ;; extent should be smaller than margin-top of region-body
                           :extent (to-mm (dec region-body-margin-top))}]])))

(def font-colors
  "Defines the font colors used on the cover page"
  {:white [0 0 0 0]
   :warmgrey [0 12 10 62]
   :blue [85 65 0 0]
   :lightgrey [0 12 20 70]})

(def issue-colors
  "Defines the bg colors used for the cover page of the respective issues"
  {1 [85 65 0 0]
   2 [69 0 31 7]
   3 [0 25 100 5]
   4 [33 71 0 0]
   5 [0 95 100 0]
   6 [50 0 100 15]})

(defn- cmyk [[cyan magenta yellow key]]
  (format "cmyk(%s,%s,%s,%s)" cyan magenta yellow key))

(defn- rgb [[cyan magenta yellow key]]
  (let [r (int (* 255 (- 1 cyan) (- 1 key)))
        g (int (* 255 (- 1 magenta) (- 1 key)))
        b (int (* 255 (- 1 yellow) (- 1 key)))]
    (format "#%02x%02x%02x" r g b)))

(defn- color [col]
  (->>
   (or (font-colors col) (issue-colors col))
   (map #(/ % 100.0))
   cmyk))

(defn- color-rgb [col]
  (->>
   (issue-colors col)
   (map #(/ % 100.0))
   rgb))

(def issue-indent
  "Mapping of indents for each issue."
  ;; In theory the issue numbers should be centered properly within the
  ;; circle. Somehow this doesn't seem to pan out, so we resort to
  ;; fidget with the indent for some issues.
  {3 "143mm"
   4 "140mm"})

(defn- coverpage-recto
  "Return a hiccup style sexp for the recto (the front) of the
  coverpage for given `title`, `year` and `issue`"
  [title year issue]
  (let [fill-color (format "fill:%s device-%s" (color-rgb issue) (color issue))]
    [:fo:page-sequence {:id "cover-recto" :master-reference "cover-recto"
                        :force-page-count "even"
                        :font-family  "StoneSansSemibold"}
     [:fo:static-content {:flow-name "xsl-region-start" :role "artifact"}
      [:fo:block]
      [:fo:block-container {:absolute-position "fixed" :width "210mm" :height "297mm"}
       (block {:font-size "0"}
              [:fo:external-graphic
               {:src (io/resource "covers/cover-recto.svg")
                :width "100%" :content-width "scale-to-fit"
                :fox:alt-text "Seiten-Hintergrund mit SBS Logo"}])]

      [:fo:block-container {:absolute-position "fixed" :width "210mm" :height "297mm" :left "0mm" :top "0mm" :background-color "transparent"}
       [:fo:block {:font-size "0" :start-indent "0mm" :end-indent "0mm"}
        [:fo:instream-foreign-object {:width "210mm" :height "297mm" :content-width "scale-to-fit"}
         [:svg {:xmlns:svg "http://www.w3.org/2000/svg" :xmlns "http://www.w3.org/2000/svg"
                :xmlns:xlink "http://www.w3.org/1999/xlink"
                :id "cover-recto-dynamic" :version "1.1"
                :width "210mm" :height "297mm" :viewBox "0 0 744.09497 1052.3625"}
          [:g {:transform "matrix(1.25,0,0,-1.25,0,1052.3625)" :style "fill-opacity:1;fill-rule:nonzero;stroke:none"}
           [:g {:id "etikett"}
            [:g {:transform "translate(166.8447,462.2588)" :visibility "visible"}
             [:path
              {:d "m 0,0 82.333,0 c 0,0 5,0.333 4.5,-5.333 -0.381,-4.32 -1.333,-22 -1.333,-22 -0.167,-3.5 0.167,-10.167 0.333,-12.167 0.167,-2 1.5,-4.333 0.334,-5 -1.167,-0.667 -86.834,0 -86.834,0 0,0 -5.214,-0.309 -5.75,3.333 -0.416,2.834 -0.25,12.667 -0.25,12.667 l 0,11.167 c 0,0 0.25,5.5 1.167,11.333 0.573,3.645 1.833,6 5.5,6"
               :style fill-color}]
             [:path
              {:d "m 0,0 82.333,0 c 0,0 5,0.333 4.5,-5.333 -0.381,-4.32 -1.333,-22 -1.333,-22 -0.167,-3.5 0.167,-10.167 0.333,-12.167 0.167,-2 1.5,-4.333 0.334,-5 -1.167,-0.667 -86.834,0 -86.834,0 0,0 -5.214,-0.309 -5.75,3.333 -0.416,2.834 -0.25,12.667 -0.25,12.667 l 0,11.167 c 0,0 0.25,5.5 1.167,11.333 0.573,3.645 1.833,6 5.5,6 z"
               :style "fill:none;stroke:#756662;stroke-width:0.89999998;stroke-linecap:round;stroke-linejoin:miter;stroke-miterlimit:4;stroke-dasharray:none;stroke-opacity:1"}]]]
           [:path {:d "m 0,0 70.866,0 0,841.89 L 0,841.89 0,0 Z" :style fill-color :id "balken"}]
           [:g {:transform "translate(477.9451,365.0547)"}
            [:path
             {:d "m 0,0 c 40.874,0 74.008,33.135 74.008,74.008 0,40.874 -33.134,74.008 -74.008,74.008 -40.874,0 -74.008,-33.134 -74.008,-74.008 C -74.008,33.135 -40.874,0 0,0"
              :style fill-color :id "kreis"}]]]]]]]]
     [:fo:flow {:flow-name"xsl-region-body"}
      [:fo:wrapper {:role "artifact"}
       [:fo:block-container {:absolute-position "fixed" :width "33mm" :height "15mm"
                             :left "56mm" :top "135mm" :display-align "center"
                             :background-color "transparent"}
        (block {:font-size "42pt" :line-height "1"
                :text-align "center" :color (color :white)
                :role "H2"}
               "neu")]]
      (block {:start-indent "3mm" :end-indent "3mm"
              :font-size "90pt" :color (color :warmgrey) :line-height "0.9"
              :text-align "start" :hyphenate "false"
              :role "H1"}
             (insert-zero-width-space title))
      [:fo:block-container {:absolute-position "fixed"
                            :width "297mm" :height "25mm"
                            :left "0mm" :top "0mm" :reference-orientation "90"}
       (block {:text-align "end" :font-size "46pt"
               :color (color :white) :hyphenate "false"
               :start-indent "10mm" :end-indent "10mm"
               :space-before "8mm" :space-before.conditionality "retain"
               :role "H2"}
              (layout/format-date year issue))]
      [:fo:block-container {:absolute-position "fixed" :width "53mm" :height "53mm"
                            :left (issue-indent issue "142mm") :top "119mm" :display-align "center"
                            :background-color "transparent"}
       (block {:font-size "120pt" :color (color :white)
               :text-align "center"
               :fox:alt-text (format "%s Ausgabe" (layout/ordinal issue))}
              (str issue))]]]))

(defn- coverpage-verso
  "Return a hiccup style sexp for the verso (the back) of the
  coverpage"
  [issue]
  (let [fill-color (format "fill:%s device-%s" (color-rgb issue) (color issue))]
    [:fo:page-sequence {:id "cover-verso" :master-reference "cover-verso"
                        :force-page-count "no-force"
                        :font-family "StoneSans, Arial"}
     [:fo:flow {:flow-name "xsl-region-body"}
      ;; add an empty page before the back cover
      [:fo:block {:break-before "page" :break-after "page"} " "]
      [:fo:wrapper {:role "artifact"}
       [:fo:block-container {:absolute-position "fixed" :width "210mm" :height "297mm"
                             :left "0mm" :top "0mm" :background-color "transparent"}
        [:fo:block {:font-size "0" :start-indent "0mm" :end-indent "0mm"}
         [:fo:instream-foreign-object {:width "210mm" :height "297mm" :content-width "scale-to-fit"}
          ;; embed svg of the cover
          [:svg {:xmlns:svg "http://www.w3.org/2000/svg" :xmlns "http://www.w3.org/2000/svg"
                 :id "cover-verso-dynamic" :version "1.1"
                 :width "210.00014mm" :height "297.00009mm" :viewBox "0 0 744.09497 1052.3625"}
           [:g {:transform "matrix(1.25,0,0,-1.25,0,1052.3625)" :style "fill-opacity:1;fill-rule:nonzero;stroke:none"}
            [:path {:d "m 14.173,14.173 496.063,0 0,813.543 -496.063,0 0,-813.543 z" :style "fill:#eeebea" :id "grau"}]
            [:path {:d "m 524.409,0 70.866,0 0,841.89 -70.866,0 0,-841.89 z" :style fill-color :id "balken-rechts"}]]]]]]]
      (block {:start-indent "5mm" :end-indent "5mm"
              :color (color :lightgrey) :line-height "1.4"
              :font-size "16pt" }
             (block {:font-size "20pt" :space-after "1em" :role "H2" :letter-spacing "150%" :font-family "StoneSansSemibold"}
                    "IMPRESSUM")
             (block {:color (color :blue) :space-after"1em" :font-family "StoneSansSemibold"}
                    (layout/translations :catalog-all))
             (block {:space-after "1em"} "Für Kundinnen und Kunden der SBS sowie für Interessenten")
             (block                      "Erscheint sechsmal jährlich und listet alle seit der letzten")
             (block {:space-after "1em"} "Ausgabe neu in die SBS aufgenommenen Bücher auf")
             (block                      "«Neu im Sortiment» kann im Jahresabonnement per Post")
             (block {:space-after "1em"} "zu CHF / € 78.– oder per E-Mail gratis bezogen werden")
             (block {:font-family "StoneSansSemibold"
                     :color (color :blue)
                     :space-before "3em" :space-after "1em" :role "H2"}
                    "Herausgeber")
             [:fo:block "SBS Schweizerische Bibliothek für"]
             [:fo:block "Blinde, Seh- und Lesebehinderte"]
             [:fo:block "Grubenstrasse 12"]
             [:fo:block "CH-8045 Zürich"]
             [:fo:table {:table-layout "fixed" :width"100%"}
              [:fo:table-column {:column-width "12mm"}]
              [:fo:table-column {:column-width "proportional-column-width(1)"}]
              [:fo:table-body {:start-indent "0mm" :end-indent "0mm"}
               [:fo:table-row
                [:fo:table-cell [:fo:block "Fon"]]
                [:fo:table-cell [:fo:block "+41 43 333 32 32"]]]
               [:fo:table-row
                [:fo:table-cell [:fo:block "Fax"]]
                [:fo:table-cell [:fo:block "+41 43 333 32 33"]]]]]
             [:fo:block
              (external-link "http://www.sbs.ch" "www.sbs.ch" "Link zur SBS Website")]
             (block {:space-before "1em"} "Abonnement, Ausleihe und Verkauf: "
                    (external-link "mailto:nutzerservice@sbs.ch" "nutzerservice@sbs.ch" "Email des SBS Nutzerservices"))
             (block "Verkauf Institutionen: " (external-link "mailto:medienverlag@sbs.ch" "medienverlag@sbs.ch" "Email des SBS Medienverlages"))
             (block {:space-before "2em" :font-size "12pt"}
                    "© SBS Schweizerische Bibliothek für Blinde, Seh- und Lesebehinderte"))]]))


(defn- layout-master-set []
  [:fo:layout-master-set
   (simple-page-master {:master-name "cover-recto-main"
                        :margin-left "0mm" :margin-right "0mm" :margin-top "0mm" :margin-bottom "0mm"}
                       [:fo:region-body
                        {:margin-left "30mm" :margin-right "5mm" :margin-top "178mm":margin-bottom "5mm"}]
                       [:fo:region-start {:extent "25mm" :reference-orientation "90"}])
   (simple-page-master {:master-name "cover-verso-main"
                        :margin-left "0mm" :margin-right "0mm" :margin-top "0mm" :margin-bottom "0mm"}
                       [:fo:region-body
                        {:margin-left "5mm" :margin-right "30mm" :margin-top "86mm" :margin-bottom "5mm"}]
                       [:fo:region-end {:extent "25mm" :reference-orientation "90"}])
   (simple-page-master {:master-name "recto" :margin-left "28mm" :margin-right "22mm"})
   (simple-page-master {:master-name "verso"})
   (simple-page-master {:master-name "blank"})
   (simple-page-master {:master-name "first"})

   [:fo:page-sequence-master {:master-name "main"}
    [:fo:repeatable-page-master-alternatives
     [:fo:conditional-page-master-reference {:master-reference "first" :page-position "first"}]
     [:fo:conditional-page-master-reference
      {:master-reference "verso" :odd-or-even "even" :blank-or-not-blank "not-blank"}]
     [:fo:conditional-page-master-reference
      {:master-reference "recto" :odd-or-even "odd" :blank-or-not-blank "not-blank"}]
     [:fo:conditional-page-master-reference {:master-reference "blank" :blank-or-not-blank "blank"}]]]
   [:fo:page-sequence-master {:master-name "cover-recto"}
    [:fo:repeatable-page-master-alternatives
     [:fo:conditional-page-master-reference {:master-reference "blank" :blank-or-not-blank "blank"}]
     [:fo:conditional-page-master-reference {:master-reference "cover-recto-main"}]]]
   [:fo:page-sequence-master {:master-name "cover-verso"}
    [:fo:repeatable-page-master-alternatives
     [:fo:conditional-page-master-reference {:master-reference "blank" :blank-or-not-blank "blank"}]
     [:fo:conditional-page-master-reference {:master-reference "cover-verso-main"}]]]])

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

(defn- realize-lazy-seqs
  "Realize all lazy sequences within `tree` to make sure the
  evaluation is done within a `binding` scope"
  [tree]
  ;; FIXME: there is a subtle bug to do with bindings and lazy
  ;; sequences. When returning a lazy seq the actual realization
  ;; might happen outside the `binding` scope and hence might get
  ;; the wrong value. So as a workaround we make sure
  ;; the sequence is fully realized.
  ;; See also http://stackoverflow.com/a/32290387 and
  ;; http://cemerick.com/2009/11/03/be-mindful-of-clojures-binding/
  ;; Maybe the binding solution isn't the bees knees after all.
  (walk/postwalk identity tree))

(defn- root-attrs []
  {:xmlns:fo "http://www.w3.org/1999/XSL/Format"
   :xmlns:fox "http://xmlgraphics.apache.org/fop/extensions"
   :line-height "1.3"
   :xml:lang "de"})

(defmulti document-sexp
  "Return a hiccup style sexp for a catalog document. Dispatch on the
  format (`fmt`) of the requested catalog.

  - `:all-formats` a standard catalog
  - `:grossdruck` a large print catalog using a big font
  - `:hörbuch` a catalog used for narrators to read the catalog with some specific sorting requirements
  - `:hörfilm` a catalog with all hörfile for a given year
  - `:ludo` a catalog with all games for a given year
  - `:taktilesbuch` a catalog with all tactile books for a given year
  - `:print-and-braille` a catalog with all print-and-braille books for a given year
  - `:custom` a catalog with a custom selection of catalog entries specific for a customer
  - `:custom-grossdruck` a catalog with a custom selection of catalog entries specific for a customer and in large print"
  (fn [items fmt year issue editorial recommendations options] fmt))

(defmethod document-sexp :grossdruck
  [items fmt _ _ editorial recommendations {:keys [description]}]
  (let [title (layout/translations :catalog-grossdruck)]
    (binding [*stylesheet* large-print-stylesheet]
      [:fo:root (style :font (root-attrs))
       (layout-master-set)
       (declarations title (or description title))
       [:fo:page-sequence {:master-reference "main"
                           :initial-page-number "1"
                           :force-page-count "end-on-even"
                           :language "de"}
        (header :recto)
        (header :verso)

        (let [subitems (-> items
                           (get fmt)
                           (cond->
                               (not (string/blank? editorial)) (assoc :editorial editorial)
                               (not (string/blank? recommendations)) (assoc :recommendation recommendations)))]
          [:fo:flow {:flow-name "xsl-region-body"}
           (toc subitems [fmt] 2 {:heading? true})
           (realize-lazy-seqs
            (mapcat #(genre-sexp (get subitems %) fmt % 1 {}) (keys subitems)))])]])))

(defmethod document-sexp :hörbuch
  [items fmt year issue editorial recommendations {:keys [description]}]
  (let [title (layout/translations :catalog-hörbuch)]
    [:fo:root (style :font (root-attrs))
     (layout-master-set)
     (declarations title (or description title))
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
                         (cond->
                             (not (string/blank? editorial)) (assoc :editorial editorial)
                             (not (string/blank? recommendations)) (assoc :recommendations recommendations)))
            path-to-numbers (layout/path-to-number subitems)]
        [:fo:flow {:flow-name "xsl-region-body"}
         ;; Cover page
         [:fo:block (style :h1)
          (format "%s Nr. %s/%s" title issue year)]
         (toc subitems [] 3 {:path-to-numbers path-to-numbers :heading? true})
         (mapcat #(format-sexp (get subitems %) % 1 {:path-to-numbers path-to-numbers :with-toc? false}) (keys subitems))])]]))

(defn- logo []
  [:fo:block {:space-before.optimum "100mm" :space-before.minimum "70mm" :text-align "right"}
   [:fo:external-graphic
    {:src (io/resource "images/sbs_logo.jpg")
     :height "35mm"
     :scaling "uniform"
     :content-width "scale-to-fit"
     :content-height "scale-to-fit"
     :fox:alt-text "SBS Logo"}]])

(defn- impressum []
  (let [creator (layout/translations :sbs)]
    [:fo:block-container {:break-before "page"}
     (block {:space-after.optimum "175mm" :space-after.minimum "150mm"} )
     (block "Herausgeber:")
     (block creator)
     (block "Grubenstrasse 12")
     (block "CH-8045 Zürich")
     (block "Fon +41 43 333 32 32")
     (block "Fax +41 43 333 32 33")
     (block (external-link "http://www.sbs.ch" "www.sbs.ch" "Link zur SBS Website"))
     (block (external-link "mailto:nutzerservice@sbs.ch" "nutzerservice@sbs.ch" "Email des SBS Nutzerservice"))
     (block {:space-before "5mm" :keep-with-previous.within-column "always"} (format "© %s" creator))]))

(defn- cover-page [titles year]
  [:fo:block-container
   (block {:space-after "50mm"} )
   (map #(block (style :h1 {:break-before "auto" :space-after "5pt"}) %) titles)
   (block (style :h2 {:break-before "auto" :space-after "5pt"})
          (format "Gesamtkatalog, Stand 1.1.%s" year))
   (logo)
   (impressum)])

(defn- custom-cover-page [title query customer]
  [:fo:block-container
   (block {:space-after "50mm"} )
   (block (style :h1 {:break-before "auto" :space-after "5pt"}) title)
   (block {:space-after "5pt"}
          (format "Stand %s"
                  (time.format/unparse (time.format/formatter "dd.MM.YYYY") (time.core/now))))
   (block {:space-before "5mm" :space-after "5mm"} query)
   (block (format "für %s" customer))
   (logo)
   (impressum)])

(defmethod document-sexp :hörfilm
  [items fmt year _ _ _ {:keys [description]}]
  (let [title (layout/translations :catalog-hörfilm)]
    [:fo:root (style :font (root-attrs))
     (layout-master-set)
     (declarations title (or description title))
     [:fo:page-sequence {:master-reference "main"
                         :initial-page-number "1"
                         :language "de"}
      (header :recto)
      (header :verso)

      (let [subitems (get items fmt)]
        [:fo:flow {:flow-name "xsl-region-body"}
         (cover-page [title "Filme mit Audiodeskription"] year)
         (toc subitems [fmt] 1 {:heading? true})
         (mapcat #(genre-sexp (get subitems %) fmt % 1 {:show-genre? false}) (keys subitems))])]]))

(defmethod document-sexp :ludo
  [items fmt year _ _ _ {:keys [description]}]
  (let [title (layout/translations :catalog-ludo)]
    [:fo:root (style :font (root-attrs))
     (layout-master-set)
     (declarations title (or description title))
     [:fo:page-sequence {:master-reference "main"
                         :initial-page-number "1"
                         :language "de"}
      (header :recto)
      (header :verso)

      (let [subitems (get items fmt)]
        [:fo:flow {:flow-name "xsl-region-body"}
         (cover-page ["Spiele in der SBS"] year)
         (toc subitems [fmt] 1 {:heading? true})
         (mapcat #(genre-sexp (get subitems %) fmt % 1 {:show-genre? false}) (keys subitems))])]]))

(defmethod document-sexp :taktilesbuch
  [items fmt year issue _ _ {:keys [description]}]
  (let [title (layout/translations :catalog-taktilesbuch)]
    [:fo:root (style :font (root-attrs))
     (layout-master-set)
     (declarations title (or description title))
     [:fo:page-sequence {:master-reference "main"
                         :initial-page-number "1"
                         :language "de"}
      (header :recto)
      (header :verso)

      (let [subitems (get items fmt)]
        [:fo:flow {:flow-name "xsl-region-body"}
         (cover-page [title] year)
         (toc subitems [fmt] 1 {:heading? true})
         (mapcat #(genre-sexp (get subitems %) fmt % 1 {}) (keys subitems))])]]))

(defmethod document-sexp :print-and-braille
  [items fmt year issue _ _ {:keys [description]}]
  (let [title (layout/translations :catalog-print-and-braille)]
    [:fo:root (style :font (root-attrs))
     (layout-master-set)
     (declarations title (or description title))
     [:fo:page-sequence {:master-reference "main"
                         :initial-page-number "1"
                         :language "de"}
      (header :recto)
      (header :verso)

      (let [subitems (get items fmt)]
        [:fo:flow {:flow-name "xsl-region-body"}
         (cover-page [title] year)
         (toc subitems [fmt] 1 {:heading? true})
         (mapcat #(genre-sexp (get subitems %) fmt % 1 {}) (keys subitems))])]]))

(defn- custom-document
  [title description query customer items]
  [:fo:root (style :font (root-attrs))
   (layout-master-set)
   (declarations title (or description query title))
   [:fo:page-sequence {:master-reference "main"
                       :initial-page-number "1"
                       :language "de"}
    (header :recto)
    (header :verso)

    [:fo:flow {:flow-name "xsl-region-body"}
     (custom-cover-page title query customer)
     ;; start the items on a new page
     (block {:break-after "page"})
     (entries-sexp items {})]]])

(defmethod document-sexp :custom
  [items fmt year issue _ _ {:keys [description query customer]}]
  (let [title (layout/translations :catalog-custom)]
    (custom-document title description query customer items)))

(defmethod document-sexp :custom-grossdruck
  [items fmt year issue _ _ {:keys [description query customer]}]
  (let [title (layout/translations :catalog-custom)]
    (binding [*stylesheet* large-print-stylesheet]
      (realize-lazy-seqs
       (custom-document title description query customer items)))))

(defmethod document-sexp :all-formats
  [items _ year issue _ _ {:keys [description]}]
  (let [title (layout/translations :catalog-all)]
    [:fo:root (style :font (root-attrs))
     (layout-master-set)
     (declarations title (or description title))
     (coverpage-recto title year issue)
     [:fo:page-sequence {:master-reference "main"
                         :initial-page-number "1"
                         :force-page-count "end-on-even"
                         :language "de"}
      (header :recto)
      (header :verso)

      [:fo:flow {:flow-name "xsl-region-body"}
       (toc items [] 1 {:heading? true})
       (mapcat #(format-sexp (get items %) % 1 {}) (keys items))]]
     (coverpage-verso issue)]))

(defn document
  "Return the xsl-fo XML for a catalog document. To produce PDF pass
  this XML to a xsl-fo processor. `items` contains a tree of catalog
  entries. `fmt` is the kind of format we want the catalog
  in (see [[document-sexp]]). `year` and `issue` are placed on the
  coverpage. `editorial` and `recommendations` are also placed inside
  the catalog (at least for some formats). Lastly optional args such
  as `query` or `customer` can be passed. These will be used on the
  cover page of custom catalogs."
  [items fmt year issue editorial recommendations &
   {:keys [description] :as args}]
  (-> items
      (document-sexp fmt year issue editorial recommendations args)
      xml/sexp-as-element))

(defn generate-document
  "Return the xsl-fo XML for a catalog document and store it in `out`.
  Similar to [[document]] but additionally stores the XML. Used for
  debugging."
  [items fmt year issue editorial recommendations out]
  (with-open [out (io/writer out)]
    (-> items
        (document fmt year issue editorial recommendations)
        (xml/indent out))))

(defn generate-pdf!
  "Return a pdf in `out` for given `document`, which is a string
  containing xsl-fo XML. The pdf is generated using the [Apache
  Fop](https://xmlgraphics.apache.org/fop/) xsl-fo formatting objects
  processor."
  [document out]
  ;; generate PDF according to https://xmlgraphics.apache.org/fop/2.0/embedding.html#basics
  (let [config-url (io/resource "fop.xconf")]
    (with-open [out (io/output-stream out)
                conf-stream (io/input-stream config-url)]
      (let [;; get the base uri to resolve resource URIs against
            base-uri (.toURI config-url)
            ;; Construct a FopFactory by specifying a base-uri and an
            ;; input stream containing the configuration
            factory (FopFactory/newInstance base-uri conf-stream)
            ;; get a user agent and add a listener to it that logs events
            user-agent (.newFOUserAgent factory)
            broadcaster (.getEventBroadcaster user-agent)
            _ (.addEventListener broadcaster
                                 (reify EventListener
                                   (processEvent [this event]
                                     (let [id (.getEventID event)]
                                       (when (not= id "org.apache.fop.render.RendererEventProducer.endPage")
                                         (println (EventFormatter/format event)))))))
            ;; Construct fop with desired output format
            fop (.newFop factory MimeConstants/MIME_PDF user-agent out)
            ;; Setup JAXP using identity transformer
            transformer (.newTransformer (TransformerFactory/newInstance))
            ;; Setup input and output for XSLT transformation
            source (StreamSource. (StringReader. (xml/emit-str document)))
            ;; Resulting SAX events (the generated FO) must be piped through to FOP
            saxresult (SAXResult. (.getDefaultHandler fop))]
        ;; Start XSLT transformation and FOP processing
        (.transform transformer source saxresult)))))
