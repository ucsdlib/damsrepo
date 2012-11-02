#!/bin/sh

PROPS=$DAMS_HOME/dams.properties
DIR=$BASE/../webapp/WEB-INF
DIR2=$BASE/../../src
CP=$DIR/classes
for i in $DIR/lib/*.jar $DIR2/lib2/*.jar; do
	CP=$CP:$i
done
#echo CP: $CP
