(ns tst.io-tupelo.postgres.jsonb
  (:use tupelo.core tupelo.test)
  (:require
    [io-tupelo.postgres.jsonb :as jsonb]
    [next.jdbc :as jdbc]
    [next.jdbc.sql :as sql]
    [tupelo.misc :as misc]
    [tupelo.string :as str]
    ))

(verify   ; only enable this during testing
  (set! *warn-on-reflection* true))

;---------------------------------------------------------------------------------------------------
#_(verify
  (is= 5 (jsonb/namespace-strip 5))
  (is= "abc" (jsonb/namespace-strip "abc"))
  (is= :item (jsonb/namespace-strip :something.really.big/item))
  (is= 'item (jsonb/namespace-strip 'something.really.big/item))
  (is= (jsonb/walk-namespace-strip
         {:a 1 :my/b [1 'her/c 3] :his/d {:e 5 :other/f :very.last/one}})
    {:a 1 :b [1 'c 3] :d {:e 5 :f :one}}))

;---------------------------------------------------------------------------------------------------
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

(verify
  ; verify form of connection string
  (is (truthy? (re-matches #"jdbc:postgresql://.*/acme_v01" (str conn)))))

;---------------------------------------------------------------------------------------------------
(def table-name->create-cmd
  {"my_stuff" "create table my_stuff (
                    id         text primary key,
                    content    jsonb )"
   })

(def index-name->create-cmd
  {"idx_gin_my_stuff" "create index idx_gin_my_stuff on my_stuff using gin (content)"
   })

;---------------------------------------------------------------------------------------------------
(defn drop-create-tables!
  [conn]
  (doseq [name (keys table-name->create-cmd)]
    (jdbc/execute! conn [(format "drop table if exists %s" name)])
    (jdbc/execute! conn [(grab name table-name->create-cmd)])))

(defn drop-create-indexes!
  [conn]
  (doseq [name (keys index-name->create-cmd)]
    (jdbc/execute! conn [(format "drop index if exists %s" name)])
    (jdbc/execute! conn [(grab name index-name->create-cmd)])))

;---------------------------------------------------------------------------------------------------
(verify
  (drop-create-tables! conn)
  (drop-create-indexes! conn)

  (sql/insert! conn :my_stuff {:id      "id001"
                               :content {:a 1 :b 2}})
  (sql/insert! conn :my_stuff {:id      "id002"
                               :content {:a 11 :b {:c 3}}})

  ; Note that next.jdbc uses table name as keyword namespace on result
  (is= (sql/query conn ["select * from my_stuff"])
    [#:my_stuff{:content {:a 1, :b 2}, :id "id001"}
     #:my_stuff{:content {:a 11, :b {:c 3}}, :id "id002"}])

  (is= (sql/query conn ["select * from my_stuff where content['a'] = '1'"])
    [#:my_stuff{:content {:a 1, :b 2}, :id "id001"}])

  (is= (only (sql/query conn ["select * from my_stuff where content['b']['c'] = '3'"]))
    #:my_stuff{:content {:a 11, :b {:c 3}}, :id "id002"})

  ; Embedded JSON requires single-quotes and escaped double-quotes in SQL query string
  (is= (only (sql/query conn ["select * from my_stuff where content @> '{\"a\": 1}' "]))
    #:my_stuff{:content {:a 1, :b 2}, :id "id001"})

  ; Can use `(jsonb/edn->json-embedded ...)` to convert EDN to json-embedded string
  (let [cmd (it-> {:a 1}
              (jsonb/edn->json-embedded it)
              (str "select * from my_stuff where content @> " it))]
    (is= (only (sql/query conn [cmd]))
      #:my_stuff{:content {:a 1, :b 2}, :id "id001"}))

  ; Can use `(walk-namespace-strip ...)` to simplify keywords on SQL query result
  (let [cmd    (it-> {:b {:c 3}}
                 (jsonb/edn->json-embedded it)
                 (str "select id from my_stuff where content @> " it))
        result (only (sql/query conn [cmd]))]
    (is= (misc/walk-namespace-strip result) ; 0.03 sec on laptop
      {:id "id002"}))
  )

;---------------------------------------------------------------------------------------------------
(verify
  (drop-create-tables! conn)
  (drop-create-indexes! conn)
  (sql/insert! conn :my_stuff {:id      "id001"
                               :content {:my_group
                                         {:created_at  "2001-12-31T11:22:33Z"
                                          :description "ogre"
                                          :offerings   [{:created_at    "1999-12-31T11:22:33Z"
                                                         :ok?           true
                                                         :description   "very good"
                                                         :duration      12
                                                         :duration_unit "month"
                                                         :misc          nil
                                                         :percent       2.0
                                                         :amount        50000.0}
                                                        {:created_at    "2022-12-31T10:22:33Z"
                                                         :ok?           true
                                                         :description   "very bad"
                                                         :duration      30
                                                         :duration_unit "days"
                                                         :misc          :something
                                                         :percent       3.0
                                                         :amount        500.0}]}}})


  (let [found    (misc/walk-namespace-strip
                   (only (sql/query conn ["select * from my_stuff"])))
        expected {:id      "id001"
                  :content {:my_group
                            {:created_at  "2001-12-31T11:22:33Z"
                             :description "ogre"
                             :offerings   [{:amount        50000.0
                                            :created_at    "1999-12-31T11:22:33Z"
                                            :description   "very good"
                                            :duration      12
                                            :duration_unit "month"
                                            :misc          nil
                                            :ok?           true
                                            :percent       2.0}
                                           {:amount        500.0
                                            :created_at    "2022-12-31T10:22:33Z"
                                            :description   "very bad"
                                            :duration      30
                                            :duration_unit "days"
                                            :misc          "something"
                                            :ok?           true
                                            :percent       3.0}]}}}]
    (is= found expected))

  (let [res (sql/query conn ["select id from my_stuff where
            content['my_group']['offerings'] @> '[{\"description\": \"very bad\"}]'  "])]
    (is= res [#:my_stuff{:id "id001"}])))


;---------------------------------------------------------------------------------------------------
;(comment
;  (let [r22 (jdbc/execute-one! conn ["
;                  insert into address(name, email)
;                    values( 'Marge Simpson', 'marge@springfield.co' ) "]
;              {:return-keys true})
;        r23 (jdbc/execute-one! conn ["select * from address where id= ?" 2])]
;    (is= r22 #:address{:id 2 :name "Marge Simpson" :email "marge@springfield.co"})
;    (is= r23 #:address{:id 2 :name "Marge Simpson" :email "marge@springfield.co"}))
;
;  (let [r32     (jdbc/execute-one! conn ["
;                insert into address(name, email)
;                  values( 'Bart Simpson', 'bart@mischief.com' ) "]
;                  {:return-keys true :builder-fn rs/as-unqualified-lower-maps})
;        r33     (jdbc/execute-one! conn ["select * from address where id= ?" 3]
;                  {:builder-fn rs/as-unqualified-lower-maps})
;        ds-opts (jdbc/with-options conn {:builder-fn rs/as-lower-maps})
;        r34     (jdbc/execute-one! ds-opts ["select * from address where id= ?" 3])
;        ]
;    (is= r32 {:id 3 :name "Bart Simpson" :email "bart@mischief.com"})
;    (is= r33 {:id 3 :name "Bart Simpson" :email "bart@mischief.com"})
;    (is= r34 #:address{:id 3 :name "Bart Simpson" :email "bart@mischief.com"})))

;---------------------------------------------------------------------------------------------------
;(verify
;  (jdbc/execute! conn ["drop table if exists invoice"])
;  (let [r41 (jdbc/execute! conn ["
;                create table invoice (
;                  id            serial primary key,
;                  product       varchar(32),
;                  unit_price    decimal(10,2),
;                  unit_count    int,
;                  customer_id   int
;                ) "]) ; postgres does not support "unsigned" integer types
;        r42 (jdbc/execute! conn ["
;                insert into invoice(product, unit_price, unit_count, customer_id)
;                  values
;                    ( 'apple',    0.99, 6, 100 ),
;                    ( 'banana',   1.25, 3, 100 ),
;                    ( 'cucumber', 2.49, 2, 100 )
;                "])
;        r43 (reduce
;              (fn [cost row]
;                (+ cost (* (:unit_price row)
;                          (:unit_count row))))
;              0
;              (jdbc/plan conn ["select * from invoice where customer_id = ? " 100]))
;        ]
;    (is= r41 [#:next.jdbc{:update-count 0}])
;    (is= r42 [#:next.jdbc{:update-count 3}])
;    (is= r43 14.67M)))
;
;;---------------------------------------------------------------------------------------------------
;(verify
;  ; creates & drops a connection (& transaction) for each command
;  (jdbc/execute-one! conn ["drop table if exists langs"])
;  (jdbc/execute-one! conn ["drop table if exists releases"])
;  (throws? (sql/query conn ["select * from langs"])) ; table does not exist
;
;  ; Creates and uses a connection for all commands
;  (with-open [conn (jdbc/get-connection conn)]
;    (jdbc/execute-one! conn ["
;        create table langs (
;          id      serial,
;          lang    varchar not null
;        ) "])
;
;    ; NOTE: Postgres reserves 'desc' for 'descending' (unlike H2), so must use 'description' here
;    (jdbc/execute-one! conn ["
;        create table releases (
;          id              serial,
;          description     varchar not null,
;          langId          numeric
;        ) "]))
;  (is= [] (sql/query conn ["select * from langs"])) ; table exists and is empty
;
;  ; uses one connection in a transaction for all commands
;  (jdbc/with-transaction [tx conn]
;    (let [tx-opts (jdbc/with-options tx {:builder-fn rs/as-lower-maps})]
;      (is= #:langs{:id 1, :lang "Clojure"}
;        (sql/insert! tx-opts :langs {:lang "Clojure"}))
;      (sql/insert! tx-opts :langs {:lang "Java"})
;
;      (is= (sql/query tx-opts ["select * from langs"])
;        [#:langs{:id 1, :lang "Clojure"}
;         #:langs{:id 2, :lang "Java"}])))
;
;  ; uses one connection in a transaction for all commands
;  (jdbc/with-transaction [tx conn]
;    (let [tx-opts (jdbc/with-options tx {:builder-fn rs/as-lower-maps})]
;      (let [clj-id (grab :langs/id (only (sql/query tx-opts ["select id from langs where lang='Clojure'"])))] ; all 1 string
;        (is= 1 clj-id)
;        (sql/insert-multi! tx-opts :releases
;          [:description :langId]
;          [["ancients" clj-id]
;           ["1.8" clj-id]
;           ["1.9" clj-id]]))
;      (let [java-id (grab :langs/id (only (sql/query tx-opts ["select id from langs where lang=?" "Java"])))] ; with query param
;        (is= 2 java-id)
;        (sql/insert-multi! tx-opts :releases
;          [:description :langId]
;          [["dusty" java-id]
;           ["8" java-id]
;           ["9" java-id]
;           ["10" java-id]]))
;
;      (let [; note cannot wrap select list in parens or get "bulk" output
;            result-0 (sql/query tx-opts ["select langs.lang, releases.description
;                                            from    langs join releases
;                                              on     (langs.id = releases.langId)
;                                              where  (lang = 'Clojure') "])
;            result-1 (sql/query tx-opts ["select l.lang, r.description
;                                            from    langs as l
;                                                      join releases as r
;                                              on     (l.id = r.langId)
;                                              where  (l.lang = 'Clojure') "])
;            result-2 (sql/query tx-opts ["select langs.lang, releases.description
;                                            from    langs, releases
;                                              where  ( (langs.id = releases.langId)
;                                                and    (lang = 'Clojure') ) "])
;            result-3 (sql/query tx-opts ["select l.lang, r.description
;                                            from    langs as l, releases as r
;                                              where  ( (l.id = r.langId)
;                                                and    (l.lang = 'Clojure') ) "])
;            ]
;        (let [expected [{:langs/lang "Clojure", :releases/description "ancients"}
;                        {:langs/lang "Clojure", :releases/description "1.8"}
;                        {:langs/lang "Clojure", :releases/description "1.9"}]]
;          (is-set= result-0 expected)
;          (is-set= result-1 expected)
;          (is-set= result-2 expected)
;          (is-set= result-3 expected)))
;      )))

