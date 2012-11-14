<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
  xmlns:dams="http://library.ucsd.edu/ontology/dams#">
  <xsl:template match="/">
    <xsl:variable name="idns"
        select="//namespace::node()[local-name()='damsid']"/>
    <xsl:variable name="objuri" select="/rdf:RDF/dams:Object/@rdf:about"/>
    <xsl:variable name="objid">
      <xsl:choose>
        <xsl:when test="starts-with($objuri,$idns)">
          <xsl:value-of select="substring-after($objuri,$idns)"/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="$objuri"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>

    <!-- XXX -->
    <xsl:variable name="owner">Foo</xsl:variable>
    <xsl:variable name="timestamp">2012-12-31T12:34:56.000Z</xsl:variable>
    <xsl:variable name="label"><xsl:text> </xsl:text></xsl:variable>
    <xsl:variable name="baseURL">http://localhost:8080</xsl:variable>

    <objectProfile xmlns="http://www.fedora.info/definitions/1/0/access/"
      xmlns:xsd="http://www.w3.org/2001/XMLSchema"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xsi:schemaLocation="http://www.fedora.info/definitions/1/0/access/ http://www.fedora.info/definitions/1/0/objectProfile.xsd"
      pid="{$objid}">
      <objLabel><xsl:value-of select="$label"/></objLabel>
      <objOwnerId><xsl:value-of select="$owner"/></objOwnerId>
      <objModels>
        <model>info:fedora/afmodel:Work</model>
        <model>info:fedora/fedora-system:FedoraObject-3.0</model>
      </objModels>
      <objCreateDate><xsl:value-of select="$timestamp"/></objCreateDate>
      <objLastModDate><xsl:value-of select="$timestamp"/></objLastModDate>
      <objDissIndexViewURL><xsl:value-of select="$baseURL"/>/dams/fedora/objects/<xsl:value-of select="$objid"/>/methods/fedora-system%3A3/viewMethodIndex</objDissIndexViewURL>
      <objItemIndexViewURL><xsl:value-of select="$baseURL"/>/dams/fedora/objects/<xsl:value-of select="$objid"/>/methods/fedora-system%3A3/viewItemIndex</objItemIndexViewURL>
      <objState>A</objState>
    </objectProfile>
  </xsl:template>
</xsl:stylesheet>
