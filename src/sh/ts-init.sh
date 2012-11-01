#!/bin/sh

# initialize a new triplestore

BASE=`dirname $0`
source $BASE/common.sh

TS=$1
java -cp $CP edu.ucsd.library.dams.commands.TripleStoreInit $PROPS $TS
