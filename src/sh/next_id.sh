#!/bin/sh

# generate a new identifier

curl -X POST http://localhost:8080/dams/api/next_id
if [ $? != 0 ]; then
    exit 1
fi
echo
