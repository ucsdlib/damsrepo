#!/bin/sh

PROPS=$DAMS_HOME/dams.properties
DIR=$BASE/../webapp/WEB-INF
export DIR2=$BASE/../../src
CP=$DIR/classes
for i in $DIR2/lib/*.jar $DIR2/lib2/*.jar; do
	CP=$CP:$i
done
