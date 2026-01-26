#!/bin/bash
set -a
source ./config.env
set +a

java -jar target/AppletMerger-1.0-jar-with-dependencies.jar $UPDATED_RES_DIR $CURRENT_RES_DIR $BACKUP_DIR $CREATE_BACKUP_SUBDIR $CREATE_CHANGELOG $FILE_ENCODING