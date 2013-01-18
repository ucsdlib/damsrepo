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
OBJ_RDF=`curl -f http://localhost:8080/dams/api/objects/$OBJ`
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo $OBJ_RDF

# get recursive object metadata
echo "Recursive object metadata"
curl -f http://localhost:8080/dams/api/objects/$OBJ/export
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

# test object existence
echo "Test object existence"
curl -f http://localhost:8080/dams/api/objects/$OBJ/exists
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

# metadata transform (retrieval only)
echo "Metadata transform (retrieval only)"
curl -f "http://localhost:8080/dams/api/objects/$OBJ/transform?recursive=true&xsl=solrindexer.xsl"
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

# metadata transform (save output as new file)
echo "Metadata transform (save output as new file)"
curl -f -X POST "http://localhost:8080/dams/api/objects/$OBJ/transform?recursive=true&xsl=solrindexer.xsl&dest=6.xml"
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

# metadata transform (save output as new file) - make sure file was created
echo "Metadata transform (save output as new file) - make sure file was created"
curl -f "http://localhost:8080/dams/api/files/$OBJ/6.xml"
if [ $? != 0 ]; then
	ERRORS=$(( $ERRORS + 1 ))
fi
echo

# find an event record
echo "Find an event record"
EVENT=`echo $OBJ_RDF | perl -pe 's/>/>\n/g' | grep "<dams:event" | head -1 | sed -e"s/\"\/>//" | sed -e"s/.*\///"`
echo "Event: $EVENT"

# retrieve an event record
echo "Retrieve an event record"
curl -f http://localhost:8080/dams/api/events/$EVENT
if [ $? != 0 ]; then
    ERRORS=$(( $ERRORS + 1 ))
fi
echo

echo ERRORS: $ERRORS
exit $ERRORS
