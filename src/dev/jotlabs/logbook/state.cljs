(ns dev.jotlabs.logbook.state
  (:require
   [reagent.core :as r]))

(defonce app-state
  (r/atom {:status :loading
           :message "Initializing..."
           :dir-handle nil
           :folder-name nil
           :current-tab nil
           :processing false}))
