#!/bin/sh

# load env
BASE=`dirname $0`
source $BASE/common.sh

IDS="$IDS bb02020202" # units
IDS="$IDS bb48484848"
IDS="$IDS bb07070707" # event
IDS="$IDS bd24241158" # collections
IDS="$IDS bb03030303"
IDS="$IDS bd5905304g"
IDS="$IDS bb01010101" # complex objects
IDS="$IDS bb80808080"
IDS="$IDS bb52572546"
IDS="$IDS bd0922518w" # format sampler complex object
IDS="$IDS bd5939745h" #   simple object, format= audio
IDS="$IDS bd46428055" #   simple object, format=data
IDS="$IDS bd3379993m" #   simple object, format=image
IDS="$IDS bd2083054q" #   simple object, format=text
IDS="$IDS bd0786115s" #   simple object, format=video
IDS="$IDS bd22194583" # simple object
IDS="$IDS bd3516400n" # assembled collection
IDS="$IDS bd48133407" # provenance collection
IDS="$IDS bd6110278b" # provenance collection part
IDS="$IDS bd44383589" # ucsd-only collection
IDS="$IDS bd5735300v" # curator-only collection
IDS="$IDS bd0683587d" # scheme (naf)
IDS="$IDS bd9386739x" # scheme (lcsh)
IDS="$IDS bd5905379f" # carousel
IDS="$IDS bd3413814d"
IDS="$IDS bd21510035"
IDS="$IDS bd08540633"
IDS="$IDS bd95572101"
IDS="$IDS bd82602702"
IDS="$IDS bd6963330m"
IDS="$IDS bd4369453c"
IDS="$IDS bd5666392f"
IDS="$IDS bd3106642r"
IDS="$IDS bd18097029"
IDS="$IDS bd0410344f" # language
IDS="$IDS bd91134949"
IDS="$IDS bb05050505" # copyright

# solr indexing
if [ "$INDEXER" -a -d $INDEXER ]; then
	cd $INDEXER
	bundle exec bin/damsolrizer-single --hydra_home . $IDS
else
	echo "Couldn't find indexer!"
fi
