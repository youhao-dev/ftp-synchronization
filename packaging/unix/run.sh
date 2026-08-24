#!/usr/bin/env sh
# 从软件目录启动，确保规则和设置文件保存在压缩包解压目录。
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$SCRIPT_DIR" || exit 1
exec ./jre/bin/java -jar ftp-synchronization.jar
