@echo off
cd /d "%~dp0"
java -cp "combo6beat.jar;libs\*" game.Main %*
pause
