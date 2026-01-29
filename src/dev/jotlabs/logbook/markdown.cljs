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

(defn ensure-date-section
  "Ensures the content has a section for the given date, returns updated content."
  [content date]
  (let [date-str (format-date date)
        section-header (date-section-header date)]
    (cond
      ;; Empty file - add full header structure
      (str/blank? content)
      (str (todo-file-header) section-header)

      ;; Section exists - return as is
      (str/includes? content (str "## " date-str))
      content

      ;; Need to add new section - insert after main header
      :else
      (let [lines (str/split-lines content)
            header-end (loop [i 0]
                         (if (>= i (count lines))
                           i
                           (let [line (nth lines i)]
                             (if (and (not (str/blank? line))
                                      (not (str/starts-with? line "#")))
                               i
                               (recur (inc i))))))]
        (str (str/join "\n" (take header-end lines))
             (when (pos? header-end) "\n")
             section-header
             (when (< header-end (count lines))
               (str "\n" (str/join "\n" (drop header-end lines)))))))))

(defn add-entry-to-date-section
  "Adds an entry under the appropriate date section."
  [content date entry]
  (let [date-str (format-date date)
        section-marker (str "## " date-str)
        content-with-section (ensure-date-section content date)]
    (str/replace content-with-section
                 section-marker
                 (str section-marker "\n" entry))))

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


