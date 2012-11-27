#!/bin/sh

# index a record in solr

curl -X POST http://localhost:8080/dams/api/objects/bb01010101/index
echo
