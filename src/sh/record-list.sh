#!/bin/sh

BASE=`dirname $0`
curl -o records.xml http://localhost:8080/dams/api/records/$1
xsltproc $BASE/../xsl/record-list.xsl records.xml
