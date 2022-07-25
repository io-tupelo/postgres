(defproject io-tupelo/postgres "0.1.0-SNAPSHOT"
  :dependencies [
                 [metosin/jsonista "0.3.6"]
                 [org.clojure/clojure "1.11.1"]
                 [org.postgresql/postgresql "42.4.0"] ; see https://mvnrepository.com/artifact/org.postgresql/postgresql for info
                 [prismatic/schema "1.3.0"]
                 [seancorfield/next.jdbc "1.2.659"]
                 [tupelo "22.07.04"]
                 ]
  :plugins [
            [com.jakemccrary/lein-test-refresh "0.25.0"]
            [lein-ancient "0.7.0"]
            ]

  :global-vars {*warn-on-reflection* false}
  :main ^:skip-aot demo.core

  :source-paths          ["src/clj"]
  :java-source-paths     ["src/java"]
  :test-paths            ["test/clj"]
  :target-path           "target/%s"
  :compile-path          "%s/class-files"
  :clean-targets         [:target-path]

  :profiles {:dev     {:dependencies []}
             :uberjar {:aot :all}}

  :jvm-opts ["-Xms500m" "-Xmx2g"]
  )
