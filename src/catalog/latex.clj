(ns catalog.latex
  "Generate LaTeX for catalogues"
  (:require [catalog.vubis :as vubis]
            [clojure.java.shell :as shell]
            [clojure.string :as string]))

(def temp-name "/tmp/catalog.tex")
(def translations {:hörbuch "Hörbücher"
                   :braille "Braille"
                   :grossdruck "Grossdruck"
                   :e-book "E-Books"
                   :hörfilm "Hörfilme"
                   :ludo "Spiele"
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

(defn escape
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

(defn preamble [{:keys [title font creator] :or {font "Verdana"}}]
  (let [[title creator] (map escape [title creator])]
    (format
     "\\isopage[12]
\\fixpdflayout
\\checkandfixthelayout
\\usepackage{graphicx}
\\usepackage{titletoc}
\\usepackage[ngerman]{babel}
\\def\\languageshorthands#1{}
\\usepackage{fontspec,xunicode,xltxtra}
\\defaultfontfeatures{Mapping=tex-text}
\\setmainfont{%s}
\\usepackage{hyperref}
\\hypersetup{pdftitle={%s}, pdfauthor={%s}}
\\setsecnumdepth{subsubsection}
\\setlength{\\parindent}{0pt}" font title creator)))

(defn frontmatter [{:keys [title issue-number year]}]
  (format
   "\\frontmatter
\\begin{Spacing}{1.75}
{\\huge %s}\\\\[0.5cm]
\\end{Spacing}
{\\large Ausgabenummer und Jahr}\\\\[1.5cm]
\\vfill
Logo\\\\
SBS Schweizerische Bibliothek für Blinde, Seh- und Lesebehinderte\\\\[0.5cm]
\\cleartorecto" (escape title)))

(defn mainmatter [env]
  "\\mainmatter
%% \\pagestyle{plain}
\\tableofcontents")

(defmulti catalog-entry (fn [{fmt :format}] fmt))

(defmethod catalog-entry :hörfilm
  [{:keys [title personel-name description source_publisher source_date genre library_signature]}]
  (apply
   format "\\begin{description}
\\item[%s] Regie: %s %s %s \\\\ %s %s
\\end{description}"
   (map escape
        [title personel-name source_date (translations genre) description library_signature])))

(defmethod catalog-entry :hörbuch
  [{:keys [creator title description source_publisher source_date genre duration narrator
           producer_brief produced_commercially
           library_signature price]}]
  (apply
   format "\\begin{description}
\\item[%s] %s %s %s \\\\ Genre: %s \\\\ %s \\\\ %s Min. Gelesen von: %s \\\\ Prod.: %s %s \\\\ Ausleihe: %s \\\\ Verkauf: %s %s
\\end{description}"
   (map escape [creator title source_publisher source_date (translations genre)
                description
                duration narrator
                producer_brief (if produced_commercially "Hörbuch aus dem Handel" "")
                library_signature
                "foo"
                price])))

(defmethod catalog-entry :default
  [{:keys [title creator description source_publisher source_date genre]}]
  (apply
   format "\\begin{description}
\\item[%s] %s %s %s %s \\\\ %s
\\end{description}"
   (map escape [creator title source_publisher source_date (translations genre) description])))

(defn catalog-entries [items]
  (for [item items] (catalog-entry item)))

(defn subgenre-entry [subgenre items]
  (when (subgenre items)
    [(format "\\subsubsection{%s}" (translations subgenre "FIXME"))
     (catalog-entries (subgenre items))]))

(defn subgenre-entries [items]
  (for [subgenre subgenres] (subgenre-entry subgenre items)))

(defn genre-entry [fmt genre items]
  (when (genre items)
    [(format "\\subsection{%s}" (translations genre "FIXME"))
     (cond
       (#{:kinder-und-jugendbücher} genre) (subgenre-entries (genre items))
       (#{:hörbuch} fmt) (subgenre-entries (genre items))
       :else (catalog-entries (genre items)))]))

(defn genre-entries [fmt items]
  (for [genre genres] (genre-entry fmt genre items)))

(defn format-entry [fmt items]
  (when (fmt items)
    [(format "\\section{%s}" (translations fmt "FIXME"))
     ;; start a section toc
     "\\startcontents"
     ;; the toc should range from levels 2 to 3 inclusive (i.e. 2 and 3)
     "\\printcontents{}{2}{\\setcounter{tocdepth}{3}}"
     (case fmt
       (:hörfilm :ludo) (catalog-entries (fmt items))
       (genre-entries fmt (fmt items)))]))

(defn format-entries [items]
  (for [fmt formats] (format-entry fmt items)))

(defn catalog [{items :items}]
  ["\\chapter{Katalog}"
   (format-entries items)])

(defn impressum [env]
   "\\chapter{Impressum}

Neu im Sortiment

Für Kundinnen und Kunden der SBS sowie für Interessenten

Erscheint sechsmal jährlich und weist alle seit der letzten Ausgabe neu in die SBS aufgenommenen Bücher nach

Neu im Sortiment kann im Jahresabonnement per Post zu CHF XX oder per E-Mail gratis bezogen werden.

Herausgeber:\\\\
SBS Schweizerische Bibliothek für Blinde, Seh- und Lesebehinderte\\\\
Grubenstrasse 12\\\\
CH-8045 Zürich\\\\
Fon +41 43 333 32 32\\\\
Fax +41 43 333 32 33\\\\
www.sbs.ch

Abonnement, Ausleihe und Verkauf: nutzerservice@sbs.ch\\\\
Verkauf Institutionen: medienverlag@sbs.ch

Copyright Vermerk")

(defn document [{:keys [class options]
                 :or {class "memoir"
                      options #{"11pt" "a4paper" "oneside" "openright"}}
                 :as env}]
  (string/join "\n"
   (flatten
    [(format "\\documentclass[%s]{%s}" (string/join "," options) class)
     (preamble env)
     "\\begin{document}"
     (frontmatter env)
     (mainmatter env)
     (catalog env)
     (impressum env)
     "\\end {document}"])))

(defn generate-latex []
  (let [items (->
               "/home/eglic/src/catalog/samples/vubis_export.xml"
               vubis/read-file
               vubis/order-and-group)]
    (spit temp-name (document
                     {:title "Neu im Sortiment"
                      :items items}))))

(defn generate-pdf []
  (shell/sh "latexmk" "-xelatex" temp-name :dir "/tmp"))
