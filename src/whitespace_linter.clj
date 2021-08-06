(ns whitespace-linter
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [colorize.core :as colorize]
            [methodical.core :as m]
            [progrock.core :as pr])
  (:import [java.io File LineNumberReader]
           [java.nio.file Files FileVisitOption Path]
           java.nio.file.attribute.BasicFileAttributes
           java.util.function.BiPredicate))

(set! *warn-on-reflection* true)

(m/defmulti lint-char
  {:arglists '([ch options])}
  :none
  :dispatcher (m/everything-dispatcher)
  :combo      (m/concat-method-combination))

(def confusing-unicode-characters
  "Map of Unicode characters to ASCII ones that they could easily be confused with."
  {\u00A0 " "  \u2000 " " \u2001 " "  \u2002 " " \u2003 " " \u2004 " " \u2005 " "  \u2006 " "  \u2007 " "  \u2008 " "
   \u2009 " "  \u200A " " \u2028 " "  \u2029 " " \u202F " " \u205F " " \u3000 " "  \uFF01 "!"  \u01C3 "!"  \u2D51 "!"
   \uFE15 "!"  \uFE57 "!" \uFF02 "\"" \uFF03 "#" \uFE5F "#" \uFF04 "$" \uFE69 "$"  \uFF05 "%"  \u066A "%"  \u2052 "%"
   \uFE6A "%"  \uFF06 "&" \uFE60 "&"  \uFF07 "'" \u02B9 "'" \u0374 "'" \uFF08 "("  \uFE59 "("  \uFF09 ")"  \uFE5A ")"
   \uFF0A "*"  \u22C6 "*" \uFE61 "*"  \uFF0B "+" \u16ED "+" \uFE62 "+" \uFF0C ","  \u02CF ","  \u16E7 ","  \u201A ","
   \uFF0D "-"  \u02D7 "-" \u2212 "-"  \u23BC "-" \u2574 "-" \uFE63 "-" \uFF0E "."  \u2024 "."  \uFF0F "/"  \u1735 "/"
   \u2044 "/"  \u2215 "/" \u29F8 "/"  \u14BF "2" \u01B7 "3" \u0417 "3" \u2128 "3"  \u13CE "4"  \u13EE "6"  \u13ED "9"
   \uFF1A ":"  \u02D0 ":" \u02F8 ":"  \u0589 ":" \u1361 ":" \u16EC ":" \u205A ":"  \u2236 ":"  \u2806 ":"  \uFE13 ":"
   \uFE55 ":"  \uFF1B ";" \u037E ";"  \uFE14 ";" \uFE54 ";" \uFF1C "<" \u02C2 "<"  \u2039 "<"  \u227A "<"  \u276E "<"
   \u2D66 "<"  \uFE64 "<" \uFF1D "="  \u2550 "=" \u268C "=" \uFE66 "=" \uFF1E ">"  \u02C3 ">"  \u203A ">"  \u227B ">"
   \u276F ">"  \uFE65 ">" \uFF1F "?"  \uFE16 "?" \uFE56 "?" \uFF20 "@" \uFE6B "@"  \u0391 "A"  \u0410 "A"  \u13AA "A"
   \u0392 "B"  \u0412 "B" \u13F4 "B"  \u15F7 "B" \u2C82 "B" \u03F9 "C" \u0421 "C"  \u13DF "C"  \u216D "C"  \u2CA4 "C"
   \u13A0 "D"  \u15EA "D" \u216E "D"  \u0395 "E" \u0415 "E" \u13AC "E" \u15B4 "F"  \u050C "G"  \u13C0 "G"  \u0397 "H"
   \u041D "H"  \u12D8 "H" \u13BB "H"  \u157C "H" \u2C8E "H" \u0399 "I" \u0406 "I"  \u2160 "I"  \u0408 "J"  \u13AB "J"
   \u148D "J"  \u039A "K" \u13E6 "K"  \u16D5 "K" \u212A "K" \u2C94 "K" \u13DE "L"  \u14AA "L"  \u216C "L"  \u039C "M"
   \u03FA "M"  \u041C "M" \u13B7 "M"  \u216F "M" \u039D "N" \u2C9A "N" \u039F "O"  \u041E "O"  \u2C9E "O"  \u03A1 "P"
   \u0420 "P"  \u13E2 "P" \u2CA2 "P"  \u051A "Q" \u2D55 "Q" \u13A1 "R" \u13D2 "R"  \u1587 "R"  \u0405 "S"  \u13DA "S"
   \u03A4 "T"  \u0422 "T" \u13A2 "T"  \u13D9 "V" \u2164 "V" \u13B3 "W" \u13D4 "W"  \u03A7 "X"  \u0425 "X"  \u2169 "X"
   \u2CAC "X"  \u03A5 "Y" \u2CA8 "Y"  \u0396 "Z" \u13C3 "Z" \uFF3B "[" \uFF3C "\\" \u2216 "\\" \u29F5 "\\" \u29F9 "\\"
   \uFE68 "\\" \uFF3D "]" \uFF3E "^"  \u02C4 "^" \u02C6 "^" \u1DBA "^" \u2303 "^"  \uFF3F "_"  \u02CD "_"  \u268A "_"
   \uFF40 "`"  \u02CB "`" \u1FEF "`"  \u2035 "`" \u0251 "a" \u0430 "a" \u03F2 "c"  \u0441 "c"  \u217D "c"  \u0501 "d"
   \u217E "d"  \u0435 "e" \u1971 "e"  \u0261 "g" \u04BB "h" \u0456 "i" \u2170 "i"  \u03F3 "j"  \u0458 "j"  \u217C "l"
   \u217F "m"  \u1952 "n" \u03BF "o"  \u043E "o" \u0D20 "o" \u2C9F "o" \u0440 "p"  \u2CA3 "p"  \u0455 "s"  \u1959 "u"
   \u222A "u"  \u1D20 "v" \u2174 "v"  \u2228 "v" \u22C1 "v" \u1D21 "w" \u0445 "x"  \u2179 "x"  \u2CAD "x"  \u0443 "y"
   \u1EFF "y"  \u1D22 "z" \uFF5B "{"  \uFE5B "{" \uFF5C "|" \u01C0 "|" \u16C1 "|"  \u239C "|"  \u239F "|"  \u23A2 "|"
   \u23A5 "|"  \u23AA "|" \u23AE "|"  \uFFE8 "|" \uFF5D "}" \uFE5C "}" \uFF5E "~"  \u02DC "~"  \u2053 "~"  \u223C "~"})

(def invisible-unicode-characters
  #{\u2060 \u2061 \u2062 \u2063 \u2064 \u2065 \u2066 \u2067 \u2068 \u2069})

(m/defmethod lint-char :character/confusing-unicode-character
  [^Character ch options]
  (when-let [similar-character (get confusing-unicode-characters ch)]
    [{:linter    :character/confusing-unicode-character
      :message   (format "Found Unicode character \\u%04x '%s' that looks way too similar to ASCII '%s'"
                         (int ch) ch similar-character)
      :character ch}]))

(m/defmethod lint-char :character/invisible-unicode-character
  [ch options]
  (when (invisible-unicode-characters ch)
    [{:linter    :character/invisible-unicode-character
      :message   (format "Found invisible Unicode character \\u%04x" (int ch))
      :character ch}]))

(m/defmethod lint-char :character/tab
  [ch _]
  (when (= ch \tab)
    [{:linter :character/tab
      :message "Found tab character. Use spaces for indentation!"}]))

(m/defmulti lint-line
  {:arglists '([^String line options])}
  :none
  :dispatcher (m/everything-dispatcher)
  :combo      (m/concat-method-combination))

(m/defmethod lint-line ::lint-line-by-character
  [^String line options]
  (into []
        (comp (map-indexed
               (fn [i c]
                 (for [error (lint-char c options)]
                   (assoc error :column (inc i)))))
              cat)
        line))

(m/defmethod lint-line :line/no-trailing-whitespace
  [line _]
  (when (re-find #"\s+$" line)
    [{:linter  :line/no-trailing-whitespace
      :message "Found line ending in trailing whitespace"}]))

;; TODO -- a linter that checks line ends in `\n`, not `\r\n`. (i.e., uses UNIX line endings instead of Windows)

(defn skip-next [pred]
  (fn [rf]
    (let [normal (Object.)
          skip (Object.)
          flag (volatile! normal)]
      (fn
        ([result] (rf result))
        ([result x]
         (cond
           (identical? @flag skip)
           (do (vreset! flag normal)
               result)

           (pred x)
           (do (vreset! flag skip)
               result)

           :else
           (rf result x)))))))

(m/defmulti lint-file
  {:arglists '([^File file options])}
  :none
  :dispatcher (m/everything-dispatcher)
  :combo      (m/concat-method-combination))

(m/defmethod lint-file ::lint-file-by-line
  [^File file options]
  (with-open [r (LineNumberReader. (io/reader file))]
    (into []
          (comp
            (take-while some?)
            (skip-next (fn [[_line-number line]] (.contains ^String line "<skip lint>")))
            (map (fn [[line-number line]]
                   (for [error (lint-line line options)]
                     (assoc error :line-number line-number, :line line))))
            cat)
          (repeatedly (fn []
                        (when-let [line (.readLine r)]
                          [(.getLineNumber r) line]))))))

(m/defmethod lint-file :file/ends-in-newline
  [^File file options]
  (when (with-open [ra-file (java.io.RandomAccessFile. file "r")]
          (.seek ra-file (dec (.length ra-file)))
          (not= (char (.readByte ra-file)) \newline))
    [{:linter      :file/ends-in-newline
      :message     "Last line of file should end in a newline character"
      ;; TODO -- this seems like a grossly inefficient way to get the number of lines in the file
      :line-number (with-open [r (io/reader file)]
                     (count (line-seq r)))}]))

(m/defmethod lint-file :file/no-trailing-blank-lines
  [^File file options]
  (with-open [r (LineNumberReader. (io/reader file))]
    (let [last-line (last (take-while some? (repeatedly #(.readLine r))))]
      (when (str/blank? last-line)
        [{:linter      :file/no-trailing-blank-lines
          :message     "File should not end in a blank line"
          :line-number (.getLineNumber r)
          :line        last-line}]))))

;; TODO -- a linter that checks that the file uses UTF-8 encoding

(m/defmulti find-files-to-lint
  {:arglists '([file-or-dir options])}
  (fn [file-or-dir _]
    (type file-or-dir)))

(defn lint-file? ^Boolean [^Path path {:keys [include-patterns exclude-patterns max-file-size-kb]}]
  (let [path-str         (str path)
        matches-pattern? (fn [pattern]
                           (re-find pattern path-str))]
    (boolean
     (and
      ;; don't try to lint the whitespace linter itself! That would be madness.
      (not (str/ends-with? path-str "whitespace_linter.clj"))
      (some matches-pattern? include-patterns)
      (not-any? matches-pattern? exclude-patterns)
      ;; ignore empty files.
      (pos? (Files/size path))
      ;; ignore large files.
      (if (> (Files/size path) (* max-file-size-kb 1024))
        (println (format "Skipping %s because file is too large (%d kB, :max-file-size-kb is %d)"
                         (str path)
                         (long (/ (Files/size path) 1024))
                         max-file-size-kb))
        true)))))

(m/defmethod find-files-to-lint Path
  [^Path path options]
  (into
   []
   (map (fn [^Path path]
          (.toFile path)))
   (iterator-seq
    (.iterator
     (Files/find
      path
      Integer/MAX_VALUE
      (reify BiPredicate
        (test [_ path attributes]
          (boolean
           (when (.isRegularFile ^BasicFileAttributes attributes)
             (lint-file? path options)))))
      (into-array FileVisitOption []))))))

(m/defmethod find-files-to-lint String
  [^String s options]
  (find-files-to-lint (Path/of s (into-array String [])) options))

(m/defmethod find-files-to-lint clojure.lang.Symbol
  [symb options]
  (find-files-to-lint (str symb) options))

(m/defmethod find-files-to-lint clojure.lang.IPersistentCollection
  [coll options]
  (mapcat #(find-files-to-lint % options) coll))

(m/defmethod find-files-to-lint :default
  [file options]
  (find-files-to-lint (io/file file) options))

(m/defmethod find-files-to-lint clojure.lang.IPersistentCollection
  [coll options]
  (mapcat #(find-files-to-lint % options) coll))

(def default-options
  {:paths            ["."]
   :include-patterns [#"."]
   :max-file-size-kb 1024 ; anything over one megabyte is probably a little big to be linting.
   ::exit-on-finish  true
   ::progress-bar    true})

(defn ->Pattern ^java.util.regex.Pattern [pattern]
  (if (instance? java.util.regex.Pattern pattern)
    pattern
    (re-pattern pattern)))

(defn parse-options [options]
  (merge
   default-options
   (cond-> options
     (:include-patterns options) (update :include-patterns (partial mapv ->Pattern))
     (:exclude-patterns options) (update :exclude-patterns (partial mapv ->Pattern)))))

(defn print-errors [errors]
  (println (format "Found %d errors" (count errors)))
  (doseq [{:keys [file line-number column message linter]} errors]
    (println
     (str
      (colorize/yellow file)
      (when line-number
        (str
         (colorize/yellow (colorize/bold (format ":%d" line-number)))
         (when column
           (colorize/yellow (colorize/italic (format ":%d" column))))))
      (format " %s (%s)" (colorize/red message) (colorize/italic linter))))))

(defn lint
  [options]
  (let [{:keys [paths], ::keys [exit-on-finish progress-bar], :as options} (parse-options options)]
    (println "Finding matching files...")
    (try
      (let [files         (vec (find-files-to-lint paths options))
            _             (println "Linting" (count files) "files...")
            bar           (atom (pr/progress-bar (count files)))
            start-time-ms (System/currentTimeMillis)
            errors        (into [] cat (pmap
                                        (fn [^File file]
                                          (let [errors (vec (lint-file file options))]
                                            (when progress-bar
                                              (pr/print (swap! bar pr/tick)))
                                            (for [error errors]
                                              (assoc error :file (str file)))))
                                        files))]
        (println (format "\nLinted %d files in %.1f seconds."
                         (count files)
                         (/ (- (System/currentTimeMillis) start-time-ms) 1000.0)))
        (if (seq errors)
          (print-errors errors)
          (println "No errors. Good job."))
        (when exit-on-finish
          (if (seq errors)
            (System/exit 1)
            (System/exit 0)))
        errors)
      (catch Throwable e
        (pprint/pprint (Throwable->map e))
        (if exit-on-finish
          (System/exit 2)
          (throw e))))))

(defn lint-interactive
  "Version of [[lint]] intended for interactive REPL usage. Doesn't call [[System/exit]] when finished, and returns
  errors in a programatically-readable format."
  [options]
  (if-not (map? options)
    (lint-interactive {:paths options})
    (lint (merge {::progress-bar false, ::exit-on-finish false} options))))
