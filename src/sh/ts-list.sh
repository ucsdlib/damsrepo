#!/bin/sh

# list all subjects in a triplestore

BASE=`dirname $0`
source $BASE/common.sh

TS=$1
java -cp $CP edu.ucsd.library.dams.commands.TripleStoreList $PROPS $TS
