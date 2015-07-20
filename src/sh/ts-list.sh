#!/bin/sh

# list all subjects in a triplestore

BASE=`dirname $0`
. $BASE/common.sh

TS=$1
java -cp $CP edu.ucsd.library.dams.commands.TripleStoreList $PROPS $TS
if [ $? != 0 ]; then
    exit 1
fi
