(ns tst.demo.util
  (:use demo.util tupelo.core tupelo.test))

(verify
  (is= nil (walk-normalize nil))
  (is= (walk-normalize "  Hello THERE!  ")
    "hello there!")

  (let [m {:a 1
           :b "  Hello There "
           :c nil}]
    (is= (walk-whitespace-collapse m)
      {:a 1
       :b "Hello There"
       :c nil})))

