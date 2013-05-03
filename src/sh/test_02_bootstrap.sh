#!/bin/sh

# load objects from scratch
BASE=`dirname $0`
source $BASE/common.sh

BASE=`dirname $0`
source $BASE/common.sh
DAMSID="http://library.ucsd.edu/ark:/20775/"
ERRORS=0
#FS=openStack
FS=localStore

function mintark
{
	XML=`curl -u $USER:$PASS -f -s -X POST http://localhost:8080/dams/api/next_id`
	if [ $? != 0 ]; then
		ERRORS=$(( $ERRORS + 1 ))
	fi
	echo $XML | sed -e's/.*<response><ids><value>//' -e 's/<\/value><\/ids>.*//'
}

# create a sample unit
REP_ARK=`mintark`
echo "ARK: $REP_ARK"
JSON=`echo "[{\"subject\":\"$REP_ARK\",\"predicate\":\"dams:unitName\",\"object\":\"\\\\\"Test Unit\\\\\"\"},{\"subject\":\"$REP_ARK\",\"predicate\":\"rdf:type\",\"object\":\"dams:Unit\"},{\"subject\":\"$REP_ARK\",\"predicate\":\"dams:unitURI\",\"object\":\"http://test.com\"}]" | urlencode`
echo "http://localhost:8080/dams/api/objects/$REP_ARK?adds=..."
curl -u $USER:$PASS -f -X POST "http://localhost:8080/dams/api/objects/$REP_ARK?adds=$JSON"
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

# create a sample collection
COL_ARK=`mintark`
echo "ARK: $COL_ARK"
JSON=`echo "[{\"subject\":\"$COL_ARK\",\"predicate\":\"dams:title\",\"object\":\"node1\"},{\"subject\":\"node1\",\"predicate\":\"rdf:type\",\"object\":\"dams:Title\"},{\"subject\":\"node1\",\"predicate\":\"rdf:value\",\"object\":\"\\\\\"Test Collection\\\\\"\"},{\"subject\":\"$COL_ARK\",\"predicate\":\"rdf:type\",\"object\":\"dams:Collection\"}]" | urlencode`
echo "http://localhost:8080/dams/api/objects/$COL_ARK?adds=..."
curl -u $USER:$PASS -f -X POST "http://localhost:8080/dams/api/objects/$COL_ARK?adds=$JSON"
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

# create two sample object
for i in 1 2; do
	OBJ_ARK=`mintark`
	echo "ARK: $OBJ_ARK"
	JSON=`echo "[{\"subject\":\"$OBJ_ARK\",\"predicate\":\"dams:title\",\"object\":\"node1\"},{\"subject\":\"node1\",\"predicate\":\"rdf:type\",\"object\":\"dams:Title\"},{\"subject\":\"node1\",\"predicate\":\"rdf:value\",\"object\":\"\\\\\"Test Object $i\\\\\"\"},{\"subject\":\"$OBJ_ARK\",\"predicate\":\"rdf:type\",\"object\":\"dams:Object\"},{\"subject\":\"$OBJ_ARK\",\"predicate\":\"dams:unit\",\"object\":\"$DAMSID$REP_ARK\"},{\"subject\":\"$OBJ_ARK\",\"predicate\":\"dams:collection\",\"object\":\"$DAMSID$COL_ARK\"}]" | urlencode`
	echo "http://localhost:8080/dams/api/objects/$OBJ_ARK?adds=..."
	curl -u $USER:$PASS -f -X POST "http://localhost:8080/dams/api/objects/$OBJ_ARK?adds=$JSON"
	if [ $? != 0 ]; then
		ERRORS=$(( $ERRORS + 1 ))
	fi
	echo

	# attach a file to the object (multipart upload)
	FILE=src/sample/files/20775-bb01010101-1-1.tif
	SRCPATH=`dirname $FILE`
	echo $FILE
	echo "http://localhost:8080/dams/api/files/$OBJ_ARK/1/1.tif"
	curl -u $USER:$PASS -f -i -X POST -F sourcePath="$SRCPATH" -F file=@$FILE http://localhost:8080/dams/api/files/$OBJ_ARK/1/1.tif?fs=$FS
	if [ $? != 0 ]; then
		ERRORS=$(( $ERRORS + 1 ))
	fi
	echo

	# attach a file to the object (staged upload)
	FILE=darry/fiji_jpg/20775-bb1400540n-1-3.jpg
	echo $FILE
	echo "http://localhost:8080/dams/api/files/$OBJ_ARK/2/1.jpg"
	curl -u $USER:$PASS -f -i -X POST -F local=$FILE http://localhost:8080/dams/api/files/$OBJ_ARK/2/1.jpg?fs=$FS
	if [ $? != 0 ]; then
		ERRORS=$(( $ERRORS + 1 ))
	fi
	echo

	# generate derivatives
	SIZES="-F size=2 -F size=3 -F size=4 -F size=5"
	echo "http://localhost:8080/dams/api/files/$OBJ_ARK/1/1.tif/derivatives"
	curl -u $USER:$PASS -f -i -L -X POST $SIZES http://localhost:8080/dams/api/files/$OBJ_ARK/1/1.tif/derivatives?fs=$FS
	if [ $? != 0 ]; then
		ERRORS=$(( $ERRORS + 1 ))
	fi
	echo
done

echo ERRORS: $ERRORS
exit $ERRORS
