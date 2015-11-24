(ns catalog.layout.latex
  "Generate LaTeX for catalogues"
  (:require [catalog.layout.common :as layout :refer [periodify wrap year braille-signatures]]
            [clj-time.core :as time.core]
            [clojure
             [string :as string]
             [walk :as walk]]
            [clojure.java
             [io :as io]
             [shell :as shell]]
            [comb.template :as template]))

(def temp-name "/tmp/catalog.tex")

(def formats [:hörbuch :braille :grossdruck :e-book :hörfilm :ludo])

(def pagestyles {:hörbuch "audiobook"
                 :braille "anybook"
                 :grossdruck "anybook"
                 :e-book "anybook"
                 :hörfilm "anybook"
                 :ludo "anybook"})

(defn escape-str
  "Escape characters that have a special meaning to LaTeX (see [The
  Comprehensive LaTeX Symbol
  List](http://www.ctan.org/tex-archive/info/symbols/comprehensive/symbols-a4.pdf))"
  [text]
  (if (nil? text) ""
      (reduce
       (fn [txt [regexp replacement]]
         (string/replace txt regexp replacement))
       text
       [[#"\\" "\\\\textbackslash "] ; backslash
        [#"\s+" " "] ; drop excessive white space
        [#"(\$|&|%|#|_|\{|\})" "\\\\$1"] ; quote special chars
        [#"(~|\^)" "$1{}"] ; append a '{}' to special chars so they are not missinterpreted
        [#" ([–—]\p{P})" " $1"] ; add non-breaking space in front of emdash or endash followed by punctuation
        [#" ((\.{3}|…)\p{P})" " $1"] ; add non-breaking space in front ellipsis followed by punctuation
        [#"\[" "\\\\lbrack{}"] ; [ and ] can sometimes be interpreted as the start or the end of an optional argument
        [#"\]" "\\\\rbrack{}"]
        [#"<<" "{<}<"] ; << and >> is apparently treated as a shorthand and is not handled by our shorthand disabling code
        [#">>" "{>}>"]])))

(defn escape [item]
  (walk/postwalk #(if (string? %) (escape-str %) %) item))

(defn to-url
  "Return an url given a `record-id`"
  [record-id]
  (let [api-key "c97386a2-914a-40c2-bd8d-df4c273175e6"]
    (format "http://online.sbs.ch/iguana/www.main.cls?v=%s&amp;sUrl=search\\%%23RecordId=%s"
            api-key
            (string/replace record-id "/" "."))))

(defn render-narrators [narrators]
  (let [narrator (first narrators)]
    (if (> (count narrators) 1)
      (wrap narrator "gelesen von: " " u.a. \\\\ " false)
      (wrap narrator "gelesen von: "))))

(defn render-subtitles [subtitles]
  (periodify (string/join ". " subtitles)))

(def render-hörbuch
  (template/fn [{:keys [creator record-id title subtitles name-of-part source-publisher
                        source-date genre-text description duration narrators producer-brief
                        produced-commercially? library-signature product-number price]}]
    (io/file (io/resource "templates/hörbuch.tex"))))

(def render-braillebuch
  (template/fn [{:keys [creator record-id title subtitles name-of-part source-publisher source-date
                        genre-text description producer-brief rucksackbuch? rucksackbuch-number
                        library-signature product-number price]}]
    (io/file (io/resource "templates/braillebuch.tex"))))

(def render-taktilesbuch
  (template/fn [{:keys [creator record-id title subtitles name-of-part source-publisher source-date
                        description producer-brief library-signature product-number price]}]
    (io/file (io/resource "templates/taktilesbuch.tex"))))

(def render-musiknoten
  (template/fn [{:keys [creator record-id title subtitles name-of-part source-publisher source-date
                        description producer-brief library-signature product-number price]}]
    (io/file (io/resource "templates/musiknoten.tex"))))

(def render-grossdruckbuch
  (template/fn [{:keys [creator record-id title subtitles name-of-part source-publisher source-date
                        genre-text description library-signature volumes product-number price]}]
    (io/file (io/resource "templates/grossdruckbuch.tex"))))

(def render-e-book
  (template/fn [{:keys [creator record-id title subtitles name-of-part source-publisher source-date
                        genre-text description library-signature]}]
    (io/file (io/resource "templates/e-book.tex"))))

(def render-hörfilm
  (template/fn [{:keys [record-id title subtitles directed-by actors movie_country genre-text
                        description producer library-signature]}]
    (io/file (io/resource "templates/hörfilm.tex"))))

(def render-spiel
  (template/fn [{:keys [record-id title subtitles creator source-publisher genre-text description
                        game-description accompanying-material library-signature]}]
    (io/file (io/resource "templates/spiel.tex"))))

(defmulti catalog-entry (fn [{fmt :format}] fmt))

(defmethod catalog-entry :hörbuch [item] (render-hörbuch item))
(defmethod catalog-entry :braille [item] (render-braillebuch item))
(defmethod catalog-entry :grossdruck [item] (render-grossdruckbuch item))
(defmethod catalog-entry :e-book [item] (render-e-book item))
(defmethod catalog-entry :hörfilm [item] (render-hörfilm item))
(defmethod catalog-entry :ludo [item] (render-spiel item))
(defmethod catalog-entry :musiknoten [item] (render-musiknoten item))
(defmethod catalog-entry :taktilesbuch [item] (render-taktilesbuch item))
(defmethod catalog-entry :default [item] (render-hörbuch item))

(def render-catalog-entries (template/fn [body] (io/file (io/resource "templates/catalog-entries.tex"))))
(def render-section (template/fn [title pagestyle body] (io/file (io/resource "templates/section.tex"))))
(def render-subsection (template/fn [title body] (io/file (io/resource "templates/subsection.tex"))))
(def render-subsubsection (template/fn [title body] (io/file (io/resource "templates/subsubsection.tex"))))

(defn catalog-entries [items]
  (render-catalog-entries
   (string/join (for [item items] (catalog-entry (escape item))))))

(defn subgenre-entry [subgenre items]
  (when-let [items (subgenre items)]
    (render-subsubsection (layout/translations subgenre "FIXME")
                          (catalog-entries items))))

(defn subgenre-entries [items]
  (string/join (for [subgenre layout/subgenres] (subgenre-entry subgenre items))))

(defn genre-entry [fmt genre items]
  (when-let [items (genre items)]
    (render-subsection
     (layout/translations genre "FIXME")
     (cond
       (#{:kinder-und-jugendbücher} genre) (subgenre-entries items)
       (#{:hörbuch} fmt) (subgenre-entries items)
       :else (catalog-entries items)))))

(defn genre-entries [fmt items]
  (let [genres (if (= fmt :braille) layout/braille-genres layout/genres)]
    (string/join (for [genre genres] (genre-entry fmt genre items)))))

(defn format-entry [fmt items]
  (when-let [items (fmt items)]
    (render-section (layout/translations fmt "FIXME")
                    (pagestyles fmt)
                    (case fmt
                      (:hörfilm :ludo) (catalog-entries items)
                      (genre-entries fmt items)))))

(defn format-entries [items]
  (string/join (for [fmt formats] (format-entry fmt items))))

(def render-document (template/fn [title class options font creator volume date body]
                       (io/file (io/resource "templates/document.tex"))))

(defn document [title items &
                {:keys [class options font creator volume date]
                 :or {class "memoir"
                      options #{"11pt" "a4paper" "twoside" "openright"}
                      font "Verdana"
                      creator "SBS Schweizerische Bibliothek für Blinde, Seh- und Lesebehinderte"
                      volume (layout/volume-number (time.core/today))
                      date (layout/format-date (time.core/now))}}]
  (render-document
   title class (string/join "," options) font creator volume date (format-entries items)))

(defn generate-latex [items]
  (spit temp-name (document "Neu im Sortiment" items)))

(defn generate-pdf []
  (shell/sh "latexmk" "-xelatex" temp-name :dir "/tmp"))
