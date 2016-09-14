(ns catalog.issue
  (:require [clj-time.core :as time.core]))

(defn issue-for
  "Return a vector of year and issue for given `date`"
  [date]
  (let [year (time.core/year date)
        issue (quot (inc (time.core/month date)) 2)]
    [year issue]))

(defn next-issue
  "Return the next vector tuple of year and issue for given `year` and `issue`"
  [year issue]
  (cond
    (>= issue 6) [(inc year) 1]
    :else [year (inc issue)]))

(defn prev-issue
  "Return the previous vector tuple of year and issue for given `year` and `issue`"
  [year issue]
  (cond
    (<= issue 1) [(dec year) 6]
    :else [year (dec issue)]))
