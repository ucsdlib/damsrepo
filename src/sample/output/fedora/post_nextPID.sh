#!/bin/sh

curl -X POST -u fedoraAdmin:fedoraAdmin "http://localhost:8983/fedora/objects/nextPID?numPIDs=$1&format=xml"
