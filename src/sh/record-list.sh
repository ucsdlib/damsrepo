#!/bin/sh

curl -o tmp/records.xml http://localhost:8080/dams/api/records
xsltproc src/xsl/record-list.xsl tmp/records.xml
