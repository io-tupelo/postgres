(ns tst.io-tupelo.postgres.simpsons
  (:use tupelo.core
        tupelo.test)
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

(verify-focus
  (prof/with-timer-print :simpsons

    (jdbc/execute-one! conn ["drop table if exists address"])
    (jdbc/execute-one! conn ["
        create table address (
          id      serial,
          name    text,
          email   text
        ) "])

    (prof/with-timer-print :simpsons-inner
      (let [r22 (jdbc/execute-one! conn ["
                  insert into address(name, email)
                    values( 'Marge Simpson', 'marge@springfield.co' ) "]
                  {:return-keys true})
            r23 (jdbc/execute-one! conn ["select * from address where id= ?" 1])]
        (is= r22 #:address{:id 1 :name "Marge Simpson" :email "marge@springfield.co"})
        (is= r23 #:address{:id 1 :name "Marge Simpson" :email "marge@springfield.co"}))

      (let [r32     (jdbc/execute-one! conn ["insert into address(name, email)
                                          values( 'Bart Simpson', 'bart@mischief.com' ) "]
                      {:return-keys true :builder-fn rs/as-unqualified-lower-maps})
            r33     (jdbc/execute-one! conn ["select * from address where id= ?" 2]
                      {:builder-fn rs/as-unqualified-lower-maps})
            ds-opts (jdbc/with-options conn {:builder-fn rs/as-lower-maps})
            r34     (jdbc/execute-one! ds-opts ["select * from address where id= ?" 2])]
        (is= r32 {:id 2 :name "Bart Simpson" :email "bart@mischief.com"})
        (is= r33 {:id 2 :name "Bart Simpson" :email "bart@mischief.com"})
        (is= r34 #:address{:id 2 :name "Bart Simpson" :email "bart@mischief.com"})))

    ))
