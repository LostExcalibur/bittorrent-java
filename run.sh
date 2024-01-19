#!/bin/sh

set -e

mvn -B --quiet package -Ddir=target
exec java -jar target/java_bittorrent.jar "$@"
