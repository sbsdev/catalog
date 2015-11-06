(defproject catalog "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "https://github.com/sbsdev/catalog"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/data.zip "0.1.1"]
                 [medley "0.7.0"]
                 [prismatic/schema "1.0.3"]
                 [clj-time "0.11.0"]
                 [comb "0.1.0" :exclusions [org.clojure/clojure]]]
  :plugins [[cider/cider-nrepl "0.10.0-SNAPSHOT"]
            [refactor-nrepl "2.0.0-SNAPSHOT"]]
  :main ^:skip-aot catalog.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
