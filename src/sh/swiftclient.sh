#!/bin/sh

# swiftclient.sh - client for OpenStack Swift storage REST API
#
# usage: swiftclient.sh action options:
#   copy srcContainer srcObject dstContainer dstObject
#   createContainer name
#   delete container [object]
#   deleteContainer name
#   download [user] container object
#   listContainers [user]
#   listObjects [user] container [path]
#   meta container object prop1 [prop2 ... propN]
#   move srcContainer srcObject dstContainer dstObject
#   stat [container [object]]
#   upload container object [inputFile] (uses object if inputFile omitted)

# connection parameters, should contain the following properties:
#   username, password, authURL
PROPS=swiftclient.properties

# dependencies
for i in lib/*.jar; do
	CP="$CP:$i"
done

java -cp $CP edu.ucsd.library.dams.file.impl.SwiftClient $PROPS "$@"
