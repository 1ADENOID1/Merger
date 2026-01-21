set -a
source config.env
set +a

java -jar target/JSONpars-1.0-SNAPSHOT-jar-with-dependencies.jar Parser $UPDATED_RES_DIR $CURRENT_RES_DIR $BACKUP_DIR $CREATE_BACKUP_SUBDIR $CREATE_CHANGELOG $FILE_ENCODING
pause
