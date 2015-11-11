(ns catalog.latex
  "Generate LaTeX for catalogues"
  (:require [catalog.vubis :as vubis]
            [clj-time
             [coerce :as time.coerce]
             [core :as time.core]
             [format :as time.format]]
            [clojure.java
             [io :as io]
             [shell :as shell]]
            [clojure.string :as string]
            [comb.template :as template])
  (:import java.util.Locale))

(def temp-name "/tmp/catalog.tex")
(def translations {:hörbuch "Neue Hörbücher"
                   :braille "Neue Braillebücher"
                   :grossdruck "Neue Grossdruckbücher"
                   :e-book "Neue E-Books"
                   :hörfilm "Neue Hörfilme"
                   :ludo "Neue Spiele"
                   :belletristik "Belletristik"
                   :sachbücher "Sachbücher"
                   :kinder-und-jugendbücher "Kinder- und Jugendbücher"
                   :action-und-thriller "Action und Thriller"
                   :beziehungsromane "Beziehungsromane"
                   :fantasy-science-fiction "Fantasy, Science Fiction"
                   :gesellschaftsromane "Gesellschaftsromane"
                   :historische-romane "Historische Romane"
                   :hörspiele "Hörspiele"
                   :krimis "Krimis"
                   :lebensgeschichten-und-schicksale "Lebensgeschichten und Schicksale"
                   :literarische-gattungen "Literarische Gattungen"
                   :literatur-in-fremdsprachen "Literatur in Fremdsprachen"
                   :mundart-heimat-natur "Mundart, Heimat, Natur"
                   :glaube-und-philosophie "Glaube und Philosophie"
                   :biografien "Biografien"
                   :freizeit-haus-garten "Freizeit, Haus, Garten"
                   :geschichte-und-gegenwart "Geschichte und Gegenwart"
                   :kunst-kultur-medien "Kunst, Kultur, Medien"
                   :lebensgestaltung-gesundheit-erziehung "Lebensgestaltung, Gesundheit, Erziehung"
                   :philosophie-religion-esoterik "Philosophie, Religion, Esoterik"
                   :reisen-natur-tiere "Reisen, Natur, Tiere"
                   :sprache "Sprache"
                   :wissenschaft-technik "Wissenschaft, Technik"
                   :jugendbücher "Jugendbücher"
                   :kinder-und-jugendsachbücher "Kinder- und Jugendsachbücher"
                   :kinderbücher-ab-10 "Kinderbücher (ab 10)"
                   :kinderbücher-ab-6 "Kinderbücher (ab 6)"})

(def formats [:hörbuch :braille :grossdruck :e-book :hörfilm :ludo])
(def genres [:belletristik :sachbücher :kinder-und-jugendbücher])
(def subgenres [:action-und-thriller :beziehungsromane :fantasy-science-fiction
                :gesellschaftsromane :historische-romane :hörspiele :krimis
                :lebensgeschichten-und-schicksale :literarische-gattungen
                :literatur-in-fremdsprachen :mundart-heimat-natur :glaube-und-philosophie
                :biografien :freizeit-haus-garten :geschichte-und-gegenwart
                :kunst-kultur-medien :lebensgestaltung-gesundheit-erziehung
                :philosophie-religion-esoterik :reisen-natur-tiere :sprache
                :wissenschaft-technik :jugendbücher :kinder-und-jugendsachbücher
                :kinderbücher-ab-10 :kinderbücher-ab-6])

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
  (zipmap (keys item)
          (map (fn [v] (if (string? v) (escape-str v) v)) (vals item))))

(defn to-url
  "Return an url given a `record-id`"
  [record-id]
  (let [api-key "c97386a2-914a-40c2-bd8d-df4c273175e6"]
    (format "http://online.sbs.ch/iguana/www.main.cls?v=%s&amp;sUrl=search\\%%23RecordId=%s"
            api-key
            (string/replace record-id "/" "."))))

(defn year [date]
  (time.format/unparse
   (time.format/formatters :year) (time.coerce/from-date date)))

