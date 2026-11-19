#!/usr/bin/env bash
cd "$(dirname "$0")"
exec java -cp "combo6beat.jar:libs/*" game.Main "$@"
