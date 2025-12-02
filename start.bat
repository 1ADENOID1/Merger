set DISTR_DIR=C:\\Users\\azaytsev\\Desktop\\serv\\config
set USER_DIR=C:\\Users\\azaytsev\\Desktop\\serv1\\config
set BACKUP_DIR=C:\\Users\\azaytsev\\Desktop\\userBackup
set CREATE_BACKUP_SUBDIR=true
set FILE_ENCODING=UTF-8

java -jar target/JSONpars-1.0-SNAPSHOT-jar-with-dependencies.jar Parser %DISTR_DIR% %USER_DIR% %BACKUP_DIR% %CREATE_BACKUP_SUBDIR% %FILE_ENCODING%
pause