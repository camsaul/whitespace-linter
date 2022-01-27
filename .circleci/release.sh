#! /usr/bin/env bash

set -euxo pipefail

script_directory=`dirname "${BASH_SOURCE[0]}"`
cd "$script_directory/.."

version=$(date --utc --iso-8601=minutes | sed 's/+00:00//' | sed 's/[-T:+]/./g')

clojure -X:jar :version "\"$version\""

clojure -X:deploy :artifact target/whitespace-linter.jar
