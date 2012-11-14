<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:param name="objid"/>
  <xsl:param name="fileid"/>
  <xsl:param name="objectDS"/>
<!-- XXX timestamp -->
<!-- XXX contentType -->
<!-- XXX size -->
  <xsl:variable name="dsid">
    <xsl:choose>
      <xsl:when test="$fileid != ''"><xsl:value-of select="$fileid"/></xsl:when>
      <xsl:otherwise><xsl:value-of select="$objectDS"/></xsl:otherwise>
  </xsl:variable>
  <xsl:template match="/">
    <datastreamProfile pid="{$objid}" dsID="{$dsid}"
        xmlns="http://www.fedora.info/definitions/1/0/management/"
        xmlns:xsd="http://www.w3.org/2001/XMLSchema"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.fedora.info/definitions/1/0/management/ http://www.fedora.info/definitions/1/0/datastreamProfile.xsd">
      <dsLabel> </dsLabel>
      <dsVersionID><xsl:value-of select="$dsid"/>.0</dsVersionID>
      <dsCreateDate><xsl:value-of select="$timestamp"/></dsCreateDate>
      <dsState>A</dsState>
      <dsMIME><xsl:value-of select="$contentType"/></dsMIME>
      <dsFormatURI> </dsFormatURI>
      <dsControlGroup>X</dsControlGroup>
      <dsSize><xsl:value-of select="$size"/></dsSize>
      <dsVersionable>true</dsVersionable>
      <dsInfoType> </dsInfoType>
      <dsLocation><xsl:value-of select="$objid"/>+<xsl:value-of select="$dsid"/>+<xsl:value-of select="$dsid"/>.0</dsLocation>
      <dsLocationType> </dsLocationType>
      <dsChecksumType>DISABLED</dsChecksumType>
      <dsChecksum>none</dsChecksum>
    </datastreamProfile>
  </xsl:template>
</xsl:stylesheet>
