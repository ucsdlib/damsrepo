#!/bin/sh

# dump all triples in a triplestore to standard output

BASE=`dirname $0`
source $BASE/common.sh

TS=$1
java -cp $CP edu.ucsd.library.dams.commands.TripleStoreDump $PROPS $TS
