(ns catalog.layout.fop
  (:require [catalog.layout.common :as layout]
            [clojure.data.xml :as xml]
            [clojure.java
             [io :as io]
             [shell :as shell]]
            [clojure.string :as string]))

(defn simple-page-master
  [{:keys [master-name page-height page-width
           margin-bottom margin-top
           margin-left margin-right]
    :or {page-height "297mm" page-width "210mm"
         margin-bottom "50mm" margin-top "20mm"
         margin-left "15mm" margin-right "35mm"}}]
  [:fo:simple-page-master {:master-name master-name
                           :page-height page-height
                           :page-width page-width
                           :margin-bottom margin-bottom
                           :margin-top margin-top
                           :margin-left margin-left
                           :margin-right margin-right}
   [:fo:region-body {:margin-top "15mm"}]
   [:fo:region-before {:region-name (format "xsl-region-before-%s" master-name)}]])

(defn heading [h hash opts]
  [:fo:block
   (merge {:font-weight "bold"
           :keep-with-next "always"
           :id hash
           :space-before "11pt"
           :space-after "11pt"} opts)
   (layout/translations h)])

(defn h1 [h]
  (heading h (hash [nil h]) {:break-before "odd-page" :font-size "21pt" :role "H1"}))

(defn h2 [fmt h]
  (heading h (hash [fmt h]) {:font-size "17pt" :role "H2"}))

(defn h3 [fmt genre h]
  (heading h (hash [fmt genre h]) {:font-size "15pt" :role "H3"}))

(defn h4 [fmt genre subgenre h]
  (heading h (hash [fmt genre subgenre h]) {:font-size "13pt" :role "H3"}))

(defn toc-entry [items fmt]
  (when (fmt items)
    [:fo:block {:text-align-last "justify"}
     [:fo:basic-link {:internal-destination (hash [nil fmt])}
      (str (layout/translations fmt) " ")
      [:fo:leader {:leader-pattern "dots"}]
      [:fo:page-number-citation {:ref-id (hash [nil fmt])}]]]))

(defn toc [items formats]
  [:fo:block
   (h1 :inhalt)
   (keep #(toc-entry items %) formats)])

(defn sub-toc-entry [items fmt genre]
  (when (genre items)
    [:fo:block {:text-align-last "justify"}
     [:fo:basic-link {:internal-destination (hash [fmt genre])}
      (str (layout/translations genre) " ")
      [:fo:leader {:leader-pattern "dots"}]
      [:fo:page-number-citation {:ref-id (hash [fmt genre])}]]]))

(defn sub-toc [items fmt]
  [:fo:block
   (keep #(sub-toc-entry items fmt %) layout/genres)])

(def wrap (layout/wrapper ""))

(defn- to-url
  "Return an url given a `record-id`"
  [record-id]
  (let [api-key "c97386a2-914a-40c2-bd8d-df4c273175e6"]
    (format "http://online.sbs.ch/iguana/www.main.cls?v=%s&amp;sUrl=search%%23RecordId=%s"
            api-key
            (string/replace record-id "/" "."))))

(defn- external-link [record-id title]
  [:fo:basic-link {:external-destination (to-url record-id)
                   :indicate-destination true}
   title])

(defn- block [& args]
  (let [default {:text-align "justify" :hyphenate "true"}]
    (if (map? (first args))
      [:fo:block (merge default (first args)) (rest args)]
      [:fo:block default args])))

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
           (external-link record-id (layout/periodify title)))
     (layout/render-subtitles subtitles)
     (layout/periodify name-of-part)
     (when (or source-publisher source-date) " - ")
     (wrap source-publisher "" (if source-date ", " "") false)
     (wrap (layout/year source-date))))

(defn- ausleihe [signature]
  (when signature
    (block {:keep-with-previous "always"} (bold "Ausleihe:") " " signature)))

(defn- verkauf [product-number price]
  (when product-number
     (block {:keep-with-previous "always"} (bold "Verkauf:") " " price ", " (layout/braille-signatures product-number))))

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
   (when library-signature
     (block {:keep-with-previous "always"} (bold "Ausleihe:") " " (layout/braille-signatures library-signature)))
   (verkauf product-number price)))

(defmethod entry-sexp :grossdruck
  [{:keys [creator record-id title subtitles name-of-part source-publisher source-date
           genre-text description library-signature volumes product-number price]}]
  (list-item
   (entry-heading-sexp creator record-id title subtitles name-of-part source-publisher source-date)
   (block (wrap genre-text "Genre: "))
   (block (wrap description))
   (when library-signature
     (block {:keep-with-previous "always"} (bold "Ausleihe:") " " library-signature (wrap volumes ", " " Bd. " false)))
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
     (bold (external-link record-id (layout/periodify title)))
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
     (bold (external-link record-id (layout/periodify title)))
     (layout/render-subtitles subtitles) " "
     (layout/periodify creator))
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
   (ausleihe library-signature))
   (verkauf product-number price))

