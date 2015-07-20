#!/bin/sh

# bulk load N-Triples or RDF/XML files into a triplestore

BASE=`dirname $0`
. $BASE/common.sh
CLASS=edu.ucsd.library.dams.commands.TripleStoreLoad
TYPES=$BASE/valid-classes.txt
PREDS=$BASE/valid-properties.txt

TS=$1
shift
echo "Loading data from $@"
java -cp $CP $CLASS $PROPS $TS $TYPES $PREDS "$@"
if [ $? != 0 ]; then
    exit 1
fi
