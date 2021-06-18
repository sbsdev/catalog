(ns catalog.routes.home
  (:require [catalog.layout :as layout]
            [catalog.middleware :as middleware]
            [catalog.web.views :as views]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [reitit.coercion.spec :as spec-coercion]
            [reitit.ring.middleware.multipart :as multipart]
            [reitit.ring.coercion :as coercion]
            [ring.util.codec :refer [url-encode]]))

(s/def ::year (s/and pos-int? #(<= 1900 % 2100)))
(s/def ::issue (s/and pos-int? #{1 2 3 4 5 6}))
(s/def ::format (s/and string? #{"grossdruck" "braille" "hörbuch"}))

(def hörbuch-encoded (url-encode "hörbuch"))

(defn home-routes []
  [ "" 
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/" {:get {:handler (fn [_] (views/home))}}]
   ["/:year/:issue"
    {:coercion spec-coercion/coercion
     :middleware [coercion/coerce-request-middleware
                  multipart/multipart-middleware]}
    
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
    ["/editorial/:fmt"
      {:get {:parameters {:path {:year ::year :issue ::issue :fmt ::format}}
             :handler (fn [{{{:keys [year issue fmt]} :path} :parameters :as r}]
                        (views/editorial-form r fmt year issue))}}]
    
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
     {:post {:parameters {:path {:year ::year :issue ::issue :fmt keyword?}
                          :multipart {:file any?}}
             :handler (fn [{{{:keys [year issue fmt]} :path
                             {:keys [file]} :multipart} :parameters :as r}]
                        (views/upload-confirm r year issue fmt file))}}]
    ["/:fmt/upload"
     {:post {:parameters {:path {:year ::year :issue ::issue :fmt keyword?}
                          :multipart {:items any?}}
             :handler (fn [{{{:keys [year issue fmt]} :path
                             {:keys [items]} :multipart} :parameters :as r}]
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
                          :multipart {:query string?
                                      :customer string?
                                      :fmt keyword?
                                      :items any?}}
             :handler (fn [{{{:keys [year issue]} :path
                             {:keys [query customer fmt items]} :multipart} :parameters :as r}]
                        (views/custom r year issue query customer fmt items))}}]
    ["/custom-confirm"
     {:post {:parameters {:path {:year ::year :issue ::issue}
                          :multipart {:query string?
                                      :customer string?
                                      :fmt keyword?
                                      :file any?}}
             :handler (fn [{{{:keys [year issue]} :path
                             {:keys [query customer fmt file]} :multipart} :parameters :as r}]
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

