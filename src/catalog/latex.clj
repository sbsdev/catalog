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
      (->
       text
       ;; backslash
       (string/replace #"\\" "\\\\textbackslash ")
       ;; drop excessive white space
       (string/replace #"\s+" " ")
       ;; quote special chars
       (string/replace #"(\$|&|%|#|_|\{|\})" "\\\\$1")
       ;; append a '{}' to special chars so they are not missinterpreted
       (string/replace #"(~|\^)" "$1{}")
       ;; add non-breaking space in front of emdash or endash followed by
       ;; punctuation
       (string/replace #" ([–—]\p{P})" " $1")
       ;; add non-breaking space in front ellipsis followed by punctuation
       (string/replace #" ((\.{3}|…)\p{P})" " $1")
       ;; [ and ] can sometimes be interpreted as the start or the end of
       ;; an optional argument
       (string/replace #"\[" "\\\\lbrack{}")
       (string/replace #"\]" "\\\\rbrack{}")
       ;; << and >> is apparently treated as a shorthand and is not
       ;; handled by our shorthand disabling code
       (string/replace #"<<" "{<}<")
       (string/replace #">>" "{>}>"))))

(defn preamble [{:keys [title font creator] :or {font "Verdana"}}]
  ["\\isopage[12]"
   "\\fixpdflayout"
   "\\checkandfixthelayout"
   "\\usepackage{graphicx}"
   "\\usepackage[ngerman]{babel}"
   "\\def\\languageshorthands#1{}"
   "\\usepackage{fontspec,xunicode,xltxtra}"
   "\\defaultfontfeatures{Mapping=tex-text}"
   (str "\\setmainfont{" font "}")
   "\\usepackage{hyperref}"
   (str "\\hypersetup{pdftitle={" (escape title) "}, pdfauthor={" (escape creator) "}}")
   "\\setsecnumdepth{subsubsection}"
   "\\setlength{\\parindent}{0pt}"])

(defn frontmatter [{:keys [title issue-number year]}]
  ["\\frontmatter"
   "\\begin{Spacing}{1.75}"
   (str  "{\\huge " (escape title) "}\\\\[0.5cm]")
   "\\end{Spacing}"
   "{\\large Ausgabenummer und Jahr}\\\\[1.5cm]"
   "\\vfill"
   "Logo\\\\ "
   "SBS Schweizerische Bibliothek für Blinde, Seh- und Lesebehinderte\\\\[0.5cm]"
   "\\cleartorecto"])

(defn mainmatter [env]
  ["\\mainmatter"
   ;; "\\pagestyle{plain}"
   "\\tableofcontents"])

(defmulti catalog-entry (fn [{format :format}] format))

(defmethod catalog-entry :hörfilm
  [{:keys [title personel-name description source_publisher source_date genre library_signature]}]
  ["\\begin{description}"
   (string/join " "
                [(format "\\item[%s]" (escape title))
                 (format "Regie: %s" (escape personel-name))
                 (escape source_date)
                 (translations genre) "\\\\"
                 (escape description)
                 (escape library_signature)])
   "\\end{description}"])

(defmethod catalog-entry :hörbuch
  [{:keys [title creator description source_publisher source_date genre duration narrator
           producer producer_place produced_commercially
           library_signature]}]
  ["\\begin{description}"
   (string/join " "
                [(format "\\item[%s]" (escape title))
                 (escape creator)
                 (escape source_publisher)
                 (escape source_date)
                 (translations genre) "\\\\"
                 (escape description) "\\\\"
                 (escape duration)
                 (format "gelesen von: %s" (escape narrator))
                 (escape producer) (escape producer_place)
                 (escape produced_commercially)
                 (escape library_signature)])
   "\\end{description}"])

(defmethod catalog-entry :default
  [{:keys [title creator description source_publisher source_date genre duration]}]
  ["\\begin{description}"
   (string/join " "
                [(format "\\item[%s]" (escape title))
                 (escape creator)
                 (escape source_publisher)
                 (escape source_date)
                 (translations genre) "\\\\"
                 (escape description)
                 "Spieldauer"
                 (escape duration)])
   "\\end{description}"])

(defn catalog-entries [items]
  (for [item items] (catalog-entry item)))

(defn subgenre-entry [subgenre items]
  (when (subgenre items)
    [(str "\\subsubsection{" (translations subgenre "FIXME")"}")
     (catalog-entries (subgenre items))]))

(defn subgenre-entries [items]
  (for [subgenre subgenres] (subgenre-entry subgenre items)))

(defn genre-entry [format genre items]
  (when (genre items)
    [(str "\\subsection{" (translations genre "FIXME") "}")
     (println genre (-> items genre first))
     (cond
       (#{:kinder-und-jugendbücher} genre) (subgenre-entries (genre items))
       (#{:hörbuch} format) (subgenre-entries (genre items))
       :else (catalog-entries (genre items)))]))

(defn genre-entries [format items]
  (for [genre genres] (genre-entry format genre items)))

(defn format-entry [format items]
  (when (format items)
    [(str "\\section{" (translations format "FIXME") "}")
     (case format
       (:hörfilm :ludo) (catalog-entries (format items))
       (genre-entries format (format items)))]))

(defn format-entries [items]
  (for [format formats] (format-entry format items)))

(defn catalog [{items :items}]
  ["\\chapter{Katalog}"
   (format-entries items)])

(defn impressum [env]
   ["\\chapter{Impressum}"
   ""
   "Neu im Sortiment"
   ""
   "Für Kundinnen und Kunden der SBS sowie für Interessenten"
   ""
   "Erscheint sechsmal jährlich und weist alle seit der letzten Ausgabe neu in die SBS aufgenommenen Bücher nach"
   ""
   "Neu im Sortiment kann im Jahresabonnement per Post zu CHF XX oder per E-Mail gratis bezogen werden."
   ""
   "Herausgeber:\\\\ "
   "SBS Schweizerische Bibliothek für Blinde, Seh- und Lesebehinderte\\\\ "
   "Grubenstrasse 12\\\\ "
   "CH-8045 Zürich\\\\ "
   "Fon +41 43 333 32 32\\\\ "
   "Fax +41 43 333 32 33\\\\ "
   "www.sbs.ch"
   ""
   "Abonnement, Ausleihe und Verkauf: nutzerservice@sbs.ch\\\\ "
   "Verkauf Institutionen: medienverlag@sbs.ch"
   ""
   "Copyright Vermerk"])

(defn document [{:keys [class options]
                 :or {class "memoir"
                      options #{"11pt" "a4paper" "oneside" "openright"}}
                 :as env}]
  (string/join "\n"
   (flatten
    [(str "\\documentclass[" (string/join "," options) "]{" class "}")
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
