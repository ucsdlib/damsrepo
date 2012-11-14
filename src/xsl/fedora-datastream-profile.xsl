<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
    xmlns:dams="http://library.ucsd.edu/ontology/dams#">
  <xsl:output method="xml" indent="yes"/>
  <xsl:param name="objid"/>
  <xsl:param name="fileid"/>
  <xsl:param name="objectDS"/>
  <xsl:param name="objectSize"/>
  <xsl:variable name="dsid">
    <xsl:choose>
      <xsl:when test="$fileid != ''"><xsl:value-of select="$fileid"/></xsl:when>
      <xsl:otherwise><xsl:value-of select="$objectDS"/></xsl:otherwise>
    </xsl:choose>
  </xsl:variable>
  <xsl:variable name="timestamp">
    <xsl:choose>
      <xsl:when test="$fileid != ''"><xsl:value-of select="//dams:File[contains(@rdf:about,concat('/',fileid))]/dams:dateCreated"/></xsl:when>
      <xsl:when test="//dams:Object/dams:event/dams:Event[dams:type='object modification']">
        <!-- XXX find latest date if there are multiple modification events -->
        <xsl:value-of select="//dams:Object/dams:event/dams:Event[dams:type='object modification']/dams:endDate"/>
      </xsl:when>
      <xsl:when test="//dams:Object/dams:event/dams:Event[dams:type='object creation']">
        <xsl:value-of select="//dams:Object/dams:event/dams:Event[dams:type='object creation']/dams:endDate"/>
      </xsl:when>
      <xsl:otherwise>ERROR</xsl:otherwise>
    </xsl:choose>
  </xsl:variable>
  <xsl:variable name="contentType">
    <xsl:choose>
      <xsl:when test="$fileid != ''"><xsl:value-of select="//dams:File[contains(@rdf:about,concat('/',fileid))]/dams:mimeType"/></xsl:when>
      <xsl:otherwise>application/rdf+xml</xsl:otherwise>
    </xsl:choose>
  </xsl:variable>
  <xsl:variable name="size">
    <xsl:choose>
      <xsl:when test="$fileid != ''"><xsl:value-of select="//dams:File[contains(@rdf:about,concat('/',fileid))]/dams:size"/></xsl:when>
      <xsl:otherwise><xsl:value-of select="$objectSize"/></xsl:otherwise>
    </xsl:choose>
  </xsl:variable>
  <xsl:template match="/">
    <datastreamProfile pid="{$objid}" dsID="{$dsid}"
        xmlns="http://www.fedora.info/definitions/1/0/management/"
        xmlns:xsd="http://www.w3.org/2001/XMLSchema"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.fedora.info/definitions/1/0/management/ http://www.fedora.info/definitions/1/0/datastreamProfile.xsd">
      <dsLabel></dsLabel>
      <dsVersionID><xsl:value-of select="$dsid"/>.0</dsVersionID>
      <dsCreateDate><xsl:value-of select="$timestamp"/></dsCreateDate>
      <dsState>A</dsState>
      <dsMIME><xsl:value-of select="$contentType"/></dsMIME>
      <dsFormatURI></dsFormatURI>
      <dsControlGroup>X</dsControlGroup>
      <dsSize><xsl:value-of select="$size"/></dsSize>
      <!-- XXX: set to false? other changes in output required? -->
      <dsVersionable>true</dsVersionable>
      <dsInfoType></dsInfoType>
      <dsLocation><xsl:value-of select="$objid"/>+<xsl:value-of select="$dsid"/>+<xsl:value-of select="$dsid"/>.0</dsLocation>
      <dsLocationType></dsLocationType>
      <dsChecksumType>DISABLED</dsChecksumType>
      <dsChecksum>none</dsChecksum>
    </datastreamProfile>
  </xsl:template>
</xsl:stylesheet>
