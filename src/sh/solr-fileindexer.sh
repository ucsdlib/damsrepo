#!/bin/sh

# index records
DAMSPAS=$1
SOLR=$2
ARKS=$3

BASE=`dirname $0`
source $BASE/common.sh

java -cp $CP edu.ucsd.library.dams.solr.SolrIndexer $DAMSPAS $SOLR $ARKS
if [ $? != 0 ]; then
    exit 1
fi
