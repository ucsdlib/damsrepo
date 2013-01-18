#!/bin/sh

# bulk index records in solr

curl -X POST -F id=bb01010101 http://localhost:8080/dams/api/index
if [ $? != 0 ]; then
    exit 1
fi
echo
