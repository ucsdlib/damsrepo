#!/bin/sh

BASE=`dirname $0`
curl -o records.xml $URL/api/records/$1
xsltproc $BASE/../xsl/record-list.xsl records.xml
