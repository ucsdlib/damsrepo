#!/bin/sh

# list all files in an object

BASE=`dirname $0`
source $BASE/common.sh

FS=$1
OBJ=$2
CMP=$3
java -cp $CP edu.ucsd.library.dams.commands.FileStoreList $PROPS $FS $OBJ $CMP
