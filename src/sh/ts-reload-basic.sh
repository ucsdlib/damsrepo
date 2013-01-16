#!/bin/sh

# clear triplestore and load predicate triples only
DAMSID="http://library.ucsd.edu/ark:/20775/"

function mintark
{
	XML=`curl -s -X POST http://localhost:8080/dams/api/next_id`
	echo $XML | sed -e's/.*<response><ids><value>//' -e 's/<\/value><\/ids>.*//'
}

BASE=`dirname $0`
source $BASE/common.sh

TS=$1
ES=$2
if [ ! "$TS" ]; then
	TS=dams
fi
if [ ! "$ES" ]; then
	ES=events
fi

# initialize object triplestore
$BASE/ts-clear.sh $TS
$BASE/ts-load.sh $TS src/sample/predicates/

# initialize event triplestore
$BASE/ts-clear.sh $ES
$BASE/ts-load.sh $ES src/sample/predicates/

# create a sample repository
ARK1=`mintark`
JSON=`echo "[{\"subject\":\"$ARK1\",\"predicate\":\"dams:repositoryName\",\"object\":\"\\\\\"Test Repository 2\\\\\"\"},{\"subject\":\"$ARK1\",\"predicate\":\"rdf:type\",\"object\":\"dams:Repository\"},{\"subject\":\"$ARK1\",\"predicate\":\"dams:repositoryURI\",\"object\":\"http://test.com\"}]" | urlencode`
curl -X POST "http://localhost:8080/dams/api/objects/$ARK1?adds=$JSON"
echo

# create a sample collection
ARK2=`mintark`
JSON=`echo "[{\"subject\":\"$ARK2\",\"predicate\":\"dams:title\",\"object\":\"node1\"},{\"subject\":\"node1\",\"predicate\":\"rdf:type\",\"object\":\"dams:Title\"},{\"subject\":\"node1\",\"predicate\":\"rdf:value\",\"object\":\"\\\\\"Test Collection\\\\\"\"},{\"subject\":\"$ARK2\",\"predicate\":\"rdf:type\",\"object\":\"dams:Collection\"}]" | urlencode`
curl -X POST "http://localhost:8080/dams/api/objects/$ARK2?adds=$JSON"
echo

# create a sample object
ARK3=`mintark`
JSON=`echo "[{\"subject\":\"$ARK3\",\"predicate\":\"dams:title\",\"object\":\"node1\"},{\"subject\":\"node1\",\"predicate\":\"rdf:type\",\"object\":\"dams:Title\"},{\"subject\":\"node1\",\"predicate\":\"rdf:value\",\"object\":\"\\\\\"Test Object\\\\\"\"},{\"subject\":\"$ARK3\",\"predicate\":\"rdf:type\",\"object\":\"dams:Object\"},{\"subject\":\"$ARK3\",\"predicate\":\"dams:repository\",\"object\":\"$DAMSID$ARK1\"},{\"subject\":\"$ARK3\",\"predicate\":\"dams:collection\",\"object\":\"$DAMSID$ARK2\"}]" | urlencode`
curl -X POST "http://localhost:8080/dams/api/objects/$ARK3?adds=$JSON"
echo

# attach a file to the object
FILE=$HOME/src/dams/src/sample/files/20775-bb01010101-1-1.tif
SRCPATH=`dirname $FILE`
echo $FILE
curl -i -X POST -F sourcePath="$SRCPATH" -F file=@$FILE http://localhost:8080/dams/api/files/$ARK3/1.tif
echo

# generate derivatives
SIZES="-F size=2 -F size=3 -F size=4 -F size=5"
curl -i -L -X POST $SIZES http://localhost:8080/dams/api/files/$ARK3/1.tif/derivatives
echo
