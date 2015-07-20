#!/bin/sh

# dump all triples in a triplestore to standard output

BASE=`dirname $0`
. $BASE/common.sh

TS=$1
java -cp $CP edu.ucsd.library.dams.commands.TripleStoreDump $PROPS $TS
if [ $? != 0 ]; then
    exit 1
fi
