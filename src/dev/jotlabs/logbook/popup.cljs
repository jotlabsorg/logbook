(ns dev.jotlabs.logbook.popup
  "Popup UI with 3 action buttons for managing reading list."
  (:require
   [dev.jotlabs.logbook.state :refer [app-state]]
   [dev.jotlabs.logbook.markdown :as md]
   [dev.jotlabs.logbook.storage :as storage]
   [reagent.dom :as rdom]))

;;TODO@chico improve manage state
;;TODO@chico improve error handling
;;TODO@chico add keyboard shortcuts
;;TODO@chico improve UI/UX
;;TODO@chico add tests
;;TODO@chico handle edge cases (e.g., tab without URL)
;;TODO@chico improve the logic with .promises chaining

(defn get-current-tab []
  (js/Promise.
   (fn [resolve reject]
     (-> js/chrome.tabs
         (.query #js {:active true :currentWindow true}
                 (fn [tabs]
                   (if-let [tab (first tabs)]
                     (resolve {:title (.-title tab)
                               :url (.-url tab)
                               :id (.-id tab)})
                     (reject (js/Error. "No active tab found")))))))))

(defn close-tab [tab-id]
  (js/Promise.
   (fn [resolve _]
     (.remove js/chrome.tabs tab-id resolve))))

(defn save-to-todo!
  "Saves current tab to the todo reading list."
  []
  (swap! app-state assoc :processing true :message "Saving...")
  (let [handle (:dir-handle @app-state)
        tab (:current-tab @app-state)
        now (js/Date.)]
    (-> (storage/get-or-create-file handle md/todo-file)
        (.then (fn [file-handle]
                 (-> (storage/read-file file-handle)
                     (.then (fn [content]
                              (let [entry (md/format-todo-entry tab now)
                                    updated (md/add-entry-to-date-section content now entry)]
                                (storage/write-file file-handle updated)))))))
        (.then (fn [_]
                 (swap! app-state assoc
                        :processing false
                        :status :success
                        :message "Saved to reading list!")))
        (.catch (fn [^js err]
                  (swap! app-state assoc
                         :processing false
                         :status :error
                         :message (str "Error: " (.-message err))))))))

(defn mark-as-read!
  "Marks current tab as read - moves from todo to read file."
  []
  (swap! app-state assoc :processing true :message "Marking as read...")
  (let [handle (:dir-handle @app-state)
        tab (:current-tab @app-state)
        now (js/Date.)
        month-file (md/read-month-file now)]
    (-> (js/Promise.all
         #js [(storage/get-or-create-file handle md/todo-file)
              (storage/get-or-create-file handle month-file)])
        (.then (fn [handles]
                 (let [todo-handle (aget handles 0)
                       read-handle (aget handles 1)]
                   (-> (js/Promise.all
                        #js [(storage/read-file todo-handle)
                             (storage/read-file read-handle)])
                       (.then (fn [contents]
                                (let [todo-content (aget contents 0)
                                      read-content (aget contents 1)
                                      url (:url tab)
                                      ;; Remove from todo
                                      new-todo (md/remove-entry-by-url todo-content url)
                                      ;; Add to read file
                                      entry (md/format-read-entry tab now)
                                      new-read (str (md/ensure-file-header read-content
                                                                           #(md/read-file-header now))
                                                    entry)]
                                  (js/Promise.all
                                   #js [(storage/write-file todo-handle new-todo)
                                        (storage/write-file read-handle new-read)]))))))))
        (.then (fn [_]
                 (swap! app-state assoc
                        :processing false
                        :status :success
                        :message "Marked as read!")))
        (.catch (fn [^js err]
                  (swap! app-state assoc
                         :processing false
                         :status :error
                         :message (str "Error: " (.-message err))))))))

