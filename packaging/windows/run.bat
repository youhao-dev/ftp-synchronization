@echo off
rem 从软件目录启动，确保规则和设置文件保存在压缩包解压目录。
cd /d "%~dp0"
"jre\bin\javaw.exe" -jar ftp-synchronization.jar
