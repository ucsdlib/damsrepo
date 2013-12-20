#!/bin/sh

# index records
JMS_URL=$1
JMS_QUEUE=$2
DAMSPAS=$3
SOLR=$4

BASE=`dirname $0`
source $BASE/common.sh

java -cp $CP edu.ucsd.library.dams.solr.SolrIndexer $JMS_URL $JMS_QUEUE $DAMSPAS $SOLR
if [ $? != 0 ]; then
    exit 1
fi
