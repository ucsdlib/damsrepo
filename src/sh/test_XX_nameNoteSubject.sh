#!/bin/sh

# load predicates
tmp/commands/ts-load.sh dams src/sample/predicates/nameNoteSubject.nt

function f
{
	ARK=$1
	FILE=$2
	tmp/commands/ts-post.sh $ARK $FILE
	tmp/commands/ts-delete.sh $ARK
	tmp/commands/ts-put.sh $ARK $FILE
}

# load records
f bd52568274 src/sample/object/new/damsNote.rdf.xml
f bd9113515d src/sample/object/new/damsCustodialResponsibilityNote.rdf.xml
f bd3959888k src/sample/object/new/damsPreferredCitationNote.rdf.xml
f bd1366006j src/sample/object/new/damsScopeContentNote.rdf.xml
f bd7509406v src/sample/object/new/madsName.rdf.xml
f bd1707307x src/sample/object/new/damsBuiltWorkPlace.rdf.xml
f bd0410365x src/sample/object/new/damsCulturalContext.rdf.xml
f bd7816576v src/sample/object/new/damsFunction.rdf.xml
f bd65537666 src/sample/object/new/damsIconography.rdf.xml
f bd2662949r src/sample/object/new/damsScientificName.rdf.xml
f bd0069066b src/sample/object/new/damsStylePeriod.rdf.xml
f bd8772217q src/sample/object/new/damsTechnique.rdf.xml
f bd6724414c src/sample/object/new/madsComplexSubject.rdf.xml
f bd0478622c src/sample/object/new/madsConferenceName.rdf.xml
f bd8021352s src/sample/object/new/madsCorporateName.rdf.xml
f bd1775562z src/sample/object/new/madsFamilyName.rdf.xml
f bd9796116g src/sample/object/new/madsGenreForm.rdf.xml
f bd8533304b src/sample/object/new/madsGeographic.rdf.xml
f bd7509406v src/sample/object/new/madsName.rdf.xml
f bd72363644 src/sample/object/new/madsOccupation.rdf.xml
f bd93182924 src/sample/object/new/madsPersonalName.rdf.xml
f bd59394235 src/sample/object/new/madsTemporal.rdf.xml
f bd46424836 src/sample/object/new/madsTopic.rdf.xml
f bd6212468x src/sample/object/new/damsObject.rdf.xml
