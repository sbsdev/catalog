(ns catalog.issue
  "Functionality to calculate next and previous issues and getting the
  issue for a given date"
  (:require [java-time :as time]))

(defn issue-for
  "Return a vector of year and issue for given `date`"
  [date]
  (let [[year month] (time/as date :year :month-of-year)
        issue (quot (inc month) 2)]
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
