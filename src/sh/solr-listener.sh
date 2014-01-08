#!/bin/sh

# index records
DAMSPAS=$1
SOLR=$2
JMS_URL=$3
JMS_QUEUE=$4

BASE=`dirname $0`
source $BASE/common.sh

java -cp $CP edu.ucsd.library.dams.solr.SolrIndexer $DAMSPAS $SOLR $JMS_URL $JMS_QUEUE
if [ $? != 0 ]; then
    exit 1
fi
