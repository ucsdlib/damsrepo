#!/bin/sh

# download a file from an object

BASE=`dirname $0`
source $BASE/common.sh
DIR=/pub/backup/tmp

FS=$1
OBJ=$2
CMP=$3
FILE=$4
DEST=$5
if [ ! "$DEST" ]; then
	DEST=$DIR/$OBJ-$CMP-$FILE
fi
java -cp $CP edu.ucsd.library.dams.commands.FileStoreDownload $PROPS $FS $OBJ $CMP $FILE $DEST
