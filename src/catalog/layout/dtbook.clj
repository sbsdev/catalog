(ns catalog.layout.dtbook
  "Render catalog items as [DTBook
  XML](http://www.daisy.org/z3986/2005/Z3986-2005.html) to be
  converted to Braille later in the tool chain"
  (:require [catalog.layout.common :as layout]
            [clj-time.core :as time.core]
            [clojure.data.xml :as xml]))

(def wrap (layout/wrapper ""))

(defn catalog-entry [{:keys [creator record-id title subtitle name-of-part source-publisher source-date
                        genre description producer-brief rucksackbuch? rucksackbuch-number
                        library-signature product-number price]}]
  [:div {:class "catalog-item"}
   [:p (wrap creator "" ": " false) (wrap title) (wrap subtitle) (wrap name-of-part) " - "
    (wrap source-publisher "" ", " false) (wrap (layout/year source-date))]
   [:p (wrap (layout/translations genre) "Genre: ")]
   [:p (wrap description)]
   (if rucksackbuch?
     [:p (wrap producer-brief "" ", " false) (wrap rucksackbuch-number "Rucksackbuch Nr. ")]
     [:p (wrap producer-brief)])
   [:p "Ausleihe: " (layout/braille-signatures library-signature)]
   [:p "Verkauf: " (wrap price "" ". " false) (layout/braille-signatures product-number)]])

(defn catalog-entries [items]
  [:div {:class "catalog-list"}
   (for [item items] (catalog-entry item))])

(defn subgenre-entry [subgenre items]
  (when-let [items (subgenre items)]
    [:level3
     [:h3 (layout/translations subgenre "FIXME")]
     (catalog-entries items)]))

(defn subgenre-entries [items]
  (for [subgenre layout/subgenres] (subgenre-entry subgenre items)))

(defn genre-entries [genre items]
  (when-let [items (genre items)]
    [:level2
     [:h2 (layout/translations genre "FIXME")]
     (cond
       (#{:kinder-und-jugendbücher} genre) (subgenre-entries items)
       :else (catalog-entries items))]))

(defn document [title items &
                {:keys [creator volume date language]
                 :or {creator "SBS Schweizerische Bibliothek für Blinde, Seh- und Lesebehinderte"
                      volume (layout/volume-number (time.core/today))
                      date (layout/format-date (time.core/now))
                      language "de"}}]
  [:dtbook {:xmlns "http://www.daisy.org/z3986/2005/dtbook/"
            :version "2005-3" :xml:lang language}
   [:head
    (for [[k v]
          {:dc:Title title
           :dc:Creator creator
           :dc:Publisher creator
           :dc:Date "FIXME"
           :dc:Format "ANSI/NISO Z39.86-2005"
           :dc:Language language}]
      [:meta {:name (name k) :content v}])
    [:book
     [:frontmatter
      [:doctitle title]
      [:docauthor creator]]
     [:bodymatter
      [:level1 [:h1]
       [:p {:class "Heftnummer"} volume]
       [:p {:class "Datum"} date]]
      [:level1 [:h1 "Katalog"]
       (for [genre layout/braille-genres] (genre-entries genre items))]
      [:level1 [:h1 "Impressum"]
       [:level2 [:h2 title]
        [:p "Für Kundinnen und Kunden der SBS sowie für Interessenten"]
        [:p "Erscheint kostenlos sechsmal jährlich und listet alle seit der letzten Ausgabe neu in die SBS aufgenommenen Braillebücher auf"]
        [:p (str "Herausgeber: " creator)]
        [:p "Abonnement: medienverlag@sbs.ch"]]
       [:level2 [:h2 "Ausleihe und Verkauf"]
        [:p creator]
        [:p "Grubenstrasse 12"]
        [:p "CH-8045 Zürich"]
        [:p "Fon +41 43 333 32 32"]
        [:p "Fax +41 43 333 32 33"]
        [:p "www.sbs.ch"]
        [:p "Ausleihe/Verkauf Privatpersonen: nutzerservice@sbs.ch"]
        [:p "Verkauf Institutionen: medienverlag@sbs.ch"]]]
      [:level1 [:h1 "Einführungsrabatt"]
       [:p "Die in dieser Ausgabe vorgestellten Rucksackbücher können während zweier Monate mit einem Einführungsrabatt von 20% (ringgebunden ohne Deckel) bestellt werden. Danach gilt der in diesem Heft angegebene Buchhandelspreis (ringgebunden mit Deckel)."]]
      [:level1 [:h1 "Abkürzungen"]
       [:p
        [:list {:type "pl"}
         [:li "Bd. = Band, Bände"]
         [:li "K = Kurzschrift"]
         [:li "V = Vollschrift"]
         [:li "wtz = weitzeilige Vollschrift"]]]]]]]])

(defn dtbook
  [items]
  (-> (document "Neu in Braille" items)
      xml/sexp-as-element
      xml/indent-str))