(defn periodify
  "Add a period to the end of `s` if it is not nil and doesn't end in
  punctuation. If `s` is nil return an empty string."
  [s]
  (cond
    (nil? s) ""
    (re-find #"[.,:!?]$" s) s
    :else (str s ".")))

(defn wrap
  "Add a period to `s` and wrap it in `prefix` and `postfix`.
  Typically this is used to put `s` on a line, i.e. where prefix is ''
  and postfix is ' \\ '."
  ([s]
   (wrap s ""))
  ([s prefix]
   (wrap s prefix " \\\\ "))
  ([s prefix postfix]
   (wrap s prefix postfix true))
  ([s prefix postfix period?]
   (if s (str prefix (if period? (periodify s) s) postfix) "")))

(defn braille-signature [[signature grade volumes :as item]]
  (when item
    (let [translations {:kurzschrift "Kurzschrift" :vollschrift "Vollschrift"}]
      (string/join
       ", "
       [(translations grade) (format "%s Bd." volumes) (periodify signature)]))))

(defn braille-signatures [items]
  (->>
   [:kurzschrift :vollschrift :weitzeilig]
   (map (fn [grade] (braille-signature (first (grade items)))))
   (remove nil?)
   (string/join " ")))

(defn render-narrators [narrators]
  (let [narrator (first narrators)]
    (if (> (count narrators) 1)
      (wrap narrator "gelesen von: " " u.a. \\\\ " false)
      (wrap narrator "gelesen von: "))))

(def render-hörbuch
  (template/fn [{:keys [creator record-id title subtitle name-of-part source-publisher
                        source-date genre description duration narrators producer-brief
                        produced-commercially? library-signature product-number price]}]
    (io/file (io/resource "templates/hörbuch.tex"))))

(def render-braillebuch
  (template/fn [{:keys [creator record-id title subtitle name-of-part source-publisher source-date
                        genre description producer-brief rucksackbuch? rucksackbuch-number
                        library-signature product-number price]}]
    (io/file (io/resource "templates/braillebuch.tex"))))

(def render-taktilesbuch
  (template/fn [{:keys [creator record-id title subtitle name-of-part source-publisher source-date
                        description producer-brief library-signature product-number price]}]
    (io/file (io/resource "templates/taktilesbuch.tex"))))

(def render-musiknoten
  (template/fn [{:keys [creator record-id title subtitle name-of-part source-publisher source-date
                        description producer-brief library-signature product-number price]}]
    (io/file (io/resource "templates/musiknoten.tex"))))

(def render-grossdruckbuch
  (template/fn [{:keys [creator record-id title subtitle name-of-part source-publisher source-date
                        genre description library-signature volumes product-number price]}]
    (io/file (io/resource "templates/grossdruckbuch.tex"))))

(def render-e-book
  (template/fn [{:keys [creator record-id title subtitle name-of-part source-publisher source-date
                        genre description library-signature]}]
    (io/file (io/resource "templates/e-book.tex"))))

(def render-hörfilm
  (template/fn [{:keys [record-id title subtitle personel-name movie_country genre
                        description producer library-signature]}]
    (io/file (io/resource "templates/hörfilm.tex"))))

(def render-spiel
  (template/fn [{:keys [record-id title subtitle creator source_publisher game_category description
                        game_materials library-signature]}]
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
    (render-subsubsection (translations subgenre "FIXME")
                          (catalog-entries items))))

(defn subgenre-entries [items]
  (string/join (for [subgenre subgenres] (subgenre-entry subgenre items))))

(defn genre-entry [fmt genre items]
  (when-let [items (genre items)]
    (render-subsection
     (translations genre "FIXME")
     (cond
       (#{:kinder-und-jugendbücher} genre) (subgenre-entries items)
       (#{:hörbuch} fmt) (subgenre-entries items)
       :else (catalog-entries items)))))

(defn genre-entries [fmt items]
  (string/join (for [genre genres] (genre-entry fmt genre items))))

(defn format-entry [fmt items]
  (when-let [items (fmt items)]
    (render-section (translations fmt "FIXME")
                    (pagestyles fmt)
                    (case fmt
                      (:hörfilm :ludo) (catalog-entries items)
                      (genre-entries fmt items)))))

(defn format-entries [items]
  (string/join (for [fmt formats] (format-entry fmt items))))

(def render-document (template/fn [title class options font creator volume date body]
                       (io/file (io/resource "templates/document.tex"))))

(defn volume-number [date]
  (let [month (.getMonthOfYear date)]
    (quot (inc month) 2)))

(defn format-date [date]
  (time.format/unparse
   (time.format/with-locale (time.format/formatter "MMMM yyyy") Locale/GERMAN)
   date))

(defn document [title items &
                {:keys [class options font creator volume date]
                 :or {class "memoir"
                      options #{"11pt" "a4paper" "twoside" "openright"}
                      font "Verdana"
                      creator "SBS Schweizerische Bibliothek für Blinde, Seh- und Lesebehinderte"
                      volume (volume-number (time.core/today))
                      date (format-date (time.core/now))}}]
  (render-document
   title class (string/join "," options) font creator volume date (format-entries items)))

(defn generate-latex [items]
  (spit temp-name (document "Neu im Sortiment" (vubis/order-and-group items))))

(defn generate-pdf []
  (shell/sh "latexmk" "-xelatex" temp-name :dir "/tmp"))
