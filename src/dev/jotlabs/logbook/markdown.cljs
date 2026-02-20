(ns dev.jotlabs.logbook.markdown
  "Markdown formatting utilities for reading list entries."
  (:require [clojure.string :as str]))

(def todo-file "todo-read-list.md")
(def history-file "history.md")

(defn read-month-file
  "Returns the filename for the read items of a given month."
  [date]
  (let [year (.getFullYear date)
        month (inc (.getMonth date))
        month-str (if (< month 10) (str "0" month) (str month))]
    (str "read-" year "-" month-str ".md")))

(defn format-date
  "Formats a date as YYYY-MM-DD."
  [date]
  (let [year (.getFullYear date)
        month (inc (.getMonth date))
        day (.getDate date)]
    (str year "-"
         (if (< month 10) (str "0" month) (str month)) "-"
         (if (< day 10) (str "0" day) (str day)))))

(defn format-datetime
  "Formats a date as YYYY-MM-DD HH:MM."
  [date]
  (let [hours (.getHours date)
        minutes (.getMinutes date)]
    (str (format-date date) " "
         (if (< hours 10) (str "0" hours) (str hours)) ":"
         (if (< minutes 10) (str "0" minutes) (str minutes)))))

(defn format-month-name
  "Returns the month name for a date."
  [date]
  (let [months ["January" "February" "March" "April" "May" "June"
                "July" "August" "September" "October" "November" "December"]]
    (nth months (.getMonth date))))

;; Entry formatting
(defn format-todo-entry
  "Formats a new reading list entry."
  [{:keys [title url]} date]
  (str "- [" title "](" url ") - Added: " (format-datetime date) "\n"))

(defn format-read-entry
  "Formats an entry marked as read."
  [{:keys [title url]} date]
  (str "- [" title "](" url ") - Read: " (format-datetime date) "\n"))

(defn format-history-entry
  "Formats an archived entry."
  [{:keys [title url]} date]
  (str "- [" title "](" url ") - Archived: " (format-datetime date) "\n"))

;; File headers
(defn todo-file-header
  "Returns the header for the todo file."
  []
  "# Reading List\n\n")

(defn read-file-header
  "Returns the header for a read file."
  [date]
  (str "# Read in " (format-month-name date) " " (.getFullYear date) "\n\n"))

(defn history-file-header
  "Returns the header for the history file."
  []
  "# History\n\n")

;; Date sections in todo file
(defn date-section-header
  "Returns a date section header."
  [date]
  (str "## " (format-date date) "\n"))

(defn add-entry-to-date-section
  "Adds an entry under the appropriate date section.
   If the section exists, inserts the entry right after the section header.
   If not, appends a new section at the end of the file."
  [content date entry]
  (let [date-str (format-date date)
        section-marker (str "## " date-str)]
    (if (str/blank? content)
      (str (todo-file-header) section-marker "\n" entry)
      (let [lines (vec (str/split-lines content))
            section-idx (first (keep-indexed
                                (fn [i line]
                                  (when (= (str/trim line) section-marker) i))
                                lines))]
        (if section-idx
          (let [before (subvec lines 0 (inc section-idx))
                after (subvec lines (inc section-idx))]
            (str (str/join "\n" before) "\n"
                 (str/trimr entry) "\n"
                 (str/join "\n" after)))
          (str (str/trimr content) "\n\n" section-marker "\n" entry))))))

(defn ensure-file-header
  "Ensures a file has its header, returns updated content."
  [content header-fn]
  (if (str/blank? content)
    (header-fn)
    content))

(defn remove-entry-by-url
  "Removes an entry from content by URL."
  [content url]
  (let [lines (str/split-lines content)
        filtered (remove #(str/includes? % url) lines)]
    (str/join "\n" filtered)))


