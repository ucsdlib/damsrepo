#!/bin/sh

# upload a file to a filestore

BASE=`dirname $0`
. $BASE/common.sh
DIR=$DAMS_HOME/tmp

FS=$1
OBJ=$2
CMP=$3
FILE=$4
SRC=$5
echo fs-upload.sh $FS $OBJ $CMP $FILE $SRC
java -cp $CP edu.ucsd.library.dams.commands.FileStoreUpload $PROPS $FS $OBJ $CMP $FILE $SRC
