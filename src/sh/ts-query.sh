#!/bin/sh

# count the number of triples in a triplestore

BASE=`dirname $0`
source $BASE/common.sh

TS=$1
Q="$2"
#echo "CP: $CP"
java -cp $CP edu.ucsd.library.dams.commands.TripleStoreQuery $PROPS $TS "$Q"
if [ $? != 0 ]; then
    exit 1
fi
