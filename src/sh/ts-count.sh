#!/bin/sh

# count the number of triples in a triplestore

BASE=`dirname $0`
source $BASE/common.sh

TS=$1
java -cp $CP edu.ucsd.library.dams.commands.TripleStoreCount $PROPS $TS
