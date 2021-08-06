# Cam's Next-Level Whitespace Linter

```
{com.github.camsaul/whitespace-linter {:sha "25f797330742aad0d6b5c86f763e9d306157a933"}}
```

Fast multithreaded and customizable linter that checks files for trailing whitespace, tabs, files that don't end in
newlines, files that end in blank lines, [Unicode characters that look maddeningly similar to ASCII
ones](https://github.com/camsaul/emacs-unicode-troll-stopper), and invisible Unicode characters. Written in Clojure,
but works on any sort of text file.

![demo](https://user-images.githubusercontent.com/1455846/128442912-e5b8c4bf-e2b9-41ec-bf99-30c1a1fa379f.gif)

# Standalone Usage

Requires the Clojure CLI (1.10.3.905 or higher). Install it using the instructions
[here](https://clojure.org/guides/getting_started) if you haven't already.

```
clojure -Sdeps \
  '{:aliases {:whitespace-linter {:deps {com.github.camsaul/whitespace-linter {:sha "25f797330742aad0d6b5c86f763e9d306157a933"}}
                                  :ns-default whitespace-linter}}}' \
  -T:whitespace-linter lint
```

# Adding to `deps.edn`

Add it to your `deps.edn`:

```clj
{:aliases
 {:whitespace-linter
  {:deps       {com.github.camsaul/whitespace-linter {:sha "25f797330742aad0d6b5c86f763e9d306157a933"}}
   :ns-default whitespace-linter}}}
```

and run it:

```
clj -T:whitespace-linter lint
```

# Configuration

You can configure the linter by setting `:exec-args` in the `deps.edn` alias, or by passing them as arguments to
`-T:whitespace-linter lint`:

```
clj -T:whitespace-linter lint :paths src
```

```clj
{:aliases
 {:whitespace-linter
  {:deps       {com.github.camsaul/whitespace-linter {:sha "25f797330742aad0d6b5c86f763e9d306157a933"}}
   :ns-default whitespace-linter
   :exec-args  {:paths            ["src" "test" "resources"]
                :include-patterns ["\\.clj.?$" "\\.jsx?$" "\\.edn$" "\\.yaml$" "\\.json$" "\\.html$"]
                :exclude-patterns ["resources/i18n/.*\\.edn$"]}}}}
```

Several options are currently supported:

| Option | Default | Description |
| --- | --- | --- |
| `:paths` | `./` | Directory(ies) or filename(s) to search for files to lint in. |
| `:include-patterns` | `[#"."]` | File paths that don't match at least one of these patterns will be ignored. |
| `:exclude-patterns` | `nil` | File paths that match any of these patterns will be ignored. |
| `:max-file-size-kb` | `1024` | Files over this size will be ignored. |

`:paths` accepts either strings, symbols, or a collection of multiple strings/symbols.

`:include-patterns` and `:exclude-patterns` accept either Strings or regex literals (regex literals cannot be embedded in EDN, so use string equivalents instead).

# Extensibility

The code uses [Methodical](https://github.com/camsaul/methodical) under the hood for easy extensibility. It takes
advantage of the
[`concat-method-combination`](https://cljdoc.org/d/methodical/methodical/0.12.0/api/methodical.core#concat-method-combination)
which calls *every* matching multimethod, and concatenates the results; and the
[`everything-dispatcher`](https://cljdoc.org/d/methodical/methodical/0.12.0/api/methodical.core#everything-dispatcher),
which considers every method implementation to be matching regardless of the arguments passed in.

### Adding Linters

Adding a new linter is as easy as writing a new multimethod:

```clj
;; linters/my_project/linters/whitespace_linter.clj
(ns my-project.linters.whitespace-linter
  (:require [methodical.core :as m]
            [whitespace-linter :as wsl]))

(m/defmethod wsl/lint-char ::no-capital-as
  [ch options]
  (when (= ch \A)
    [{:message "No capital A's are allowed in this project!"
      :linter ::no-capital-as}]))

(defn lint [options]
  (wsl/lint options))
```

Linters should return a sequence of error maps with the keys `:message` and `:linter` for any errors they decide
exist. `options` are those passed in to the command via the CLI or `:exec-args` in the `deps.edn` file.

Put your customizations in a new file that acts as a wrapper for `whitespace-linter`, and use it in its place. Update
your `deps.edn` to use your new namespace:

```clj
{:aliases
 {:whitespace-linter
  {:deps       {com.github.camsaul/whitespace-linter {:sha "25f797330742aad0d6b5c86f763e9d306157a933"}}
   :ns-default my-project.linters.whitespace-linter
   :paths      ["linters"]}}}
```

That's it! Line and column information is added to the output automatically (where applicable):

```
$ clj -T:whitespace-linter lint :paths naughty.clj
Finding matching files...
Linting 1 files...
1/1   100% [==================================================]  ETA: 00:00
Linted 1 files in 0.0 seconds.
Found 4 errors
naughty.clj:1:5 Found Unicode character \u1d21 'ᴡ' that looks way too similar to ASCII 'w' (:character/confusing-unicode-character)
naughty.clj:4:5 No capital A's are allowed in this project! (:my-project.linters.whitespace-linter/no-capital-as)
naughty.clj:4:9 No capital A's are allowed in this project! (:my-project.linters.whitespace-linter/no-capital-as)
naughty.clj:4:23 No capital A's are allowed in this project! (:my-project.linters.whitespace-linter/no-capital-as)
```

There are three methods you can implement (as many times as you want, of course) to add new linters, depending on
which situation is most appropriate:

```clj
;; called once for each character
(lint-char ^Character ch options)

;; called once for each line. Line DOES NOT include newlines at the end -- use lint-file if you need those
(lint-line ^String line options)

;; called once for each file
(lint-file ^java.io.File file options)
```

### Removing Built-In Linters

You can also remove built-in linters using Methodical's [`remove-primary-method!`](https://cljdoc.org/d/methodical/methodical/0.12.0/api/methodical.core#remove-primary-method!):

```clj
(m/remove-primary-method! #'wsl/lint-char :character/confusing-unicode-character)
```

### Selectively Disabling Linters

There is not currently a way to selectively disable certain linters for certain files, altho it seems like it wouldn't
be to hard to add... PRs are welcome.

# Add it as a GitHub Action

Here's an example GitHub action configuration to add the whitespace linter to your project: https://github.com/metabase/metabase/blob/30b54faa9599bff8d17bb46bef1b838de5334135/.github/workflows/whitespace.yml

# Interactive/Programmatic Usage

For interactive or programmatic usage, you can use `whitespace-linter/lint-interactive` instead of `lint`. This
displays REPL-friendly output (i.e., no progress bar), returns errors in a machine-friendly format, and skips calls to
`System/exit` when linting is finished.

# License

Copyright © 2021 Cam Saul.

Distributed under the [Eclipse Public
License](https://raw.githubusercontent.com/metabase/camsaul/whitespace-linter/LICENSE), same as Clojure.
