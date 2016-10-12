(defproject catalog "0.1.0-SNAPSHOT"
  :description "A webapp to generate catalogs of the new items in the library"
  :url "https://github.com/sbsdev/catalog"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/data.zip "0.1.2"]
                 [medley "0.8.3"]
                 [prismatic/schema "1.1.3"]
                 [clj-time "0.12.0"]
                 [comb "0.1.0" :exclusions [org.clojure/clojure]]
                 [org.clojure/data.xml "0.1.0-beta2"]
                 [org.apache.xmlgraphics/fop "2.1.1-SNAPSHOT" :exclusions [xalan commons-io commons-logging]]
                 [hiccup "1.0.5"]
                 [org.immutant/web "2.1.5"]
                 [ring/ring-defaults "0.2.1"]
                 [ring/ring-core "1.5.0"]
                 [ring/ring-devel "1.5.0"]
                 [com.cemerick/friend "0.2.3"]
                 [compojure "1.5.1"]
                 [endophile "0.1.2"]
                 [org.clojure/java.jdbc "0.6.1"]
                 [mysql/mysql-connector-java "6.0.4"]
                 [yesql "0.5.3"]
                 [org.tobereplaced/nio.file "0.4.0"]]
  :plugins [[cider/cider-nrepl "0.14.0-SNAPSHOT"]
;            [refactor-nrepl "2.3.0-SNAPSHOT"]
            [lein-immutant "2.1.0"]
            [lein-codox "0.10.0"]
            [org.clojars.cvillecsteele/lein-git-version "1.0.3"]]
  :codox {:project {:name "Kati"}
          :source-paths ["src"]
          :source-uri "https://github.com/sbsdev/catalog/blob/v{version}/{filepath}#L{line}"
          :metadata {:doc/format :markdown}}
  :main ^:skip-aot catalog.web.main
  :immutant {:war {:context-path "/"
                   :name "%p%v%t"
                   :nrepl {:port 40021
                           :start? true}}}
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:source-paths ["src" "dev"]}})
