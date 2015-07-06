#!/bin/sh

# download a file from an object

BASE=`dirname $0`
. $BASE/common.sh
DIR=$DAMS_HOME/tmp

FS=$1
OBJ=$2
CMP=$3
FILE=$4
DEST=$5
if [ ! "$DEST" ]; then
	DEST=$DIR/$OBJ-$CMP-$FILE
fi
echo fs-download.sh $FS $OBJ $CMP $FILE $DEST
java -cp $CP edu.ucsd.library.dams.commands.FileStoreDownload $PROPS $FS $OBJ $CMP $FILE $DEST
