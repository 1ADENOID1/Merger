@echo off

for /f "usebackq delims=" %%i in ("config.env") do (set "%%i")

java -jar target/AppletMerger-1.0-jar-with-dependencies.jar Parser %UPDATED_RES_DIR% %CURRENT_RES_DIR% %BACKUP_DIR% %CREATE_BACKUP_SUBDIR% %CREATE_CHANGELOG% %FILE_ENCODING%
pause