(ns catalog.metrics
  (:require
   [iapetos.core :as prometheus]
   [iapetos.collector.jvm :as jvm]
   [iapetos.collector.fn :as fn]
   [iapetos.collector.ring :as ring]))

(defonce registry
  (-> (prometheus/collector-registry)
      (jvm/initialize)
      (fn/initialize)
      (ring/initialize)))
