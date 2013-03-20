#!/bin/sh

curl -o tmp/records.xml http://localhost:8080/dams/api/records/$1
xsltproc src/xsl/record-list.xsl tmp/records.xml
