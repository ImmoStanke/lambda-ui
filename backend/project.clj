(defproject lambdaui "0.1.0-SNAPSHOT"
  :description "LambdaCD-Plugin that provides a modern UI for your pipeline."
  :url "https://github.com/sroidl/lambda-ui"
  :license {:name "Apache License 2.0"
            :url  "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [lambdacd "0.9.3"]
                 [compojure "1.5.0"]
                 [http-kit "2.1.18"]
                 [org.clojure/data.json "0.2.6"]
                 [org.slf4j/slf4j-simple "1.7.22"]
                 [trptcolin/versioneer "0.2.0"]
                 ]
  :test-paths ["test"]
  :repositories [["snapshots" { :url "https://clojars.org/repo"
                                :username "sroidl"
                                :password [:gpg :env]}]
                 ["releases" { :url "https://clojars.org/repo"
                                               :username "sroidl"
                                               :password [:gpg :env]}]]
  :profiles {:dev {:dependencies [[lambdacd-git "0.1.2"]
                                  [com.gearswithingears/shrubbery "0.4.1"]]
                   :aot          [lambdaui.testpipeline.core]
                   :main         lambdaui.testpipeline.core}})
