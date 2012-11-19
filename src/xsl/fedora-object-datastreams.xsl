<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
  xmlns:dams="http://library.ucsd.edu/ontology/dams#">
  <xsl:output method="xml" indent="yes"/>
  <xsl:param name="objid"/>
  <xsl:param name="baseURL"/>
  <xsl:param name="objectDS"/>
  <xsl:template match="/">
    <objectDatastreams pid="{$objid}" baseURL="{$baseURL}"
        xmlns="http://www.fedora.info/definitions/1/0/access/"
        xmlns:xsd="http://www.w3.org/2001/XMLSchema"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.fedora.info/definitions/1/0/access/ http://www.fedora-commons.org/definitions/1/0/listDatastreams.xsd">

      <!-- XXX how much support for these builtin datastreams do we need? -->
      <datastream dsid="DC" label="Dublin Core Record for this object" mimeType="text/xml" />
      <datastream dsid="RELS-EXT" label="Fedora Object-to-Object Relationship Metadata" mimeType="application/rdf+xml" />
      <datastream dsid="rightsMetadata" label="" mimeType="text/xml" />
      <datastream dsid="{$objectDS}" label="DAMS RDF metadata" mimeType="application/rdf+xml" />
      <xsl:for-each select="//dams:File">
        <xsl:variable name="dsid" select="translate(substring-after(@rdf:about,concat($objid,'/')),'/','_')"/>
        <datastream dsid="_{$dsid}" label="" mimeType="{dams:mimeType}"/>
      </xsl:for-each>
    </objectDatastreams>
  </xsl:template>
</xsl:stylesheet>
