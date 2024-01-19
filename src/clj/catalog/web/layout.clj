(ns catalog.web.layout
  "Define the basic page structure and layout"
  (:require [catalog.issue :as issue]
            [hiccup.page :refer [html5 include-css include-js]]
            [ring.util.http-response :as response]
            [reitit.core :as reitit]
            [trptcolin.versioneer.core :as version]))

(defn glyphicon
  ([class]
   (glyphicon class nil))
  ([class tooltip]
   [:span (into {} (list [:class (str "glyphicon glyphicon-" class)]
                         (when tooltip [:title tooltip])))]))

(defn button
  "Return a button. If the `href` is nil the button is disabled"
  [href & body]
  (if href
    [:a.btn.btn-default {:href href} body]
    [:button.btn.btn-default {:disabled "disabled"} body]))

(defn menu-item [href & body]
  [:li [:a {:href href} body]])

(defn button-group [buttons]
  [:div.btn-group
   (for [button buttons] button)])

(defn- dropdown-menu [title items]
  [:li.dropdown
   [:a.dropdown-toggle
    {:href "#" :role "button" :data-toggle "dropdown" :aria-expanded false} title [:span.caret]]
   [:ul.dropdown-menu {:role "menu"}
    (for [[link label] items]
      (menu-item link label))]])

(defn path-for [{::reitit/keys[router]} name args]
  (-> router
      (reitit/match-by-name name args)
      (reitit/match->path)))

(defn navbar
  "Display the navbar"
  [year issue]
  [:div.navbar.navbar-default {:role "navigation"}
   [:div.container-fluid
    [:div.navbar-header
     [:button.navbar-toggle
      {:type "button"
       :data-toggle "collapse" :data-target "#navbar-collapse-target"}
      [:span.sr-only "Toggle navigation"]
      [:span.icon-bar]
      [:span.icon-bar]
      [:span.icon-bar]]
     [:a.navbar-brand {:href (format "/%s/%s" year issue)} "Kati"]]
    [:div.collapse.navbar-collapse
     {:id "navbar-collapse-target"}
     [:ul.nav.navbar-nav
      (dropdown-menu "Upload" [[(format "/%s/%s/upload" year issue) "Neu im Sortiment"]
                               [(format "/%s/%s/upload-full" year issue) "Gesamtkatalog"]])
      (dropdown-menu "Editorials" [[(format "/%s/%s/editorial/grossdruck" year issue) "Grossdruck"]
                                   [(format "/%s/%s/editorial/braille" year issue) "Braille"]
                                   [(format "/%s/%s/editorial/hörbuch" year issue) "Hörbuch"]])
      (menu-item (format "/%s/%s/full" year issue) "Full")
      (menu-item (format "/%s/%s/custom" year issue) "Custom")
      ]
     [:div.nav.navbar-nav.navbar-right
      [:p.navbar-text (format "%s/%s" year issue)]
      (let [[year issue] (issue/prev-issue year issue)]
        [:a.btn.btn-default.btn-sm.navbar-btn {:href (format "/%s/%s" year issue)} (glyphicon "chevron-left" "Older catalogs")])
      [:a.btn.btn-default.btn-sm.navbar-btn {:href "/"} (glyphicon "stop" "Current catalogs")]
      (let [[year issue] (issue/next-issue year issue)]
        [:a.btn.btn-default.btn-sm.navbar-btn {:href (format "/%s/%s" year issue)} (glyphicon "chevron-right" "Newer catalogs")])]]]])

(defn common
  "Display a page using the bootstrap css"
  [year issue & body]
  (response/content-type
   (response/ok
    (html5
     [:head
      [:title "Catalog"]
      (let [group "ch.sbs" artifact "catalog"]
        [:meta {:name "application-version"
                :content (format "%s (%s)"
                                 (version/get-version group artifact)
                                 (version/get-revision group artifact))}])
      (include-css "/css/bootstrap.min.css")
      (include-css "/css/bootstrap-markdown.min.css")]
     [:body
      [:div.container
       (navbar year issue)
       body]
      (include-js "/js/jquery-1.12.0.min.js")
      (include-js "/js/bootstrap.min.js")
      (include-js "/js/markdown.min.js")
      (include-js "/js/bootstrap-markdown.js")]))
   "text/html; charset=utf-8"))

