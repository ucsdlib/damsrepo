<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
  xmlns:dams="http://library.ucsd.edu/ontology/dams#">
  <xsl:output method="xml" indent="yes"/>
  <xsl:param name="objid"/>
  <xsl:template match="/">

    <xsl:variable name="type">
      <xsl:value-of select="local-name(/rdf:RDF/*)"/>
    </xsl:variable>

    <!-- XXX find latest timestamp for file ingest -->
    <xsl:variable name="fileTimestamp">
      <xsl:for-each select="/rdf:RDF//dams:File/dams:event/dams:DAMSEvent[contains(dams:type,'file')]">
        <xsl:sort select="dams:eventDate" order="descending"/>
        <xsl:if test="position()=1">
          <xsl:value-of select="dams:eventDate"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:variable>

    <!-- XXX find record created date if there are multiple events -->
    <xsl:variable name="createdDate">
      <xsl:choose>
        <xsl:when test="/rdf:RDF/*/dams:event/dams:DAMSEvent[contains(dams:type,'record created')]">
          <xsl:value-of select="/rdf:RDF/*/dams:event/dams:DAMSEvent[contains(dams:type,'record created')]/dams:eventDate"/>
        </xsl:when>
        <xsl:when test="/rdf:RDF/*/dams:event/dams:DAMSEvent[contains(dams:type,'record edited')]">
          <xsl:for-each select="/rdf:RDF/*/dams:event/dams:DAMSEvent[contains(dams:type,'record edited')]">
            <xsl:sort select="dams:eventDate" order="ascending"/>
            <xsl:if test="position()=1">
              <xsl:value-of select="dams:eventDate"/>
            </xsl:if>
          </xsl:for-each>
        </xsl:when>
        <xsl:when test="/rdf:RDF/*/dams:event/dams:DAMSEvent/dams:eventDate">
          <xsl:value-of select="/rdf:RDF/*/dams:event/dams:DAMSEvent/dams:eventDate"/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:text>1999-12-31T23:59:59-0800</xsl:text>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>

    <!-- XXX find latest date if there are multiple modification events -->
    <xsl:variable name="timestamp">
      <xsl:choose>
        <xsl:when test="/rdf:RDF/*/dams:event/dams:DAMSEvent[contains(dams:type,'record edited')]">
          <xsl:for-each select="/rdf:RDF/*/dams:event/dams:DAMSEvent[contains(dams:type,'record edited')]">
            <xsl:sort select="dams:eventDate" order="descending"/>
            <xsl:if test="position()=1">
              <xsl:call-template name="selectTimestamp">
                <xsl:with-param name="recordTimestamp"><xsl:value-of select="dams:eventDate" /></xsl:with-param>
                <xsl:with-param name="fileTimestamp"><xsl:value-of select="$fileTimestamp" /></xsl:with-param>
              </xsl:call-template>
            </xsl:if>
          </xsl:for-each>
        </xsl:when>
        <xsl:when test="/rdf:RDF/*/dams:event/dams:DAMSEvent[contains(dams:type,'record created')]">
          <xsl:call-template name="selectTimestamp">
            <xsl:with-param name="recordTimestamp"><xsl:value-of select="/rdf:RDF/*/dams:event/dams:DAMSEvent[contains(dams:type,'record created')]/dams:eventDate" /></xsl:with-param>
            <xsl:with-param name="fileTimestamp"><xsl:value-of select="$fileTimestamp" /></xsl:with-param>
          </xsl:call-template>
        </xsl:when>
        <xsl:when test="/rdf:RDF/*/dams:event/dams:DAMSEvent/dams:eventDate">
          <xsl:call-template name="selectTimestamp">
            <xsl:with-param name="recordTimestamp"><xsl:value-of select="/rdf:RDF/*/dams:event/dams:DAMSEvent/dams:eventDate"/></xsl:with-param>
            <xsl:with-param name="fileTimestamp"><xsl:value-of select="$fileTimestamp" /></xsl:with-param>
          </xsl:call-template>
        </xsl:when>
        <xsl:otherwise>
          <xsl:text>1999-12-31T23:59:59-0800</xsl:text>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>

    <!-- XXX add to data model? -->
    <xsl:variable name="owner">foo</xsl:variable>

    <!-- XXX use title? is this used for anything? -->
    <xsl:variable name="label"></xsl:variable>

    <!-- XXX pass from parameter? is this used for anything? -->
    <xsl:variable name="baseURL">http://localhost:8080</xsl:variable>

    <objectProfile xmlns="http://www.fedora.info/definitions/1/0/access/"
      xmlns:xsd="http://www.w3.org/2001/XMLSchema"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xsi:schemaLocation="http://www.fedora.info/definitions/1/0/access/ http://www.fedora.info/definitions/1/0/objectProfile.xsd"
      pid="{$objid}">
      <objLabel><xsl:value-of select="$label"/></objLabel>
      <objOwnerId><xsl:value-of select="$owner"/></objOwnerId>
      <objModels>
        <model>info:fedora/afmodel:<xsl:value-of select="$type"/></model>
        <model>info:fedora/fedora-system:FedoraObject-3.0</model>
      </objModels>
      <objCreateDate><xsl:value-of select="$createdDate"/></objCreateDate>
      <objLastModDate><xsl:value-of select="$timestamp"/></objLastModDate>
      <objDissIndexViewURL><xsl:value-of select="$baseURL"/>/dams/fedora/objects/<xsl:value-of select="$objid"/>/methods/fedora-system%3A3/viewMethodIndex</objDissIndexViewURL>
      <objItemIndexViewURL><xsl:value-of select="$baseURL"/>/dams/fedora/objects/<xsl:value-of select="$objid"/>/methods/fedora-system%3A3/viewItemIndex</objItemIndexViewURL>
      <objState>A</objState>
    </objectProfile>
  </xsl:template>

  <xsl:template name="selectTimestamp">
    <xsl:param name="recordTimestamp" />
    <xsl:param name="fileTimestamp" />
    <xsl:choose>
      <xsl:when test="string-length($fileTimestamp) > 0 and translate($fileTimestamp, '-:T:+', '') > translate($recordTimestamp, '-:T:+', '')">
        <xsl:value-of select="$fileTimestamp"/>
      </xsl:when>
      <xsl:otherwise><xsl:value-of select="$recordTimestamp"/></xsl:otherwise>
    </xsl:choose>
  </xsl:template>
</xsl:stylesheet>
