#!/bin/sh

# delete all triples from a triplestore

BASE=`dirname $0`
source $BASE/common.sh

TS=$1
java -cp $CP edu.ucsd.library.dams.commands.TripleStoreClear $PROPS $TS
if [ $? != 0 ]; then
    exit 1
fi
