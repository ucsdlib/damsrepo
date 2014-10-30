#!/bin/sh

# load env
BASE=`dirname $0`
source $BASE/common.sh

IDS="$@"
if [ ! "$IDS" ]; then
	IDS="$IDS bb01010101" # complex objects
	IDS="$IDS bb80808080"
	IDS="$IDS bb52572546"
	IDS="$IDS bd0922518w" # format sampler complex object
	IDS="$IDS bd5939745h" #   simple object, format= audio
	IDS="$IDS bd46428055" #   simple object, format=data
	IDS="$IDS bd3379993m" #   simple object, format=image
	IDS="$IDS bd86037516" #   simple object, format=image(local access)
	IDS="$IDS bd2083054q" #   simple object, format=text
	IDS="$IDS bd0786115s" #   simple object, format=video
	IDS="$IDS bd51895934" # provenance collection member
	IDS="$IDS bd64524003" # provenance collection part member
	IDS="$IDS bd22194583" # simple object
	IDS="$IDS bb02020202" # units
	IDS="$IDS bb48484848"
	IDS="$IDS bd24241158" # collections
	IDS="$IDS bb03030303"
	IDS="$IDS bd5905304g"
	IDS="$IDS bb24242424"
	IDS="$IDS bb25252525"
	IDS="$IDS bd3516400n" # assembled collection
	IDS="$IDS bd48133407" # provenance collection
	IDS="$IDS bd6110278b" # provenance collection part
	IDS="$IDS bd44383589" # ucsd-only collection
	IDS="$IDS bd5735300v" # curator-only collection
	IDS="$IDS bd0683587d" # scheme (naf)
	IDS="$IDS bd9386739x" # scheme (lcsh)
	IDS="$IDS bd0410344f" # language=English
	IDS="$IDS bd91134949" # language=French
	IDS="$IDS bb05050505" # copyright=under copyright
	IDS="$IDS bd0513099p" # copyright=public domain
	IDS="$IDS bb2628975j" # embargo object
	IDS="$IDS bd5774559x" # object linked to curator-only collection
fi

# solr indexing
if [ "$INDEXER" -a -d $INDEXER ]; then
	SOLR_CONF=solr/blacklight/conf/
	if [ -d $SOLR_CONF ]; then
		cp -v $INDEXER/solr_conf/conf/* $SOLR_CONF
	else
		echo "$SOLR_CONF doesn't exist, can't update Solr config!"
	fi
	cd $INDEXER
	bundle exec bin/damsolrizer-single --hydra_home . $IDS
else
	echo "Couldn't find indexer!"
fi
