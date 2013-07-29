#!/bin/sh

curl $SOLR_URL/blacklight/update?commit=true -H "Content-Type: text/xml" --data-binary "<delete><query>*:*</query></delete>"
