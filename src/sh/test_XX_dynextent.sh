#!/bin/sh

# test selective updates and deletes
BASE=`dirname $0`
source $BASE/common.sh

ERRORS=0

DAMSID="http://library.ucsd.edu/ark:/20775/"
ERRORS=0

function mintark
{
    XML=`curl -s -u $USER:$PASS -f -X POST $URL/api/next_id`
    if [ $? != 0 ]; then
        ERRORS=$(( $ERRORS + 1 ))
    fi
    echo $XML | sed -e's/.*<response><ids><value>//' -e 's/<\/value><\/ids>.*//'
}
function addobj
{
	OBJ_ARK=`mintark`
	echo "Adding object: $OBJ_ARK"
	JSON=`echo "[{\"subject\":\"$OBJ_ARK\",\"predicate\":\"rdf:type\",\"object\":\"dams:Object\"},{\"subject\":\"$OBJ_ARK\",\"predicate\":\"dams:assembledCollection\",\"object\":\"$DAMSID$COL_ARK\"}]" | urlencode`
	curl -s -u $USER:$PASS -f -X POST "$URL/api/objects/$OBJ_ARK?adds=$JSON"
	if [ $? != 0 ]; then
    	ERRORS=$(( $ERRORS + 1 ))
	fi
	echo
}

# create a collection
COL_ARK=`mintark`
echo "Adding collection: $COL_ARK"
JSON=`echo "[{\"subject\":\"$COL_ARK\",\"predicate\":\"rdf:type\",\"object\":\"dams:AssembledCollection\"}]" | urlencode`
curl -s -u $USER:$PASS -f -X POST "$URL/api/objects/$COL_ARK?adds=$JSON"
if [ $? != 0 ]; then
    ERRORS=$(( $ERRORS + 1 ))
fi
echo

# add first object
addobj

# check for extent=1
COUNT=`curl -s $URL/api/objects/$COL_ARK | grep -c "1 digital object."`
if [ $COUNT = 1 ]; then
	echo "extent = 1"
else
	ERRORS=$(( $ERRORS + 1 ))
fi

# add another object
addobj

# check for extent=2
COUNT=`curl -s $URL/api/objects/$COL_ARK | grep -c "2 digital objects."`
if [ $COUNT = 1 ]; then
	echo "extent = 2"
else
	ERRORS=$(( $ERRORS + 1 ))
fi

echo ERRORS: $ERRORS
exit $ERRORS
