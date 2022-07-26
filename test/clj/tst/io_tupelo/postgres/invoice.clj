(ns tst.io-tupelo.postgres.invoice
  (:use tupelo.core tupelo.test)
  (:require
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]
    [next.jdbc.sql :as sql]
    ))

;---------------------------------------------------------------------------------------------------
(def db-info {:dbtype   "postgres"
              :dbname   "acme_v01"
              :user     "postgres"
              :password "docker"
              })

(def conn (jdbc/get-datasource db-info)) ; returns an inner class: "next.jdbc.connection$..."

;---------------------------------------------------------------------------------------------------
(verify
  (jdbc/execute! conn ["drop table if exists invoice"])
  (let [r41 (jdbc/execute! conn ["
                create table invoice (
                  id            serial primary key,
                  product       varchar(32),
                  unit_price    decimal(10,2),
                  unit_count    int,
                  customer_id   int
                ) "]) ; postgres does not support "unsigned" integer types
        r42 (jdbc/execute! conn ["
                insert into invoice(product, unit_price, unit_count, customer_id)
                  values
                    ( 'apple',    0.99, 6, 100 ),
                    ( 'banana',   1.25, 3, 100 ),
                    ( 'cucumber', 2.49, 2, 100 )
                "])
        r43 (reduce
              (fn [cost row]
                (+ cost (* (:unit_price row)
                          (:unit_count row))))
              0
              (jdbc/plan conn ["select * from invoice where customer_id = ? " 100]))
        ]
    (is= r41 [#:next.jdbc{:update-count 0}])
    (is= r42 [#:next.jdbc{:update-count 3}])
    (is= r43 14.67M)))


