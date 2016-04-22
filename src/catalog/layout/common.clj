(ns catalog.layout.common
  (:require [clj-time
             [coerce :as time.coerce]
             [core :as time.core]
             [format :as time.format]]
            [clojure.string :as string]
            [endophile.core :as endophile])
  (:import java.util.Locale))

(def formats [:hörbuch :braille :grossdruck :e-book :hörfilm :ludo])
(def genres [:belletristik :sachbücher :kinder-und-jugendbücher])
(def movie-genres [:spielfilm :mundartfilm :dokumentarfilm])
(def game-genres [:lernspiel :solitairspiel :denkspiel :geschicklichkeitsspiel
                  :kartenspiel :legespiel :rollenspiel :würfelspiel :ratespiel
                  :bücher-über-spiel])
(def braille-genres (conj genres :musiknoten :taktilesbuch))
(def subgenres [:action-und-thriller :beziehungsromane :fantasy-science-fiction
                :gesellschaftsromane :glaube-und-philosophie
                :historische-romane :hörspiele :krimis
                :lebensgeschichten-und-schicksale :literarische-gattungen
                :literatur-in-fremdsprachen :mundart-heimat-natur
                :biografien :freizeit-haus-garten :geschichte-und-gegenwart
                :kunst-kultur-medien :lebensgestaltung-gesundheit-erziehung
                :philosophie-religion-esoterik :reisen-natur-tiere :sprache
                :wissenschaft-technik
                :kinderbücher-ab-6 :kinderbücher-ab-10 :jugendbücher :kinder-und-jugendsachbücher])

(def ^:dynamic translations {:inhalt "Inhaltsverzeichnis"
                             :sbs "SBS Schweizerische Bibliothek für Blinde, Seh- und Lesebehinderte"
                             ;; Names of catalogs
                             :catalog-all "Neu im Sortiment"
                             :catalog-hörbuch "Neu als Hörbuch"
                             :catalog-braille "Neu in Braille"
                             :catalog-grossdruck "Neu in Grossdruck"
                             ;; Names of formats
                             :hörbuch "Neue Hörbücher"
                             :braille "Neue Braillebücher"
                             :grossdruck "Neue Grossdruckbücher"
                             :e-book "Neue E-Books"
                             :hörfilm "Neue Hörfilme"
                             :ludo "Neue Spiele"
                             ;; Names for genres
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
                             :kinder-und-jugendsachbücher "Kinder- und Jugendsachbücher"
                             :kinderbücher-ab-6 "Kinderbücher (ab 6)"
                             :kinderbücher-ab-10 "Kinderbücher (ab 10)"
                             :spielfilm "Spielfilme"
                             :mundartfilm "Mundartfilme"
                             :dokumentarfilm "Dokumentarfilme"
                             :jugendbücher "Jugendbücher (ab 14)"
                             :musiknoten "Braille-Musiknoten"
                             :taktilesbuch "Taktile Bücher"
                             :spiel "Spiele"
                             :lernspiel "Lernspiele"
                             :solitairspiel "Solitairspiele"
                             :denkspiel "Denkspiele"
                             :geschicklichkeitsspiel "Geschicklichkeitsspiele"
                             :kartenspiel "Kartenspiele"
                             :legespiel "Legespiele"
                             :rollenspiel "Rollenspiele"
                             :würfelspiel "Würfelspiele"
                             :ratespiel "Ratespiele"
                             :bücher-über-spiel "Bücher über Spiele"
                             ;; Names for braille grades
                             [:kurzschrift false] "Kurzschrift"
                             [:vollschrift false] "Vollschrift"
                             [:schachschrift false] "Schachschrift"
                             [:kurzschrift true] "Weitzeilige Kurzschrift"
                             [:vollschrift true] "Weitzeilige Vollschrift"
                             [:schachschrift true] "Weitzeilige Schachschrift"
                             ;; other strings
                             :editorial "Editorial"
                             :recommendation "Buchtipp"
                             :recommendations "Buchtipps"})

(defn volume-number [date]
  (let [month (time.core/month (time.coerce/from-date date))]
    (quot (inc month) 2)))

(defn format-date [date]
  (time.format/unparse
   (time.format/with-locale (time.format/formatter "MMMM yyyy") Locale/GERMAN)
   (time.coerce/from-date date)))

(defn year [date]
  (when date
    (time.core/year (time.coerce/from-date date))))

(defn periodify
  "Add a period to the end of `s` if it is not nil and doesn't end in
  punctuation. If `s` is nil return an empty string."
  [s]
  (cond
    (nil? s) ""
    (number? s) (str s ".")
    (re-find #"[.,:!?]$" s) s
    (string/blank? s) s
    :else (str s ".")))

(defn- insert-zero-width-space
  "Add a zero with space after each sequence of white space in `s`.
  This seems to improve the FOP output for screen readers"
  [s]
  (string/replace s #"(\S)\s+(\S)" "$1 ​$2"))

(defn wrap
  "Adds a period to `s` and wrap it in `prefix` and `postfix`. If `s`
  is a sequence the contained strings are joined with \", \" as a
  separator"
  ([s]
   (wrap s ""))
  ([s prefix]
   (wrap s prefix ""))
  ([s prefix postfix]
   (wrap s prefix postfix true))
  ([s prefix postfix period?]
   (if s
     (let [s (cond->> s
               (seq? s) (string/join ", ")
               true insert-zero-width-space
               period? periodify)]
       (str prefix s postfix))
     "")))

(defn empty-or-blank? [s]
  ;; FIXME: might not be needed with clojure 1.8
  (let [safe-blank? (fn [s] (and (string? s)
                                 (string/blank? s)))]
    (or (nil? s)
        (and (coll? s)
             (or (empty? s) (every? safe-blank? s)))
        (safe-blank? s)
        false)))

(defn- braille-signature [[signature grade volumes double-spaced? accompanying-material :as item]]
  (when item
    (->>
     [(wrap (translations [grade (boolean double-spaced?)]) "" "" false)
      (wrap volumes "" " Bd." false)
      (wrap accompanying-material "" "" false)
      signature]
     (remove string/blank?)
     (string/join ", "))))

(defn braille-signatures [items]
  (->>
   [[:kurzschrift false] [:vollschrift false] [:kurzschrift true] [:vollschrift true]
    [:schachschrift true] [:schachschrift false] [nil true] [nil false]]
   (keep (fn [k] (braille-signature (first (get items k)))))
   (string/join ". ")))

(defn render-subtitles [subtitles]
  (string/join " " (map periodify subtitles)))

(defn- branch? [node]
  (map? node))

(defn- mapcat-indexed [f coll]
  (apply concat (map-indexed f coll)))

(defn md-extract-headings [markdown]
  (->>
   markdown
   endophile/mp
   endophile/to-clj
   (filter #(#{:h1 :h2 :h3 :h4} (:tag %)))
   (map (comp string/join :content))))

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

(defn path-to-number
  "Return a map of path to number vector mapping. This can be used to
  look up a section number for a given path."
  [tree]
  (apply hash-map (tree-walk tree [] [])))

(defn section-numbers [numbers]
  (when (not-empty numbers)
    (str (string/join "." numbers) ". ")))
