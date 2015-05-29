#!/bin/sh

# bulk load N-Triples or RDF/XML files into a triplestore

BASE=`dirname $0`
. $BASE/common.sh
CLASS=edu.ucsd.library.dams.commands.TripleStoreLoad

TS=$1
shift
echo "Loading data from $@"
java -cp $CP $CLASS $PROPS $TS "" "" "$@"
if [ $? != 0 ]; then
    exit 1
fi
