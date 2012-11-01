#!/bin/sh

PROPS=$DAMS_HOME/dams.properties
DIR=$BASE/../webapp/WEB-INF
CP=$DIR/classes
for i in $DIR/lib/*.jar; do
	CP=$CP:$i
done
