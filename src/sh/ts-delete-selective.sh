#!/bin/sh

# delete single predicates only

OBJID=$1
curl -i -X DELETE "http://localhost:8080/dams/api/objects/$OBJID/selective?predicate=dams:relationship&predicate=dams:repository"
echo
