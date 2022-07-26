(ns demo.util
  (:use tupelo.core)
  (:require
    [clojure.walk :as walk]
    [schema.core :as s]
    [tupelo.schema :as tsk]
    [tupelo.string :as str]
    ))

(defn walk-normalize ; => tupelo.string.safe/walk-normalize
  [data]
  (walk/postwalk
    (fn [arg]
      (cond-it-> arg
        (string? it) (str/lower-case (str/whitespace-collapse it))))
    data))

(defn walk-whitespace-collapse
  "Walks a data structure, calling `(str/whitespace-collapse ...)` on all String values, else noop."
  [data]
  (walk/postwalk
    (fn [arg] (cond-it-> arg
                (string? it) (str/whitespace-collapse it)))
    data))
