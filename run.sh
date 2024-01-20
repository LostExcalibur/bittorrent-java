#!/bin/sh

set -e

mvn -B --quiet package -Ddir=target
exec java -ea -jar target/bittorrent-client.jar "$@"
