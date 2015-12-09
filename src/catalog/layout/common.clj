(ns catalog.layout.common
  (:require [clj-time
             [coerce :as time.coerce]
             [format :as time.format]]
            [clojure.string :as string]
            [clojure
             [string :as string]]
            [clojure
             [string :as string]])
  (:import java.util.Locale))

(def genres [:belletristik :sachbücher :kinder-und-jugendbücher])
(def braille-genres (conj genres :musiknoten :taktilesbuch))
(def subgenres [:action-und-thriller :beziehungsromane :fantasy-science-fiction
                :gesellschaftsromane :historische-romane :hörspiele :krimis
                :lebensgeschichten-und-schicksale :literarische-gattungen
                :literatur-in-fremdsprachen :mundart-heimat-natur :glaube-und-philosophie
                :biografien :freizeit-haus-garten :geschichte-und-gegenwart
                :kunst-kultur-medien :lebensgestaltung-gesundheit-erziehung
                :philosophie-religion-esoterik :reisen-natur-tiere :sprache
                :wissenschaft-technik
                :kinderbücher-ab-6 :kinderbücher-ab-10 :jugendbücher :kinder-und-jugendsachbücher])

(def ^:dynamic translations {:inhalt "Inhaltsverzeichnis"
                             :hörbuch "Neue Hörbücher"
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
                             :kinderbücher-ab-6 "Kinderbücher (ab 6)"
                             :musiknoten "Braille-Musiknoten"
                             :taktilesbuch "Taktile Bücher"
                             :spiel "Spiel"
                             [:kurzschrift false] "Kurzschrift"
                             [:vollschrift false] "Vollschrift"
                             [:kurzschrift true] "Weitzeilige Kurzschrift"
                             [:vollschrift true] "Weitzeilige Vollschrift"})

(defn volume-number [date]
  (let [month (.getMonthOfYear date)]
    (quot (inc month) 2)))

(defn format-date [date]
  (time.format/unparse
   (time.format/with-locale (time.format/formatter "MMMM yyyy") Locale/GERMAN)
   date))

(defn year [date]
  (when date
    (time.format/unparse
     (time.format/formatters :year) (time.coerce/from-date date))))

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

(defn wrapper
  "Return a function that adds a period to `s` and wrap it in `prefix`
  and `postfix`. If `s` is a sequence the contained strings are joined
  with \", \" as a separator"
  [postfix]
  (fn wrap
    ([s]
     (wrap s ""))
    ([s prefix]
     (wrap s prefix postfix))
    ([s prefix postfix]
     (wrap s prefix postfix true))
    ([s prefix postfix period?]
     (if s
       (let [s (if (seq? s) (string/join ", " s) s)]
         (str prefix (if period? (periodify s) s) postfix))
       ""))))

(def wrap (wrapper " \\\\ "))

(defn- braille-signature [[signature grade volumes double-spaced? :as item]]
  (when item
    (->>
     [(wrap (translations [grade double-spaced?]) "" "" false)
      (wrap volumes "" " Bd." false)
      signature]
     (remove string/blank?)
     (string/join ", "))))

(defn braille-signatures [items]
  (->>
   [[:kurzschrift false] [:vollschrift false] [:kurzschrift true] [:vollschrift true]
    [nil true] [nil false]]
   (keep (fn [k] (braille-signature (first (get items k)))))
   (string/join ". ")))

(def formats [:hörbuch :braille :grossdruck :e-book :hörfilm :ludo])

(defn render-subtitles [subtitles]
  (periodify (string/join ". " subtitles)))
