#!/bin/sh

# test selective updates and deletes
BASE=`dirname $0`
source $BASE/common.sh

ERRORS=0

DAMSID="http://library.ucsd.edu/ark:/20775/"
ERRORS=0

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
JSON=`echo "[{\"subject\":\"$REP_ARK\",\"predicate\":\"dams:unitName\",\"object\":\"\\\\\"Test Unit 2\\\\\"\"},{\"subject\":\"$REP_ARK\",\"predicate\":\"rdf:type\",\"object\":\"dams:Unit\"},{\"subject\":\"$REP_ARK\",\"predicate\":\"dams:unitURI\",\"object\":\"http://test.com\"}]" | urlencode`
echo "http://localhost:8080/dams/api/objects/$REP_ARK?adds=XXX"
curl -u $USER:$PASS -f -X POST "http://localhost:8080/dams/api/objects/$REP_ARK?adds=$JSON"
if [ $? != 0 ]; then
    ERRORS=$(( $ERRORS + 1 ))
fi
echo

# create a sample collection
COL_ARK=`mintark`
echo "ARK: $COL_ARK"
JSON=`echo "[{\"subject\":\"$COL_ARK\",\"predicate\":\"dams:title\",\"object\":\"node1\"},{\"subject\":\"node1\",\"predicate\":\"rdf:type\",\"object\":\"dams:Title\"},{\"subject\":\"node1\",\"predicate\":\"rdf:value\",\"object\":\"\\\\\"Test Collection\\\\\"\"},{\"subject\":\"$COL_ARK\",\"predicate\":\"rdf:type\",\"object\":\"dams:Collection\"}]" | urlencode`
echo "http://localhost:8080/dams/api/objects/$COL_ARK?adds=XXX"
curl -u $USER:$PASS -f -X POST "http://localhost:8080/dams/api/objects/$COL_ARK?adds=$JSON"
if [ $? != 0 ]; then
    ERRORS=$(( $ERRORS + 1 ))
fi
echo

OBJ=`mintark`
echo "ARK: $OBJ"
JSON=`echo "[{\"subject\":\"$OBJ\",\"predicate\":\"dams:title\",\"object\":\"node1\"},{\"subject\":\"node1\",\"predicate\":\"rdf:type\",\"object\":\"dams:Title\"},{\"subject\":\"node1\",\"predicate\":\"rdf:value\",\"object\":\"\\\\\"Test Object $i\\\\\"\"},{\"subject\":\"$OBJ\",\"predicate\":\"rdf:type\",\"object\":\"dams:Object\"},{\"subject\":\"$OBJ\",\"predicate\":\"dams:unit\",\"object\":\"$DAMSID$REP_ARK\"},{\"subject\":\"$OBJ\",\"predicate\":\"dams:collection\",\"object\":\"$DAMSID$COL_ARK\"}]" | urlencode`
echo "http://localhost:8080/dams/api/objects/$OBJ?adds=XXX"
curl -u $USER:$PASS -f -X POST "http://localhost:8080/dams/api/objects/$OBJ?adds=$JSON"
if [ $? != 0 ]; then
    ERRORS=$(( $ERRORS + 1 ))
fi
echo

# post selective_update_1.rdf.xml
# => date and original note added
echo "Augment object metadata (mode=add)"
cat src/sample/test/selective_update_1.rdf.xml | sed -e"s/__OBJ__/$OBJ/" > tmp/tmp3.rdf.xml
curl -u $USER:$PASS -f -X PUT -F mode=add -F file=@tmp/tmp3.rdf.xml http://localhost:8080/dams/api/objects/$OBJ
if [ $? != 0 ]; then
    ERRORS=$(( $ERRORS + 1 ))
fi
echo

# selective delete dams:note
# => date remains, note deleted
echo "Selective delete"
curl -u $USER:$PASS -f -X DELETE "http://localhost:8080/dams/api/objects/$OBJ/1/selective?predicate=dams%3Anote"
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

# post selective_update_2.rdf.xml
# => date remains, new note added
echo "Augment object metadata (mode=add)"
cat src/sample/test/selective_update_2.rdf.xml | sed -e"s/__OBJ__/$OBJ/" > tmp/tmp4.rdf.xml
curl -u $USER:$PASS -f -X PUT -F mode=add -F file=@tmp/tmp4.rdf.xml http://localhost:8080/dams/api/objects/$OBJ
if [ $? != 0 ]; then
    ERRORS=$(( $ERRORS + 1 ))
fi
echo

echo ERRORS: $ERRORS
exit $ERRORS