(defmethod entry-sexp :taktilesbuch
  [{:keys [creator record-id title subtitles name-of-part source-publisher source-date
           description producer-brief library-signature product-number price]}]
  (list-item
   (entry-heading-sexp creator record-id title subtitles name-of-part source-publisher source-date)
   (block (wrap description))
   (block (wrap producer-brief))
   (block (bold "Ausleihe:") " " (layout/braille-signatures library-signature))
   (verkauf product-number price)))

(defn entries-sexp [items]
  [:fo:list-block
   (for [item items] (entry-sexp item))])

(defn subgenre-sexp [items fmt genre subgenre]
  (when-let [items (subgenre items)]
    [(h3 fmt genre subgenre)
     (entries-sexp items)]))

(defn subgenres-sexp [items fmt genre]
  (mapcat #(subgenre-sexp items fmt genre %) layout/subgenres))

(defn genre-sexp [items fmt genre]
  (when-let [items (genre items)]
    [(h2 fmt genre)
     (cond
       (#{:kinder-und-jugendbücher} genre) (subgenres-sexp items fmt genre)
       (#{:hörbuch} fmt) (subgenres-sexp items fmt genre)
       :else (entries-sexp items))]))

(defn format-sexp [items fmt]
  (when-let [items (fmt items)]
    [(h1 fmt)
     (sub-toc items fmt)
     (case fmt
       (:hörfilm :ludo) (entries-sexp items)
       (mapcat #(genre-sexp items fmt %) layout/genres))]))

(defn- retrieve-marker []
  [:fo:inline
   [:fo:retrieve-marker {:retrieve-class-name "head"
                         :retrieve-position "last-ending-within-page"
                         :retrieve-boundary "page-sequence"}]])

(defn- page-number []
  [:fo:inline
   [:fo:page-number]])

(defn- header [side]
  (let [recto? (= side :recto)]
    [:fo:static-content {:flow-name (if recto? "xsl-region-before-recto" "xsl-region-before-verso")
                         :role "artifact"}
     [:fo:block {:text-align-last "justify" :font-weight "bold" :border-bottom "thin solid"}
      (if recto? (retrieve-marker) (page-number))
      [:fo:leader]
      (if recto? (page-number) (retrieve-marker))]]))

(defn document-sexp
  [items {:keys [title creator description date]
    :or {title "Neu im Sortiment"
         creator "SBS Schweizerische Bibliothek für Blinde, Seh- und Lesebehinderte"
         description "Katalog der neu erschienenen Bücher"}}]
  [:fo:root {:xmlns:fo "http://www.w3.org/1999/XSL/Format"
             :font-family "Verdana"
             :font-size "11pt"
             :line-height "130%"
             :xml:lang "de"}
   [:fo:layout-master-set
    (simple-page-master {:master-name "recto"})
    (simple-page-master {:master-name "verso"})
    [:fo:page-sequence-master {:master-name "main"}
     [:fo:repeatable-page-master-alternatives
      [:fo:conditional-page-master-reference {:master-reference "verso" :odd-or-even "even"}]
      [:fo:conditional-page-master-reference {:master-reference "recto" :odd-or-even "odd"}]]]]
   [:fo:declarations
    [:x:xmpmeta {:xmlns:x "adobe:ns:meta/"}
     [:rdf:RDF {:xmlns:rdf"http://www.w3.org/1999/02/22-rdf-syntax-ns#"}
      [:rdf:Description {:rdf:about ""
                         :xmlns:dc "http://purl.org/dc/elements/1.1/"}
       ;; Dublin Core properties
       [:dc:title title]
       [:dc:creator creator]
       [:dc:description description]]
      [:rdf:Description {:rdf:about ""
                         :xmlns:xmp "http://ns.adobe.com/xap/1.0/"}
       ;; XMP properties
       [:xmp:CreatorTool "Apache FOP"]]]]]
   [:fo:page-sequence {:master-reference "main"
                       :language "de"}
    [:fo:flow {:flow-name "xsl-region-body"}
     [:fo:block {:font-size "26pt"} title]
     [:fo:block {:space-before "20mm"} date]
     [:fo:block {:space-before "170mm"} creator]]]
   [:fo:page-sequence {:master-reference "main"
                       :initial-page-number "1"
                       :language "de"}
    (header :recto)
    (header :verso)

    [:fo:flow {:flow-name "xsl-region-body"}
     (toc items layout/formats)
     (mapcat #(format-sexp items %) layout/formats)]]])

(defn document [items]
  (with-open [out (io/writer "catalog.xml")]
    (-> (document-sexp items {})
        xml/sexp-as-element
        (xml/indent out))))

(defn generate-pdf []
  (shell/sh "fop" "-c" "resources/fop.xconf" "catalog.xml" "catalog.pdf"))
