<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
      xmlns:dams="http://library.ucsd.edu/ontology/dams#"
      xmlns:mads="http://www.loc.gov/mads/rdf/v1#"
      xmlns:mods="http://www.loc.gov/mods/v3"
      xmlns:mets="http://www.loc.gov/METS/"
      xmlns:owl="http://www.w3.org/2002/07/owl#"
      xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
      xmlns:xlink="http://www.w3.org/1999/xlink"
      xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" indent="yes"/>
  <xsl:variable name="madsNS">http://www.loc.gov/mads/rdf/v1#</xsl:variable>

  <!-- handle modsCollection records as ProvenanceCollections -->
  <xsl:template match="mods:modsCollection">
    <dams:ProvenanceCollection rdf:about="">
      <xsl:apply-templates/>
    </dams:ProvenanceCollection>
  </xsl:template>

  <!-- suppress desc md -->
  <xsl:template match="text()"/>
  <xsl:template match="mets:dmdSec"/>
  <xsl:template match="/mets:mets/mets:structMap[@TYPE='logical']/mets:div">
    <xsl:variable name="dmdid" select="@DMDID"/>
    <dams:Object rdf:about="{/mets:mets/@OBJID}">
      <xsl:call-template name="mods">
        <xsl:with-param name="dmdid" select="$dmdid"/>
      </xsl:call-template>
      <xsl:for-each select="mets:div">
        <xsl:call-template name="div"/>
      </xsl:for-each>
    </dams:Object>
  </xsl:template>
  <xsl:template name="div">
    <xsl:choose>
      <xsl:when test="@LABEL != ''">
        <xsl:variable name="dmdid" select="@DMDID"/>
        <dams:hasComponent>
          <dams:Component rdf:about="">
            <dams:label><xsl:value-of select="@LABEL"/></dams:label>
            <dams:order><xsl:value-of select="@ORDER"/></dams:order>
            <xsl:call-template name="mods">
              <xsl:with-param name="dmdid" select="$dmdid"/>
            </xsl:call-template>
            <xsl:for-each select="mets:div">
              <xsl:call-template name="div"/>
            </xsl:for-each>
          </dams:Component>
        </dams:hasComponent>
      </xsl:when>
      <xsl:when test="@TYPE='page' and mets:fptr">
        <xsl:call-template name="file"/>
      </xsl:when>
      <xsl:otherwise>
        <!-- ??? -->
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <xsl:template name="file">
    <xsl:variable name="fid" select="mets:fptr/@FILEID"/>
    <dams:hasFile>
      <dams:File rdf:about="">
        <xsl:for-each select="//mets:file[@ID=$fid]">
          <dams:use><xsl:value-of select="@USE"/></dams:use>
          <dams:sourceFilename>
            <xsl:value-of select="mets:FLocat/@xlink:href"/>
          </dams:sourceFilename>
        </xsl:for-each>
      </dams:File>
    </dams:hasFile>
  </xsl:template>
  <xsl:template name="mods">
    <xsl:param name="dmdid"/>
    <!-- desc md from dmdSec[@ID=$dmdid] -->
    <xsl:for-each select="//mets:dmdSec[@ID=$dmdid]/mets:mdWrap/mets:xmlData/mods:mods">
      <xsl:apply-templates/>
    </xsl:for-each>
  </xsl:template>
  <xsl:template match="mods:mods/mods:titleInfo|mods:relatedItem/mods:titleInfo">
    <dams:title>
      <dams:Title>
        <rdf:value><xsl:value-of select="mods:title"/></rdf:value>
      </dams:Title>
    </dams:title>
  </xsl:template>
  <xsl:template match="mods:mods/mods:language">
    <dams:language>
      <dams:Language rdf:about="">
        <xsl:for-each select="mods:languageTerm">
          <xsl:choose>
            <xsl:when test="@type='code'">
              <dams:code><xsl:value-of select="."/></dams:code>
            </xsl:when>
            <xsl:when test="@type='text'">
              <rdf:value><xsl:value-of select="."/></rdf:value>
            </xsl:when>
          </xsl:choose>
          <xsl:if test="@authority != ''">
            <dams:authority><xsl:value-of select="@authority"/></dams:authority>
          </xsl:if>
        </xsl:for-each>
      </dams:Language>
    </dams:language>
  </xsl:template>
  <xsl:template match="mods:mods/mods:typeOfResource">
    <dams:typeOfResource>
      <xsl:value-of select="."/>
    </dams:typeOfResource>
  </xsl:template>
  <xsl:template match="mods:physicalDescription/mods:extent">
    <dams:note>
      <dams:Note>
        <dams:type>extent</dams:type>
        <rdf:value><xsl:value-of select="."/></rdf:value>
      </dams:Note>
    </dams:note>
  </xsl:template>
  <xsl:template match="mods:mods/mods:abstract">
    <dams:note>
      <dams:Note>
        <dams:displayLabel>
          <xsl:choose>
            <xsl:when test="@displayLabel != ''">
              <xsl:value-of select="@displayLabel"/>
            </xsl:when>
            <xsl:otherwise>Abstract</xsl:otherwise>
          </xsl:choose>
        </dams:displayLabel>
        <dams:type>abstract</dams:type>
        <rdf:value><xsl:value-of select="."/></rdf:value>
      </dams:Note>
    </dams:note>
  </xsl:template>
  <xsl:template match="mods:mods/mods:note|mods:mods/mods:physicalDescription/mods:note|mods:relatedItem/mods:note">
    <dams:note>
      <dams:Note>
        <xsl:if test="@displayLabel != ''">
          <dams:displayLabel>
            <xsl:value-of select="@displayLabel"/>
          </dams:displayLabel>
        </xsl:if>
        <xsl:if test="@type != ''">
          <dams:type><xsl:value-of select="@type"/></dams:type>
        </xsl:if>
        <rdf:value><xsl:value-of select="."/></rdf:value>
      </dams:Note>
    </dams:note>
  </xsl:template>
  <xsl:template match="mods:accessCondition">
    <xsl:choose>
      <xsl:when test="@displayLabel = 'Rights'">
        <dams:copyright>
          <dams:Copyright rdf:about="">
            <dams:copyrightStatus>XXX</dams:copyrightStatus>
            <dams:copyrightJurisdiction>XXX</dams:copyrightJurisdiction>
            <dams:copyrightNote><xsl:value-of select="."/></dams:copyrightNote>
            <xsl:for-each select="//mods:accessCondition[@displayLabel='Access']">
              <dams:copyrightPurposeNote>
                <xsl:value-of select="."/>
              </dams:copyrightPurposeNote>
            </xsl:for-each>
          </dams:Copyright>
        </dams:copyright>
      </xsl:when>
      <xsl:when test="@displayLabel = 'License'">
        <dams:license>
          <dams:License rdf:about="">
            <dams:licenseNote><xsl:value-of select="."/></dams:licenseNote>
          </dams:License>
        </dams:license>
      </xsl:when>
    </xsl:choose>
  </xsl:template>
  <xsl:template match="mods:relatedItem[@type='host']">
    <dams:collection>
      <dams:Collection>
        <xsl:apply-templates/>
      </dams:Collection>
    </dams:collection>
  </xsl:template>
  <xsl:template match="mods:identifier">
    <dams:note>
      <dams:Note>
        <dams:type>identifier</dams:type>
        <dams:displayLabel><xsl:value-of select="@type"/></dams:displayLabel>
        <rdf:value><xsl:value-of select="."/></rdf:value>
      </dams:Note>
    </dams:note>
  </xsl:template>
  <xsl:template match="mods:originInfo">
    <dams:date>
      <dams:Date>
        <xsl:for-each select="mods:dateCreated">
          <xsl:choose>
            <xsl:when test="@point='start'">
              <dams:beginDate><xsl:value-of select="."/></dams:beginDate>
            </xsl:when>
            <xsl:when test="@point='end'">
              <dams:endDate><xsl:value-of select="."/></dams:endDate>
            </xsl:when>
            <xsl:otherwise>
              <rdf:value><xsl:value-of select="."/></rdf:value>
            </xsl:otherwise>
          </xsl:choose>
        </xsl:for-each>
      </dams:Date>
    </dams:date>
  </xsl:template>
  <xsl:template match="mods:mods/mods:name">
    <dams:relationship>
      <dams:role>
        <dams:Role rdf:about="">
          <dams:code>
            <xsl:value-of select="mods:role/mods:roleTerm[@type='code']"/>
          </dams:code>
          <rdf:value>
            <xsl:value-of select="mods:role/mods:roleTerm[@type='text']"/>
          </rdf:value>
          <xsl:if test="mods:role/mods:roleTerm/@authority != ''">
            <dams:authority>
              <xsl:value-of select="mods:role/mods:roleTerm/@authority"/>
            </dams:authority>
          </xsl:if>
        </dams:Role>
      </dams:role>
      <dams:name>
        <xsl:call-template name="name"/>
      </dams:name>
    </dams:relationship>
  </xsl:template>
  <xsl:template name="authority">
    <xsl:if test="@authority != ''">
      <dams:authority><xsl:value-of select="@authority"/></dams:authority>
    </xsl:if>
    <xsl:if test="@authorityURI != ''">
      <dams:authorityURI rdf:resource="{@authorityURI}"/>
    </xsl:if>
  </xsl:template>
  <xsl:template name="name" match="mods:subject/mods:name">
    <xsl:variable name="elementName">
      <xsl:choose>
        <xsl:when test="@type='personal'">PersonalName</xsl:when>
        <xsl:when test="@type='corporate'">CorporateName</xsl:when>
        <xsl:when test="@type='corporate'">CorporateName</xsl:when>
        <xsl:otherwise>Name</xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:element name="mads:{$elementName}">
      <xsl:attribute name="rdf:about"/>
      <mads:authoritativeLabel>
        <xsl:choose>
          <xsl:when test="mods:displayForm != ''">
            <xsl:value-of select="mods:displayForm"/>
          </xsl:when>
          <xsl:otherwise>
            <xsl:value-of select="mods:namePart"/>
          </xsl:otherwise>
        </xsl:choose>
      </mads:authoritativeLabel>
      <xsl:call-template name="authority"/>
      <xsl:if test="mods:description != ''">
        <owl:sameAs rdf:resource="{mods:description}"/>
      </xsl:if>
      <mads:elementList rdf:parseType="Collection">
        <xsl:choose>
          <xsl:when test="count(mods:namePart) = 1
                and $elementName = 'PersonalName'">
            <mads:FullNameElement>
              <xsl:value-of select="mods:namePart"/>
            </mads:FullNameElement>
          </xsl:when>
          <xsl:when test="count(mods:namePart) = 1">
            <mads:NameElement>
              <xsl:value-of select="mods:namePart"/>
            </mads:NameElement>
          </xsl:when>
          <xsl:when test="count(mods:namePart) &gt; 1">
            <xsl:for-each select="mods:namePart">
              <xsl:variable name="subelement">
                <xsl:choose>
                  <xsl:when test="@type='family'">FamilyNameElement</xsl:when>
                  <xsl:when test="@type='given'">GivenNameElement</xsl:when>
                  <xsl:when test="@type='date'">DateNameElement</xsl:when>
                  <xsl:otherwise>NameElement</xsl:otherwise>
                </xsl:choose>
              </xsl:variable>
              <xsl:element name="mads:{$subelement}" namespace="{$madsNS}">
                <xsl:value-of select="."/>
              </xsl:element>
            </xsl:for-each>
          </xsl:when>
        </xsl:choose>
      </mads:elementList>
    </xsl:element>
  </xsl:template>
  <xsl:template match="mods:mods/mods:subject">
    <dams:subject>
      <xsl:choose>
        <xsl:when test="count(*) &gt; 1">
          <mads:ComplexSubject rdf:about="">
            <xsl:call-template name="authority"/>
            <mads:authoritativeLabel>
              <xsl:for-each select="*">
                <xsl:if test="position() &gt; 1">--</xsl:if>
                <xsl:value-of select="."/>
              </xsl:for-each>
            </mads:authoritativeLabel>
            <mads:componentList>
              <xsl:apply-templates/>
            </mads:componentList>
          </mads:ComplexSubject>
        </xsl:when>
        <xsl:otherwise>
          <xsl:apply-templates/>
        </xsl:otherwise>
      </xsl:choose>
    </dams:subject>
  </xsl:template>
  <xsl:template match="mods:genre">
    <dams:subject>
      <xsl:call-template name="simplesubject"/>
    </dams:subject>
  </xsl:template>
  <xsl:template name="simplesubject" match="mods:topic">
    <xsl:variable name="elemName">
      <xsl:choose>
        <xsl:when test="local-name() = 'topic'">Topic</xsl:when>
        <xsl:when test="local-name() = 'genre'">GenreForm</xsl:when>
        <xsl:otherwise>ZZZ<xsl:value-of select="name()"/></xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:element name="mads:{$elemName}">
      <xsl:call-template name="authority"/>
      <mads:authoritativeLabel>
        <xsl:value-of select="."/>
      </mads:authoritativeLabel>
      <!-- XXX: do we want to create an element list when there is only one
                namePart? This just repeats the display form, and in some cases
                includes subject strings that aren't broken into components
           -->
      <mads:elementList>
        <xsl:element name="mads:{$elemName}Element">
          <mads:elementValue>
            <xsl:value-of select="."/>
          </mads:elementValue>
        </xsl:element>
      </mads:elementList>
    </xsl:element>
  </xsl:template>
<!-- XXX repository???  -->
</xsl:stylesheet>
