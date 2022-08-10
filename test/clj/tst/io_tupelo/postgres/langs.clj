(ns tst.io-tupelo.postgres.langs
  (:use tupelo.core tupelo.test)
  (:require
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]
    [next.jdbc.sql :as sql]
    [tupelo.profile :as prof]
    ))

;---------------------------------------------------------------------------------------------------
; NOTE: ***** Must MANUALLY  create DB 'acme_v01' before run this test! *****
;       In PSQL, do `create database acme_v01;`.  See the README.

;---------------------------------------------------------------------------------------------------
(def db-info {:dbtype   "postgres"
              :dbname   "acme_v01"
              :user     "postgres"
              :password "docker"
              })

(def conn (jdbc/get-datasource db-info)) ; returns an inner class: "next.jdbc.connection$..."

;---------------------------------------------------------------------------------------------------
(verify
  (prof/with-timer-print :langs

    ; creates & drops a connection (& transaction) for each command
    (jdbc/execute-one! conn ["drop table if exists langs"])
    (jdbc/execute-one! conn ["drop table if exists releases"])
    (throws? (sql/query conn ["select * from langs"])) ; table does not exist

    ; Creates and uses a connection for all commands
    (with-open [conn (jdbc/get-connection conn)]
      (jdbc/execute-one! conn ["
        create table langs (
          id      serial,
          lang    text not null
        ) "])

      ; NOTE: Postgres reserves 'desc' for 'descending' (unlike H2), so must use 'description' here
      (jdbc/execute-one! conn ["
        create table releases (
          id              serial,
          description     text not null,
          langId          numeric
        ) "]))
    (is= [] (sql/query conn ["select * from langs"])) ; table exists and is empty

    ; uses one connection in a transaction for all commands
    (jdbc/with-transaction [tx conn]
      (let [tx-opts (jdbc/with-options tx {:builder-fn rs/as-lower-maps})]
        (is= #:langs{:id 1, :lang "Clojure"}
          (sql/insert! tx-opts :langs {:lang "Clojure"}))
        (sql/insert! tx-opts :langs {:lang "Java"})

        (is= (sql/query tx-opts ["select * from langs"])
          [#:langs{:id 1, :lang "Clojure"}
           #:langs{:id 2, :lang "Java"}])))

    ; uses one connection in a transaction for all commands
    (prof/with-timer-print :langs-tx
      (jdbc/with-transaction [tx conn]
        (let [tx-opts (jdbc/with-options tx {:builder-fn rs/as-lower-maps})]
          (let [clj-id (grab :langs/id (only (sql/query tx-opts ["select id from langs where lang='Clojure'"])))] ; all 1 string
            (is= 1 clj-id)
            (sql/insert-multi! tx-opts :releases
              [:description :langId]
              [["ancients" clj-id]
               ["1.8" clj-id]
               ["1.9" clj-id]]))
          (let [java-id (grab :langs/id (only (sql/query tx-opts ["select id from langs where lang=?" "Java"])))] ; with query param
            (is= 2 java-id)
            (sql/insert-multi! tx-opts :releases
              [:description :langId]
              [["dusty" java-id]
               ["8" java-id]
               ["9" java-id]
               ["10" java-id]]))

          ; Note that `numeric` langid field becomes BigDecimal in Clojure,
          ; and all result keywords have table name as namespace like :releases/langid
          ; Also note that `rs/as-lower-maps` options makes :langid result all lowercase
          (is-set= (sql/query tx-opts ["select * from releases"])
            [#:releases{:description "ancients", :id 1, :langid 1M}
             #:releases{:description "1.8", :id 2, :langid 1M}
             #:releases{:description "1.9", :id 3, :langid 1M}
             #:releases{:description "dusty", :id 4, :langid 2M}
             #:releases{:description "8", :id 5, :langid 2M}
             #:releases{:description "9", :id 6, :langid 2M}
             #:releases{:description "10", :id 7, :langid 2M}])

          (let [; note cannot wrap select list in parens or get "bulk" output
                result-0 (sql/query tx-opts ["select  langs.lang, releases.description
                                              from    langs join releases
                                              on      (langs.id = releases.langId)
                                              where   (lang = 'Clojure') "])
                result-1 (sql/query tx-opts ["select  l.lang, r.description
                                              from    langs as l
                                                      join releases as r
                                              on      (l.id = r.langId)
                                              where   (l.lang = 'Clojure') "])
                result-2 (sql/query tx-opts ["select  langs.lang, releases.description
                                              from    langs, releases
                                              where   ( (langs.id = releases.langId)
                                                and     (lang = 'Clojure') ) "])
                result-3 (sql/query tx-opts ["select  l.lang, r.description
                                              from    langs as l, releases as r
                                              where   ( (l.id = r.langId)
                                                and     (l.lang = 'Clojure') ) "])
                ]
            (let [expected [{:langs/lang "Clojure", :releases/description "ancients"}
                            {:langs/lang "Clojure", :releases/description "1.8"}
                            {:langs/lang "Clojure", :releases/description "1.9"}]]
              (is-set= result-0 expected)
              (is-set= result-1 expected)
              (is-set= result-2 expected)
              (is-set= result-3 expected)))
          )))))

