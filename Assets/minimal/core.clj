(ns minimal.core
  (:use arcadia.core arcadia.linear))

(defn first-callback [o]
  (arcadia.core/log "Hello, Arcadia"))
