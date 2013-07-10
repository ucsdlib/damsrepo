#!/bin/sh

# copy an object within a triplestore

BASE=`dirname $0`
source $BASE/common.sh

FS=$1
SRC=$2
DST=$3
java -cp $CP edu.ucsd.library.dams.commands.FileStoreCopy $PROPS $FS $SRC $DST
