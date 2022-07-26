(ns io-tupelo.jsonb
  "Helper functions from next.jdbc docs to handle clj<->jsonb I/O"
  (:use tupelo.core)
  (:require
    [jsonista.core :as json]
    [next.jdbc :as jdbc]
    [next.jdbc.prepare :as prepare]
    [next.jdbc.result-set :as rs]
    [next.jdbc.sql :as sql]
    [clojure.walk :as walk])
  (:import
    [clojure.lang IPersistentMap IPersistentVector]
    [java.sql PreparedStatement]
    [org.postgresql.util PGobject]))

; (set! *warn-on-reflection* true)

;---------------------------------------------------------------------------------------------------
(comment   ; origin EDN<->JSON conversion replaced with tupelo.core: edn->json & json->edn
  ; :decode-key-fn here specifies that JSON-keys will become keywords:
  (def mapper (json/object-mapper {:decode-key-fn keyword}))
  (def ->json json/write-value-as-string)
  (def <-json #(json/read-value % mapper)))

(defn ^:no-doc ->pgobject
  "Transforms Clojure data to a PGobject that contains the data as JSON.
  PGObject type defaults to `jsonb` but can be changed via metadata key `:pgtype`"
  [x]
  (let [pgtype (or (:pgtype (meta x))
                 "jsonb")]
    (doto (PGobject.)
      (.setType pgtype)
      (.setValue (edn->json x)))))

(defn ^:no-doc <-pgobject
  "Transform PGobject containing `json` or `jsonb` value to Clojure data."
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

;---------------------------------------------------------------------------------------------------
(defn edn->json-embedded
  "Accepts a Clojure EDN data structure like `{:a 1 :b {:c 3}}` and converts it into a JSON string,
  then surrounds it with single-quotes. The result is suitable for inclusion in a Postgres JSONB expression."
  [data]
  (format "' %s '" (edn->json data)))

(defn namespace-strip
  "Removes namespace from any keyword or symbol, otherwise NOOP."
  [item]
  (cond-it-> item
    (keyword? item) (keyword (name item))
    (symbol? item) (symbol (name item))))

(defn walk-namespace-strip
  "Recursively walks a datastructure, removing the namespace from any keyword or symbol"
  [data] (walk/postwalk namespace-strip data))
