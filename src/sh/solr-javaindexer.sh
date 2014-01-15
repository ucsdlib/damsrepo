#!/bin/sh

BASE=`dirname $0`
source $BASE/common.sh

DAMSPAS=$DAMSPAS_URL/solrdoc/
SOLR=$SOLR_URL/blacklight

java -cp $CP edu.ucsd.library.dams.solr.SolrIndexer $DAMSPAS $SOLR $@
if [ $? != 0 ]; then
    exit 1
fi
