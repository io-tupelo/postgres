(ns tst.demo.util
  (:use demo.util tupelo.core tupelo.test)
  (:require
    [tupelo.string :as str])
  )

(verify
  (is= nil (str-norm-safe nil))
  (is= "hello there!" (str-norm-safe "  Hello THERE!  "))

  (is= nil (str-trim-safe nil))
  (is= "Hello THERE!" (str-trim-safe "  Hello THERE!  ")))

(verify
  (let [m {:a 1
           :b "  Hello There "
           :c nil}]
    (is= (whitespace-collapse-vals m)
      {:a 1
       :b "Hello There"
       :c nil})
    ))