;;TODO@chico improve this .then.then.then.
(defn archive-and-close!
  "Archives to history and closes the tab."
  []
  (swap! app-state assoc :processing true :message "Archiving...")
  (let [handle (:dir-handle @app-state)
        tab (:current-tab @app-state)
        now (js/Date.)]
    (-> (js/Promise.all
         #js [(storage/get-or-create-file handle md/todo-file)
              (storage/get-or-create-file handle md/history-file)])
        (.then (fn [handles]
                 (let [todo-handle (aget handles 0)
                       history-handle (aget handles 1)]
                   (-> (js/Promise.all
                        #js [(storage/read-file todo-handle)
                             (storage/read-file history-handle)])
                       (.then (fn [contents]
                                (let [todo-content (aget contents 0)
                                      history-content (aget contents 1)
                                      url (:url tab)
                                      ;; Remove from todo if present
                                      new-todo (md/remove-entry-by-url todo-content url)
                                      ;; Add to history
                                      entry (md/format-history-entry tab now)
                                      new-history (str (md/ensure-file-header history-content
                                                                              md/history-file-header)
                                                       entry)]
                                  (js/Promise.all
                                   #js [(storage/write-file todo-handle new-todo)
                                        (storage/write-file history-handle new-history)]))))))))
        (.then (fn [_]
                 (close-tab (:id tab))))
        (.then (fn [_]
                 ;; Update to the new active tab
                 (swap! app-state assoc
                        :processing false
                        :status :success
                        :message "Archived and closed!")
                 ;; Fetch the new current tab after a short delay
                 (js/setTimeout
                  (fn []
                    (-> (get-current-tab)
                        (.then #(swap! app-state assoc :current-tab % :message "Ready"))
                        (.catch #(swap! app-state assoc :message "Ready"))))
                  100)))
        (.catch (fn [^js err]
                  (swap! app-state assoc
                         :processing false
                         :status :error
                         :message (str "Error: " (.-message err))))))))

(defn select-folder!
  "Prompts user to select a folder for storage."
  []
  (swap! app-state assoc :processing true :message "Select a folder...")
  (-> (storage/request-directory!)
      (.then (fn [handle]
               (swap! app-state assoc
                      :dir-handle handle
                      :folder-name (.-name handle)
                      :status :ready
                      :processing false
                      :message "Folder selected!")
               ;; Fetch current tab
               (-> (get-current-tab)
                   (.then #(swap! app-state assoc :current-tab %)))))
      (.catch (fn [^js err]
                ;; If user cancelled, just restore previous state
                (if (= (.-name err) "AbortError")
                  (let [has-folder? (:folder-name @app-state)]
                    (swap! app-state assoc
                           :processing false
                           :status (if has-folder? :needs-permission :needs-setup)
                           :message (if has-folder? "Cancelled" "Please select a folder")))
                  (swap! app-state assoc
                         :processing false
                         :status :error
                         :message (str "Error: " (.-message err))))))))

(defn grant-permission!
  "Re-requests permission on the existing saved folder."
  []
  (swap! app-state assoc :processing true :message "Requesting permission...")
  (let [handle (:dir-handle @app-state)]
    (-> (storage/verify-permission handle)
        (.then (fn [h]
                 (swap! app-state assoc
                        :dir-handle h
                        :status :ready
                        :processing false
                        :message "Ready")
                 ;; Fetch current tab
                 (-> (get-current-tab)
                     (.then #(swap! app-state assoc :current-tab %)))))
        (.catch (fn [^js err]
                  (swap! app-state assoc
                         :processing false
                         :status :error
                         :message (str "Error: " (.-message err))))))))

;; UI Components
(defn status-bar []
  (let [{:keys [status message]} @app-state
        status-class (case status
                       :error "error"
                       :success "success"
                       "")]
    [:div.status {:class status-class} message]))

;; Initialization
(defn initialize!
  "Initialize the popup."
  []
  (-> (storage/initialize-storage)
      (.then (fn [{:keys [status handle folder-name]}]
               (swap! app-state assoc
                      :status status
                      :dir-handle handle
                      :folder-name folder-name
                      :message (case status
                                 :ready "Ready"
                                 :needs-setup "Please select a folder"
                                 :needs-permission "Grant access to continue"
                                 "Unknown status"))
               (when (= status :ready)
                 (-> (get-current-tab)
                     (.then #(swap! app-state assoc :current-tab %))
                     (.catch #(swap! app-state assoc
                                     :status :error
                                     :message "Could not get current tab"))))))
      (.catch (fn [^js err]
                (swap! app-state assoc
                       :status :error
                       :message (str "Init error: " (.-message err)))))))

(defn setup-view []
  [:div.setup-container
   [:p.setup-text
    "Select a folder where your reading list files will be stored."]
   [:button.btn.btn-folder
    {:on-click select-folder!
     :disabled (:processing @app-state)}
    [:span.btn-icon "📁"]
    [:span.btn-label "Select Folder"]]])

(defn permission-view []
  (let [folder-name (:folder-name @app-state)]
    [:div.setup-container
     [:p.setup-text
      (str "Folder: " (or folder-name "Unknown"))]
     [:div.actions
      [:button.btn.btn-folder
       {:on-click grant-permission!
        :disabled (:processing @app-state)}
       [:span.btn-icon "📂"]
       [:span.btn-label "Use This Folder"]]
      [:button.btn.btn-archive
       {:on-click select-folder!
        :disabled (:processing @app-state)}
       [:span.btn-icon "📁"]
       [:span.btn-label "Change Folder"]]]]))
(defn action-button [{:keys [class icon label shortcut on-click]}]
  [:button.btn {:class class
                :on-click on-click
                :disabled (:processing @app-state)}
   [:span.btn-icon icon]
   [:span.btn-label label]
   (when shortcut
     [:span.btn-shortcut shortcut])])

(defn actions-view []
  [:div.actions
   [action-button {:class "btn-save"
                   :icon "+"
                   :label "Save to Read"
                   :on-click save-to-todo!}]
   [action-button {:class "btn-read"
                   :icon "✓"
                   :label "Mark as Read"
                   :on-click mark-as-read!}]
   [action-button {:class "btn-archive"
                   :icon "→"
                   :label "Close & Archive"
                   :on-click archive-and-close!}]])
(defn app []
  [:div
   [:div.header
    [:div.logo "L"]
    [:div.title "Logbook"]]
   [status-bar]
   (case (:status @app-state)
     :needs-setup [setup-view]
     :needs-permission [permission-view]
     :loading [:div.setup-container [:p.setup-text "Loading..."]]
     [actions-view])])

(defn init []
  (rdom/render [app]
               (.getElementById js/document "app"))
  (initialize!))
