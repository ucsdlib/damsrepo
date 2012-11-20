#!/bin/sh

XSL=$HOME/src/dams/src/xsl
FDD=$XSL/fedora-datastream-delete.xsl
PID=$XSL/fedora-nextPID.xsl
OBPR=$XSL/fedora-object-profile.xsl
OBDS=$XSL/fedora-object-datastreams.xsl
DSPR=$XSL/fedora-datastream-profile.xsl

OBJ_XML=objectShow-export.xml
NID_XML=next_id_multiple.xml

OBJOPT="--stringparam objid bb01010101 --stringparam objectDS "DAMS-RDF" --stringparam objectSize 9844"
FILOPT="--stringparam objid bb01010101 --stringparam fileid 1-3.jpg"

echo "object deleted:"
xsltproc $OBJOPT $FDD $OBJ_XML
echo ""
echo "-----"
echo "datastream deleted:"
xsltproc $FILOPT $FDD $OBJ_XML
echo ""

echo "-----"
echo "datastream profile (object):"
xsltproc $OBJOPT $DSPR $OBJ_XML

echo "-----"
echo "datastream profile (file):"
xsltproc $FILOPT $DSPR $OBJ_XML

echo "-----"
echo "nextPID"
xsltproc $PID $NID_XML

echo "-----"
echo "object profile"
xsltproc $OBJOPT $OBPR $OBJ_XML

echo "-----"
echo "object datastreams"
xsltproc $OBJOPT $OBDS $OBJ_XML
