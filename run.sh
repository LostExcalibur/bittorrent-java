#!/bin/sh

set -e

mvn -B --quiet package -Ddir=target
exec java -ea -jar target/java_bittorrent.jar "$@"
