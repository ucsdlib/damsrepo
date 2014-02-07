#!/bin/sh

# index records
JMS_URL=$1
JMS_QUEUE=$2

$BASE/solr-javaindexer.sh $JMS_URL $JMS_QUEUE
if [ $? != 0 ]; then
    exit 1
fi
