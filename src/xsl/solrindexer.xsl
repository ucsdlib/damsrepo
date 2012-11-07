<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
    xmlns:dams="http://library.ucsd.edu/ontology/dams#"
    xmlns:mads="http://www.loc.gov/mads/rdf/v1#"
    exclude-result-prefixes="rdf dams mads">
  <xsl:output method="xml" indent="yes"/>
  <xsl:template match="text()"/>
  <xsl:template match="/">
    <add>
      <xsl:apply-templates/>
    </add>
  </xsl:template>
  <xsl:template match="dams:Object" priority="2">
    <doc>

      <!-- relationship -->
      <xsl:for-each select="dams:relationship">
        <field name="name">
          <xsl:value-of select=".//mads:authoritativeLabel"/>
        </field>
      </xsl:for-each>

      <!-- files -->
      <xsl:for-each select="dams:hasFile/dams:File/@rdf:about">
        <field name="file">
            <xsl:call-template name="chop"/>
        </field>
      </xsl:for-each>

      <!-- rights XXX -->
      <!-- copyright XXX -->
      <!-- event XXX -->
      <!-- otherResource XXX -->

      <!-- language -->
      <xsl:for-each select="dams:language/@rdf:resource">
        <field name="lang">
          <xsl:call-template name="chop"/>
        </field>
      </xsl:for-each>

      <!-- typeOfResource -->
      <xsl:for-each select="dams:typeOfResource">
        <field name="type">
          <xsl:value-of select="."/>
        </field>
      </xsl:for-each>

      <!-- collection -->
      <xsl:for-each select="dams:collection/dams:Collection">
        <field name="collection">
          <xsl:value-of select="dams:title/rdf:value"/>
        </field>
      </xsl:for-each>

      <!-- repository -->
      <xsl:for-each select="dams:repository/dams:Repository">
        <field name="repository">
          <xsl:value-of select="dams:repositoryName"/>
        </field>
        <field name="repository_uri">
          <xsl:value-of select="dams:repositoryURI"/>
        </field>
      </xsl:for-each>

      <!-- title -->
      <xsl:for-each select="dams:title">
        <field name="title">
          <xsl:value-of select="rdf:value"/>
        </field>
        <xsl:if test="dams:relatedTitle">
          <xsl:for-each select="dams:relatedTitle">
            <field name="title_{dams:type}">
              <xsl:value-of select="rdf:value"/>
            </field>
          </xsl:for-each>
        </xsl:if>
      </xsl:for-each>

      <!-- subject -->
      <xsl:for-each select="dams:subject/mads:ComplexSubject//mads:authoritativeLabel">
        <field name="subject"><xsl:value-of select="."/></field>
      </xsl:for-each>


      <!-- date -->
      <xsl:for-each select="dams:date">
        <xsl:if test="rdf:value">
          <field name="date_display">
            <xsl:value-of select="rdf:value"/>
          </field>
        </xsl:if>
        <xsl:if test="dams:beginDate">
          <field name="date_begin">
            <xsl:value-of select="dams:beginDate"/>
          </field>
        </xsl:if>
        <xsl:if test="dams:endDate">
          <field name="date_end">
            <xsl:value-of select="dams:endDate"/>
          </field>
        </xsl:if>
      </xsl:for-each>

    </doc>
  </xsl:template>

  <xsl:template name="chop">
    <xsl:variable name="idns"
        select="//namespace::node()[local-name()='damsid']"/>
    <xsl:variable name="prns"
        select="//namespace::node()[local-name()='dams']"/>
    <xsl:choose>
      <xsl:when test="starts-with(.,$idns)">
        <xsl:value-of select="substring-after(.,$idns)"/>
      </xsl:when>
      <xsl:when test="starts-with(.,$prns)">
        <xsl:value-of select="substring-after(.,$prns)"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="."/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
</xsl:stylesheet>
