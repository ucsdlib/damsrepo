#!/bin/sh

# shutdown triplestore

BASE=`dirname $0`
. $BASE/common.sh

TS=$1
java -cp $CP edu.ucsd.library.dams.commands.TripleStoreShutdown $PROPS $TS
if [ $? != 0 ]; then
    exit 1
fi
