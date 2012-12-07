<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
  xmlns:dams="http://library.ucsd.edu/ontology/dams#">
  <xsl:output method="xml" indent="yes"/>
  <xsl:param name="objid"/>
  <xsl:param name="defaultGroup"/>
  <xsl:template match="/">
    <rightsMetadata>
      <copyright>
        <human><xsl:value-of select="//dams:Copyright/dams:copyrightNote"/></human>
        <machine>
          <xsl:for-each select="//dams:License[@rdf:about]">
            <a rel="license" href="{@rdf:about}">
              <xsl:if test="//dams:Permission">
                permission: <xsl:value-of select="//dams:Permission/rdf:value"/>
              </xsl:if>
            </a>
          </xsl:for-each>
          <xsl:for-each select="//dams:Copyright[@rdf:about]">
            <a rel="copyright" href="{@rdf:about}">
              <xsl:value-of select="dams:copyrightStatus"/>
              <xsl:if test="dams:copyrightJurisdiction != ''">
                (<xsl:value-of select="dams:copyrightJurisdiction"/>)
              </xsl:if>
            </a>
          </xsl:for-each>
        </machine>
      </copyright>
      <access type="discover">
        <group><xsl:value-of select="$defaultGroup"/></group>
      </access>
      <access type="read">
        <group><xsl:value-of select="$defaultGroup"/></group>
      </access>
      <access type="edit">
        <xsl:for-each select="//dams:Repository/dams:repositoryGroup">
          <group><xsl:value-of select="."/></group>
        </xsl:for-each>
      </access>
<!-- copyrightPurposeNote?
      <use>
        <human>XXX</human>
        <machine>XXX</machine>
      </use>
-->
<!-- if there is an embargo date...
      <embargo>
        <human>XXX</human>
        <machine>
          <date type="release">YYYY-MM-DD</date>
        </machine>
      </embargo>
-->
    </rightsMetadata>

  </xsl:template>
</xsl:stylesheet>
