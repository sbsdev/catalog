(ns catalog.routes.home
  (:require [catalog.layout :as layout]
            [catalog.middleware :as middleware]
            [catalog.web.views :as views]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [reitit.coercion.spec :as spec-coercion]
            [reitit.ring.coercion :as coercion]
            [ring.util.codec :refer [url-encode]]))

(defn home-page [request]
  (layout/render request "home.html" {:docs (-> "docs/docs.md" io/resource slurp)}))

(defn about-page [request]
  (layout/render request "about.html"))

(s/def ::year (s/and pos-int? #(<= 1900 % 2100)))
(s/def ::issue (s/and pos-int? #{1 2 3 4 5 6}))

(def hörbuch-encoded (url-encode "hörbuch"))

(defn home-routes []
  [ "" 
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/" {:get {:handler (fn [_] (views/home))}}]
   ["/:year/:issue"
    {:coercion spec-coercion/coercion
     :middleware [coercion/coerce-request-middleware]}
    
    ["" {:get {:parameters {:path {:year ::year :issue ::issue}}
               :handler (fn [{{{:keys [year issue]} :path} :parameters}]
                          (views/home year issue))}}]

    ;; Neu im Sortiment
    ["/neu-im-sortiment.pdf"
     {:get {:parameters {:path {:year ::year :issue ::issue}}
            :handler (fn [{{{:keys [year issue]} :path} :parameters}]
                       (views/neu-im-sortiment year issue))}}]

    ;; Neu in Grossdruck
    ["/neu-in-grossdruck.pdf"
     {:get {:parameters {:path {:year ::year :issue ::issue}}
            :handler (fn [{{{:keys [year issue]} :path} :parameters}]
                       (views/neu-in-grossdruck year issue))}}]

    ;; Neu in Braille
    ["/neu-in-braille.xml"
     {:get {:parameters {:path {:year ::year :issue ::issue}}
            :handler (fn [{{{:keys [year issue]} :path} :parameters}]
                       (views/neu-in-braille year issue))}}]

    ;; Neu als Hörbuch
    [(format "/neu-als-%s.pdf" hörbuch-encoded)
     {:get {:parameters {:path {:year ::year :issue ::issue}}
            :handler (fn [{{{:keys [year issue]} :path} :parameters}]
                       (views/neu-als-hörbuch year issue))}}]

    [(format "/neu-als-%s.ncc" hörbuch-encoded)
     {:get {:parameters {:path {:year ::year :issue ::issue}}
            :handler (fn [{{{:keys [year issue]} :path} :parameters}]
                       (views/neu-als-hörbuch-ncc year issue))}}]

    ;; Editorials
    ["/editorial"
     ["/grossdruck"
      {:get {:parameters {:path {:year ::year :issue ::issue}}
             :handler (fn [{{{:keys [year issue]} :path} :parameters :as r}]
                        (views/editorial-form r :grossdruck year issue))}}]
     ["/braille"
      {:get {:parameters {:path {:year ::year :issue ::issue}}
             :handler (fn [{{{:keys [year issue]} :path} :parameters :as r}]
                        (views/editorial-form r :braille year issue))}}]
     [(format "/%s" hörbuch-encoded)
      {:get {:parameters {:path {:year ::year :issue ::issue}}
             :handler (fn [{{{:keys [year issue]} :path} :parameters :as r}]
                        (views/editorial-form r :hörbuch year issue))}}]]
    
    ;; Upload catalog data
    ["/upload"
     {:get {:parameters {:path {:year ::year :issue ::issue}}
            :handler (fn [{{{:keys [year issue]} :path} :parameters :as r}]
                       (views/upload-form r year issue))}}]
    ["/upload-full"
     {:get {:parameters {:path {:year ::year :issue ::issue}}
            :handler (fn [{{{:keys [year issue]} :path} :parameters :as r}]
                       (views/upload-full-form r year issue))}}]

    ["/:fmt/upload-confirm"
     {:post {:parameters {:path {:year ::year :issue ::issue}
                          :query {:fmt keyword? :file any?}}
             :handler (fn [{{{:keys [year issue]} :path
                             {:keys [fmt file]} :query} :parameters :as r}]
                        (views/upload-confirm r year issue fmt file))}}]
    ["/:fmt/upload"
     {:post {:parameters {:path {:year ::year :issue ::issue}
                          :query {:fmt keyword? :items any?}}
             :handler (fn [{{{:keys [year issue]} :path
                             {:keys [fmt items]} :query} :parameters :as r}]
                        (views/upload r year issue fmt items))}}]
    
    ;; Full catalogs
    ["/full"
     {:get {:parameters {:path {:year ::year :issue ::issue}}
            :handler (fn [{{{:keys [year issue]} :path} :parameters :as r}]
                       (views/full-catalogs r year issue))}}]
    
    ;; Custom catalogs
    ["/custom"
     {:get {:parameters {:path {:year ::year :issue ::issue}}
            :handler (fn [{{{:keys [year issue]} :path} :parameters :as r}]
                       (views/custom-form r year issue))}
      :post {:parameters {:path {:year ::year :issue ::issue}
                          :query {:query string?
                                  :customer string?
                                  :fmt keyword?
                                  :items any?}}
             :handler (fn [{{{:keys [year issue]} :path
                             {:keys [query customer fmt items]} :query} :parameters :as r}]
                       (views/custom r year issue query customer fmt items))}}]
    ["/custom-confirm"
     {:get {:parameters {:path {:year ::year :issue ::issue}
                         :query {:query string?
                                 :customer string?
                                 :fmt keyword?
                                 :file any?}}
            :handler (fn [{{{:keys [year issue]} :path
                            {:keys [query customer fmt file]} :query} :parameters :as r}]
                       (views/custom-confirm r year issue query customer fmt file))}}]
    
    ]
   ["/:year/full"
    {:coercion spec-coercion/coercion
     :middleware [coercion/coerce-request-middleware]}
    [(format "/%s-in-der-sbs.pdf" (url-encode "hörfilme"))
     {:name ::hörfilme
      :get {:parameters {:path {:year ::year}}
            :handler (fn [{{{:keys [year]} :path} :parameters}]
                       (views/hörfilme year))}}]
    ["/spiele-in-der-sbs.pdf"
     {:name ::spiele
      :get {:parameters {:path {:year ::year}}
            :handler (fn [{{{:keys [year]} :path} :parameters}]
                       (views/spiele year))}}]
    [(format "/taktile-%s-der-sbs.pdf" (url-encode "kinderbücher"))
     {:name ::kinderbücher
      :get {:parameters {:path {:year ::year}}
            :handler (fn [{{{:keys [year]} :path} :parameters}]
                       (views/taktile-bücher year))}}]
    [(format "/print-und-braille-%s-in-der-sbs.pdf" (url-encode "bücher"))
     {:name ::print-and-braille-bücher
      :get {:parameters {:path {:year ::year}}
            :handler (fn [{{{:keys [year]} :path} :parameters}]
                       (views/print-and-braille-bücher year))}}]
    ]
   ])

