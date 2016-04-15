(defproject catalog "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "https://github.com/sbsdev/catalog"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/data.zip "0.1.1"]
                 [medley "0.7.4"]
                 [prismatic/schema "1.1.0"]
                 [clj-time "0.11.0"]
                 [comb "0.1.0" :exclusions [org.clojure/clojure]]
                 [org.clojure/data.xml "0.0.8"]
                 [org.apache.xmlgraphics/fop "2.1"]
                 [hiccup "1.0.5"]
                 [org.immutant/web "2.1.3"]
                 [ring/ring-defaults "0.2.0"]
                 [ring/ring-core "1.4.0"]
                 [ring/ring-devel "1.4.0"]
                 [com.cemerick/friend "0.2.1"]
                 [compojure "1.5.0"]
                 [endophile "0.1.2"]
                 [org.clojure/java.jdbc "0.5.8"]
                 [mysql/mysql-connector-java "6.0.2"]
                 [ragtime "0.5.3"]]
  :plugins [[cider/cider-nrepl "0.12.0-SNAPSHOT"]
            [refactor-nrepl "2.3.0-SNAPSHOT"]
            [org.clojars.cvillecsteele/lein-git-version "1.0.3"]]
  :main ^:skip-aot catalog.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:source-paths ["src" "dev"]}})
