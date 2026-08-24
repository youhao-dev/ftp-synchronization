#!/usr/bin/env sh
# macOS 可双击本文件启动，也可在终端中执行。
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$SCRIPT_DIR" || exit 1
exec ./jre/bin/java -jar ftp-synchronization.jar
