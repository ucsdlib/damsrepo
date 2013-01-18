#!/bin/sh

ERRORS=0

# list repositories
echo "Listing repositories"
REPO_LIST=`curl -s -f http://localhost:8080/dams/api/repositories`
if [ $? != 0 ]; then
    ERRORS=$(( $ERRORS + 1 ))
fi

# parse repository list
REPO=`echo $REPO_LIST | perl -pe 's/></>\n</g' | grep "<repository>" | head -1 | perl -pe "s/<\/repository>//" | perl -pe "s/.*\///"`
echo "Repo: $REPO"

# list objects in the repository
echo "Listing objects in repository $REPO"
OBJ_LIST=`curl -s -f http://localhost:8080/dams/api/repositories/$REPO`
if [ $? != 0 ]; then
    ERRORS=$(( $ERRORS + 1 ))
fi

# parse object list
OBJ=`echo $OBJ_LIST | perl -pe 's/></>\n</g' | grep "<obj>" | head -1 | perl -pe "s/<\/obj>//" | perl -pe "s/.*\///"`
echo "Object: $OBJ"

# get basic object metadata
echo "Basic object metadata"
curl -s -f http://localhost:8080/dams/api/objects/$OBJ | sed -e's/<rdf:value>Test Object<\/rdf:value>/<rdf:value>Test Updated Title<\/rdf:value>/' > tmp/tmp.rdf.xml
if [ $? != 0 ]; then
    ERRORS=$(( $ERRORS + 1 ))
fi
echo

# replace object metadata (mode=all)
echo "Replace object metadata (mode=all)"
#XXX: multipart not being triggered...
curl -f -X PUT -F mode=all -F file=@tmp/tmp.rdf.xml http://localhost:8080/dams/api/objects/$OBJ
if [ $? != 0 ]; then
    ERRORS=$(( $ERRORS + 1 ))
fi
echo

# augment object metadata (mode=add)
echo "Augment object metadata (mode=add)"
cat src/sample/test/object_update_partial.rdf.xml | sed -e"s/__OBJ__/$OBJ/" > tmp/tmp2.rdf.xml
#XXX: multipart not being triggered...
curl -f -X PUT -F mode=add -F file=@tmp/tmp2.rdf.xml http://localhost:8080/dams/api/objects/$OBJ
if [ $? != 0 ]; then
    ERRORS=$(( $ERRORS + 1 ))
fi
echo

# replace a file
echo "Replace a file"
FILE_SRC=src/sample/test/test2.jpg
FILE_ID=2/1.jpg
curl -f -X PUT -F file=@$FILE_SRC http://localhost:8080/dams/api/files/$OBJ/$FILE_ID
if [ $? != 0 ]; then
    ERRORS=$(( $ERRORS + 1 ))
fi
echo

# regeneration file characterization metadata
echo "Regenerate file characterization metadata"
curl -f -X PUT http://localhost:8080/dams/api/files/$OBJ/$FILE_ID/characterize
if [ $? != 0 ]; then
    ERRORS=$(( $ERRORS + 1 ))
fi
echo


echo ERRORS: $ERRORS
exit $ERRORS
