#!/bin/sh

# update a metadata record

OBJID=bb01010101
JSON='[{"subject":"bb01010101","predicate":"dams:note","object":"node1"},{"subject":"node1","predicate":"dams:type","object":"abstract"},{"subject":"node1","predicate":"rdf:value","object":"test"}]'

curl -X PUT -F adds=$JSON http://localhost:8080/dams/api/objects/$OBJID
if [ $? != 0 ]; then
    exit 1
fi
echo
