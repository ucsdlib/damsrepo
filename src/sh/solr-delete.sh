#!/bin/sh

# delete a record from solr

curl -X DELETE http://localhost:8080/dams/api/objects/bb01010101/index
if [ $? != 0 ]; then
    exit 1
fi
echo
