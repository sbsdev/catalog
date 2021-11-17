(defproject ch.sbs/catalog "0.36.0-SNAPSHOT"

  :description "A webapp to generate catalogs of the new items in the library"
  :url "https://github.com/sbsdev/catalog"
  :license {:name "GNU Affero General Public License"
            :url "https://www.gnu.org/licenses/agpl.html"}

  :dependencies [[babashka/fs "0.1.1"]
                 [ch.qos.logback/logback-classic "1.2.7"]
                 [clj-commons/iapetos "0.1.12"]
                 [clojure.java-time "0.3.3"]
                 [com.google.protobuf/protobuf-java "3.19.1"]
                 [conman "0.9.1" :exclusions [org.clojure/tools.reader]]
                 [cprop "0.1.19"]
                 [endophile "0.2.1"]
                 [expound "0.8.10"]
                 [hiccup "1.0.5"]
                 [io.prometheus/simpleclient_hotspot "0.12.0"]
                 [json-html "0.4.7"]
                 [luminus-migrations "0.7.1"]
                 [luminus-transit "0.1.2" :exclusions [com.cognitect/transit-clj]]
                 [luminus-undertow "0.1.12"]
                 [luminus/ring-ttl-session "0.3.3"]
                 [markdown-clj "1.10.6"]
                 [medley "1.3.0"]
                 [metosin/muuntaja "0.6.8" :exclusions [metosin/jsonista]]
                 [metosin/reitit "0.5.15"]
                 [metosin/ring-http-response "0.9.3"]
                 [mount "0.1.16"]
                 [mysql/mysql-connector-java "8.0.27"]
                 [nrepl "0.8.3"]
                 [org.apache.xmlgraphics/fop "2.4" :exclusions [xalan commons-io]]
                 [org.clojure/clojure "1.10.3"]
                 [org.clojure/data.xml "0.1.0-beta2"]
                 [org.clojure/data.zip "1.0.0"]
                 [org.clojure/tools.cli "1.0.206"]
                 [org.clojure/tools.logging "1.1.0"]
                 [org.webjars.npm/bulma "0.9.3"]
                 [org.webjars.npm/material-icons "1.7.1"]
                 [org.webjars/webjars-locator "0.41"]
                 [org.webjars/webjars-locator-jboss-vfs "0.1.0"]
                 [prismatic/schema "1.1.12"]
                 [ring-webjars "0.2.0"]
                 [ring/ring-core "1.9.4"]
                 [ring/ring-defaults "0.3.3"]
                 [selmer "1.12.44"]
                 [trptcolin/versioneer "0.2.0"]]

  :min-lein-version "2.0.0"

  :source-paths ["src/clj"]
  :test-paths ["test/clj"]
  :resource-paths ["resources"]
  :target-path "target/%s/"
  :main ^:skip-aot catalog.core

  :plugins [[lein-kibit "0.1.8"]
            [lein-codox "0.10.8"]]

  :codox {:project {:name "Kati"}
          :source-paths ["src"]
          :source-uri "https://github.com/sbsdev/catalog/blob/{version}/{filepath}#L{line}"
          :metadata {:doc/format :markdown}}

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  #_["deploy"]
                  #_["uberjar"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]

  :profiles
  {:uberjar {:omit-source true
             :aot :all
             :uberjar-name "catalog.jar"
             :source-paths ["env/prod/clj" ]
             :resource-paths ["env/prod/resources"]}

   :dev           [:project/dev :profiles/dev]
   :test          [:project/dev :project/test :profiles/test]

   :project/dev  {:jvm-opts ["-Dconf=dev-config.edn" ]
                  :dependencies [[pjstadig/humane-test-output "0.11.0"]
                                 [prone "2021-04-23"]
                                 [ring/ring-devel "1.9.4"]
                                 [ring/ring-mock "0.4.0"]]
                  :plugins      [[com.jakemccrary/lein-test-refresh "0.25.0"]
                                 [jonase/eastwood "0.9.9"]]

                  :source-paths ["env/dev/clj" ]
                  :resource-paths ["env/dev/resources"]
                  :repl-options {:init-ns user
                                 :timeout 120000}
                  :injections [(require 'pjstadig.humane-test-output)
                               (pjstadig.humane-test-output/activate!)]}
   :project/test {:jvm-opts ["-Dconf=test-config.edn" ]
                  :resource-paths ["env/test/resources"] }
   :profiles/dev {}
   :profiles/test {}})
