(ns dev.jotlabs.logbook.storage
  "File System Access API wrapper with IndexedDB 
  persistence for directory handle.")

;; there is a lot of promise chains here; consider using
;; there is a lot complexity code that we can improve

(def ^:private db-name "loogbook-db")
(def ^:private store-name "directory-handles")
(def ^:private handle-key "root-directory")

(defn- open-db
  "Opens IndexedDB and returns a promise with the database."
  []
  (js/Promise.
   (fn [resolve reject]
     (let [request (.open js/indexedDB db-name 1)]
       (set! (.-onupgradeneeded request)
             (fn [e]
               (let [db (.-result (.-target e))]
                 (when-not (.contains (.-objectStoreNames db) store-name)
                   (.createObjectStore db store-name)))))
       (set! (.-onsuccess request)
             (fn [e]
               (resolve (.-result (.-target e)))))
       (set! (.-onerror request)
             (fn [e]
               (reject (.-error (.-target e)))))))))

(defn save-directory-handle!
  "Saves the directory handle and its name to IndexedDB."
  [handle]
  (-> (open-db)
      (.then (fn [db]
               (js/Promise.
                (fn [resolve reject]
                  (let [tx (.transaction db #js [store-name] "readwrite")
                        store (.objectStore tx store-name)
                        ;; Store both handle and name
                        data #js {:handle handle :name (.-name handle)}
                        request (.put store data handle-key)]
                    (set! (.-onsuccess request) #(resolve true))
                    (set! (.-onerror request) #(reject (.-error %))))))))))

(defn get-directory-handle
  "Retrieves the saved directory handle from IndexedDB."
  []
  (-> (open-db)
      (.then (fn [db]
               (js/Promise.
                (fn [resolve reject]
                  (let [tx (.transaction db #js [store-name] "readonly")
                        store (.objectStore tx store-name)
                        request (.get store handle-key)]
                    (set! (.-onsuccess request)
                          #(resolve (.-result request)))
                    (set! (.-onerror request)
                          #(reject (.-error %))))))))))

(defn request-directory!
  "Prompts user to select a directory and saves the handle.
   Falls back to opening a setup tab when showDirectoryPicker
   is unavailable (e.g. extension popups in Brave)."
  []
  (if (fn? (.-showDirectoryPicker js/window))
    (-> (.showDirectoryPicker js/window #js {:mode "readwrite"})
        (.then (fn [handle]
                 (-> (save-directory-handle! handle)
                     (.then (fn [_] handle))))))
    (do
      (.create js/chrome.tabs
               #js {:url (.getURL js/chrome.runtime "setup.html")})
      (js/Promise.reject
       (doto (js/Error. "Opened setup page in a new tab")
         (-> .-name (set! "AbortError")))))))

(defn verify-permission
  "Verifies we have read/write permission on the directory handle."
  [handle]
  (-> (.queryPermission handle #js {:mode "readwrite"})
      (.then (fn [state]
               (if (= state "granted")
                 (js/Promise.resolve handle)
                 (-> (.requestPermission handle #js {:mode "readwrite"})
                     (.then (fn [state]
                              (if (= state "granted")
                                handle
                                (throw (js/Error. "Permission denied")))))))))))

(defn get-or-create-file
  "Gets or creates a file in the directory."
  [dir-handle filename]
  (.getFileHandle dir-handle filename #js {:create true}))

(defn read-file
  "Reads the contents of a file."
  [file-handle]
  (-> (.getFile file-handle)
      (.then (fn [file]
               (.text file)))))

(defn write-file
  "Writes content to a file, replacing existing content."
  [file-handle content]
  (-> (.createWritable file-handle)
      (.then (fn [writable]
               (-> (.write writable content)
                   (.then #(.close writable)))))))

(defn initialize-storage
  "Initializes storage by checking for existing directory handle.
   Returns :ready if handle exists and permission granted,
   :needs-permission if handle exists but needs user interaction to grant permission,
   :needs-setup if no handle saved yet."
  []
  (-> (get-directory-handle)
      (.then (fn [data]
               (if data
                 (let [handle (if (.-handle data) (.-handle data) data)  ;; Handle legacy format
                       folder-name (or (.-name data) (.-name handle))]
                   (-> (.queryPermission handle #js {:mode "readwrite"})
                       (.then (fn [state]
                                (if (= state "granted")
                                  {:status :ready :handle handle :folder-name folder-name}
                                  ;; Handle exists but needs permission
                                  {:status :needs-permission :handle handle :folder-name folder-name})))))
                 {:status :needs-setup :handle nil :folder-name nil})))))
