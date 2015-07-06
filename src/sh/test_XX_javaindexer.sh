#!/bin/sh

# load env
BASE=`dirname $0`
. $BASE/common.sh

# copy solr config
if [ "$INDEXER" -a -d $INDEXER ]; then
	SOLR_CONF=solr/blacklight/conf/
	if [ -d $SOLR_CONF ]; then
		cp -v $INDEXER/solr_conf/conf/* $SOLR_CONF
	else
		echo "$SOLR_CONF doesn't exist, can't update Solr config!"
	fi
fi

# reindex sample records
$BASE/solr-javaindexer.sh $BASE/../sample/sample.arks
