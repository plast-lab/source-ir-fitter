#!/usr/bin/env bash

set -e

echo "* Updating parsers submodule..."
git submodule update --init

echo "* Building Kotlin parser dependency..."
cd grammars-v4
mvn install --pl kotlin/kotlin-formal --am
cd ..
