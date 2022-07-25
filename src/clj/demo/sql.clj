(ns demo.sql
  (:use tupelo.core)
  (:require
    [jsonista.core :as json]
    [next.jdbc :as jdbc]
    [next.jdbc.prepare :as prepare]
    [next.jdbc.result-set :as rs]
    [next.jdbc.sql :as sql]
    )
  (:import
    [clojure.lang IPersistentMap IPersistentVector]
    [java.sql PreparedStatement]
    [org.postgresql.util PGobject]
    ))

; (set! *warn-on-reflection* true)

;---------------------------------------------------------------------------------------------------
; Helper functions from next.jdbc docs to handle clj<->jsonb I/O

; :decode-key-fn here specifies that JSON-keys will become keywords:
(comment
  (def mapper (json/object-mapper {:decode-key-fn keyword}))
  (def ->json json/write-value-as-string) ; #todo or use tupelo.core/edn->json
  (def <-json #(json/read-value % mapper))) ; #todo or use tupelo.core/json->edn

(defn ->pgobject
  "Transforms Clojure data to a PGobject that contains the data as
  JSON. PGObject type defaults to `jsonb` but can be changed via
  metadata key `:pgtype`"
  [x]
  (let [pgtype (or (:pgtype (meta x))
                 "jsonb")]
    (doto (PGobject.)
      (.setType pgtype)
      (.setValue (edn->json x)))))

(defn <-pgobject
  "Transform PGobject containing `json` or `jsonb` value to Clojure
  data."
  [^PGobject v]
  (let [type  (.getType v)
        value (.getValue v)]
    (if (#{"jsonb" "json"} type)
      (when value
        (with-meta (json->edn value) {:pgtype type}))
      value)))

; if a SQL parameter is a Clojure hash map or vector, it'll be transformed
; to a PGobject for JSON/JSONB:
(extend-protocol prepare/SettableParameter
  IPersistentMap
  (set-parameter [m ^PreparedStatement s i]
    (.setObject s i (->pgobject m)))

  IPersistentVector
  (set-parameter [v ^PreparedStatement s i]
    (.setObject s i (->pgobject v))))

; if a row contains a PGobject then we'll convert them to Clojure data
; while reading (if column is either "json" or "jsonb" type):
(extend-protocol rs/ReadableColumn
  PGobject
  (read-column-by-label [^PGobject v _]
    (<-pgobject v))
  (read-column-by-index [^PGobject v _2 _3]
    (<-pgobject v)))

