#!/bin/sh

# bulk delete records from solr

curl -X DELETE -F id=bb01010101 http://localhost:8080/dams/api/index
echo