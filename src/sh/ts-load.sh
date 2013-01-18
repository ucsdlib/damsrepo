#!/bin/sh

# bulk load N-Triples or RDF/XML files into a triplestore

BASE=`dirname $0`
source $BASE/common.sh

TS=$1
shift
java -cp $CP edu.ucsd.library.dams.commands.TripleStoreLoad $PROPS $TS "$@"
if [ $? != 0 ]; then
    exit 1
fi
