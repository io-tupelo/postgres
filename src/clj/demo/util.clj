(ns demo.util
  (:use tupelo.core)
  (:require
    [clojure.walk :as walk]
    [schema.core :as s]
    [tupelo.string :as str]
    [tupelo.schema :as tsk]))

(s/defn str-norm-safe :- s/Any ; => tupelo.string/normalize[-safe]
  [arg :- s/Any]
  (if (string? arg)
    (str/lower-case (str/whitespace-collapse arg))
    arg))

(s/defn str-trim-safe :- s/Any
  [arg :- s/Any]
  (if (string? arg)
    (str/trim arg)
    arg))

(s/defn whitespace-collapse-vals :- tsk/KeyMap
  [tx :- tsk/KeyMap]
  (map-vals tx
    (fn [arg]
      (if (string? arg)
        (str/whitespace-collapse arg)
        arg))))
