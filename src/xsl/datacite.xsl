<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:dams="http://library.ucsd.edu/ontology/dams#"
    xmlns:mads="http://www.loc.gov/mads/rdf/v1#"
    xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
    xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#"
    xmlns="http://datacite.org/schema/kernel-4"
    exclude-result-prefixes="dams mads rdf">

  <xsl:output method="xml" indent="yes"/>

  <!-- wrapper -->
  <xsl:template match="/rdf:RDF">
    <resource xmlns="http://schema.datacite.org/meta/kernel-4.0/"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xsi:schemaLocation="http://schema.datacite.org/meta/kernel-4.0/ http://schema.datacite.org/meta/kernel-4.0/metadata.xsd">
      <identifier identifierType="DOI">(:tba)</identifier>

      <xsl:for-each select="dams:AssembledCollection|dams:ProvenanceCollection|dams:ProvenanceCollectionPart">
        <resourceType resourceTypeGeneral="Dataset">Dataset</resourceType>
        <xsl:call-template name="datacite"/>
      </xsl:for-each>

      <xsl:for-each select="dams:Object">
        <xsl:variable name="unit">
          <xsl:choose>
            <xsl:when test="dams:unit/@rdf:resource">
              <xsl:variable name="sid" select="dams:unit/@rdf:resource"/>
              <xsl:value-of select="//*[@rdf:about=$sid]/dams:code"/>
            </xsl:when>
            <xsl:otherwise>
              <xsl:value-of select="dams:unit//dams:code" />
            </xsl:otherwise>
          </xsl:choose>
        </xsl:variable>
        <xsl:choose>
          <xsl:when test="$unit = 'rdcp'">
            <resourceType resourceTypeGeneral="Dataset">Dataset</resourceType>
          </xsl:when>
          <xsl:otherwise>
            <xsl:for-each select="dams:typeOfResource[1]">
              <xsl:choose>
                <xsl:when test="text() = 'still image'">
                  <resourceType resourceTypeGeneral="Image"/>
                </xsl:when>
                <xsl:when test="text() = 'text'">
                  <resourceType resourceTypeGeneral="Text"/>
                </xsl:when>
                <xsl:when test="text() = 'data'">
                  <resourceType resourceTypeGeneral="Dataset">Dataset</resourceType>
                </xsl:when>
                <xsl:when test="text() = 'sound recording'">
                  <resourceType resourceTypeGeneral="Sound"/>
                </xsl:when>
                <xsl:when test="text() = 'sound recording-nonmusical'">
                  <resourceType resourceTypeGeneral="Sound"/>
                </xsl:when>
                <xsl:when test="text() = 'moving image'">
                  <resourceType resourceTypeGeneral="Audiovisual"/>
                </xsl:when>
              </xsl:choose>
            </xsl:for-each>
          </xsl:otherwise>
        </xsl:choose>

        <xsl:call-template name="datacite"/>
      </xsl:for-each>

    </resource>
  </xsl:template>

  <!-- core datacite metadata -->
  <xsl:template name="datacite">

    <creators>
      <xsl:for-each select="dams:note/dams:Note[dams:type='preferred citation']/rdf:value">
        <xsl:call-template name="creator">
          <xsl:with-param name="name" select="substring-before(., ' (')"/>
        </xsl:call-template>
      </xsl:for-each>
    </creators>

    <titles>
      <xsl:variable name="collectionTitle">
        <xsl:for-each select="dams:assembledCollection/*|dams:provenanceCollection/*|dams:provenanceCollectionPart/*">
          <xsl:value-of select="dams:title//mads:authoritativeLabel"/>
        </xsl:for-each>
      </xsl:variable>
      <xsl:for-each select="dams:title//mads:authoritativeLabel|dams:title//mads:variantLabel">
        <xsl:variable name="lang">
          <xsl:choose>
            <xsl:when test="@xml:lang"><xsl:value-of select="@xml:lang"/></xsl:when>
            <xsl:otherwise>en-US</xsl:otherwise>
          </xsl:choose>
        </xsl:variable>
        <xsl:variable name="type">
          <xsl:choose>
            <xsl:when test="local-name(../..) = 'hasTranslationVariant'">TranslatedTitle</xsl:when>
            <xsl:when test="local-name(../..) = 'hasVariant'">AlternativeTitle</xsl:when>
            <xsl:otherwise></xsl:otherwise>
          </xsl:choose>
        </xsl:variable>
        <xsl:element name="title">
          <xsl:attribute name="xml:lang"><xsl:value-of select="$lang"/></xsl:attribute>
          <xsl:if test="$type != ''">
            <xsl:attribute name="titleType"><xsl:value-of select="$type"/></xsl:attribute>
          </xsl:if>
          <xsl:value-of select="."/>
          <xsl:if test="string-length($collectionTitle) &gt; 0">
            <xsl:value-of select="concat('. ', 'In ', $collectionTitle)"/>
          </xsl:if>
        </xsl:element>
      </xsl:for-each>
    </titles>

    <publisher>UC San Diego Library Digital Collections</publisher>

    <!-- dates -->
    <xsl:for-each select="dams:date/dams:Date[dams:type='issued']">
      <publicationYear><xsl:value-of select="rdf:value"/></publicationYear>
    </xsl:for-each>
    <xsl:if test="dams:date/dams:Date[dams:type != 'published']">
      <dates>
        <xsl:for-each select="dams:date/dams:Date[dams:type != 'published']">
          <xsl:choose>
            <xsl:when test="dams:beginDate != '' and dams:endDate != '' and dams:beginDate != dams:endDate">
              <date>
                <xsl:call-template name="date-type"/>
                <xsl:value-of select="dams:beginDate"/>
                <xsl:text>/</xsl:text>
                <xsl:value-of select="dams:endDate"/>
              </date>
            </xsl:when>
            <xsl:when test="dams:beginDate != ''">
              <date>
                <xsl:call-template name="date-type"/>
                <xsl:value-of select="dams:beginDate"/>
              </date>
            </xsl:when>
            <xsl:when test="rdf:value != ''">
              <date>
                <xsl:call-template name="date-type"/>
                <xsl:value-of select="rdf:value"/>
              </date>
            </xsl:when>
          </xsl:choose>
        </xsl:for-each>
      </dates>
    </xsl:if>

    <xsl:if test="dams:note/dams:Note[dams:type='description' or dams:type='methods']">
      <descriptions>
        <xsl:for-each select="dams:note/dams:Note[dams:type='description' or dams:type='methods']">
          <xsl:variable name="descriptionType">
            <xsl:choose>
              <xsl:when test="dams:type = 'methods'">Methods</xsl:when>
              <xsl:otherwise>Abstract</xsl:otherwise>
            </xsl:choose>
          </xsl:variable>
          <description descriptionType="{$descriptionType}">
            <xsl:value-of select="rdf:value"/>
          </description>
        </xsl:for-each>
      </descriptions>
    </xsl:if>

    <!-- subject, excl. geo -->
    <subjects>
      <xsl:for-each select="dams:builtWorkPlace|dams:conferenceName|dams:corporateName|dams:culturalContext|dams:familyName|dams:function|dams:genreForm|dams:iconography|dams:name|dams:occupation|dams:otherName|dams:personalName|dams:scientificName|dams:stylePeriod|dams:technique|dams:temporal|dams:topic|dams:complexSubject|dams:geographic">
        <xsl:choose>
          <xsl:when test="@rdf:resource">
            <xsl:variable name="sid" select="@rdf:resource"/>
            <xsl:for-each select="//*[@rdf:about=$sid]">
              <xsl:call-template name="subject-element"/>
            </xsl:for-each>
          </xsl:when>
          <xsl:otherwise>
            <xsl:for-each select="*">
              <xsl:call-template name="subject-element"/>
            </xsl:for-each>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:for-each>
    </subjects>

    <!-- format mime type or filename ext pref. -->
    <formats>
      <xsl:call-template name="format">
        <xsl:with-param name="n">1</xsl:with-param>
      </xsl:call-template>
    </formats>

    <!-- rights -->
    <rightsList>
      <xsl:for-each select="dams:license/dams:License">
        <rights>
          <xsl:if test="dams:licenseURI != ''">
            <xsl:attribute name="rightsURI">
              <xsl:value-of select="dams:licenseURI"/>
            </xsl:attribute>
          </xsl:if>
          <xsl:value-of select="dams:licenseNote"/>
        </rights>
      </xsl:for-each>
    </rightsList>

    <!-- language -->
    <xsl:if test="dams:language/mads:Language/mads:code != 'zxx'">
      <language>
        <xsl:value-of select="dams:language/mads:Language/mads:code"/>
      </language>
    </xsl:if>

    <!-- geolocation -->
    <xsl:if test="dams:cartographics/dams:Cartographics/dams:point">
      <xsl:for-each select="dams:cartographics/dams:Cartographics/dams:point[1]">
        <xsl:variable name="lat" select="substring-before(., ',')"/>
        <xsl:variable name="rest" select="substring-after(., ',')"/>
        <xsl:variable name="lon">
          <xsl:choose>
            <xsl:when test="contains($rest, ',')">
              <xsl:value-of select="substring-before($rest, ',')"/>
            </xsl:when>
            <xsl:otherwise><xsl:value-of select="$rest"/></xsl:otherwise>
          </xsl:choose>
        </xsl:variable>
        <geoLocations>
          <geoLocation>
            <geoLocationPoint>
              <pointLongitude><xsl:value-of select="normalize-space($lon)"/></pointLongitude>
              <pointLatitude><xsl:value-of select="normalize-space($lat)"/></pointLatitude>
            </geoLocationPoint>
          </geoLocation>
        </geoLocations>
      </xsl:for-each>
    </xsl:if>

    <xsl:if test="dams:cartographics/dams:Cartographics/dams:polygon">
      <xsl:for-each select="dams:cartographics/dams:Cartographics/dams:polygon[1]">
        <geoLocations>
          <geoLocation>
            <geoLocationPolygon>
              <xsl:call-template name="polygon">
                <xsl:with-param name="point" select="."/>
              </xsl:call-template>
            </geoLocationPolygon>
          </geoLocation>
        </geoLocations>
      </xsl:for-each>
    </xsl:if>
 
    <xsl:if test="dams:cartographics/dams:Cartographics/dams:line">
      <xsl:for-each select="dams:cartographics/dams:Cartographics/dams:line[1]">
        <xsl:variable name="latitudes">
          <xsl:call-template name="geo_string">
            <xsl:with-param name="point" select="."/>
            <xsl:with-param name="type" select="'latitude'"/>
          </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="min_lat">
          <xsl:call-template name="getMin">
            <xsl:with-param name="string" select="normalize-space($latitudes)"/>
          </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="max_lat">
          <xsl:call-template name="getMax">
            <xsl:with-param name="string" select="normalize-space($latitudes)"/>
          </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="longitudes">
          <xsl:call-template name="geo_string">
            <xsl:with-param name="point" select="."/>
            <xsl:with-param name="type" select="'longitude'"/>
          </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="min_lon">
          <xsl:call-template name="getMin">
            <xsl:with-param name="string" select="normalize-space($longitudes)"/>
          </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="max_lon">
          <xsl:call-template name="getMax">
            <xsl:with-param name="string" select="normalize-space($longitudes)"/>
          </xsl:call-template>
        </xsl:variable>
        <geoLocations>
          <geoLocation>
            <geoLocationBox>
              <xsl:call-template name="box_longitude">
                <xsl:with-param name="maxLon" select="number(normalize-space($max_lon))"/>
                <xsl:with-param name="minLon" select="number(normalize-space($min_lon))"/>
              </xsl:call-template>
              <southBoundLatitude><xsl:value-of select="normalize-space($min_lat)"/></southBoundLatitude>
              <northBoundLatitude><xsl:value-of select="normalize-space($max_lat)"/></northBoundLatitude>
            </geoLocationBox>
          </geoLocation>
        </geoLocations>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>

  <xsl:template name="box_longitude">
    <xsl:param name="maxLon"/>
    <xsl:param name="minLon"/>
    <xsl:choose>
      <xsl:when test="180 >= ($maxLon - $minLon)">
        <westBoundLongitude><xsl:value-of select="$minLon"/></westBoundLongitude>
        <eastBoundLongitude><xsl:value-of select="$maxLon"/></eastBoundLongitude>
      </xsl:when>
      <xsl:otherwise>
        <westBoundLongitude><xsl:value-of select="$maxLon"/></westBoundLongitude>
        <eastBoundLongitude><xsl:value-of select="$minLon"/></eastBoundLongitude>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  
  <!-- tokenize creator list on semicolons -->
  <xsl:template name="creator">
    <xsl:param name="name"/>
    <xsl:variable name="before" select="substring-before(concat($name,';'), ';')"/>
    <xsl:variable name="after" select="substring-after($name, '; ')"/>
    <creator>
      <creatorName><xsl:value-of select="$before"/></creatorName>
    </creator>
    <xsl:if test="$after != ''">
      <xsl:call-template name="creator">
        <xsl:with-param name="name" select="$after"/>
      </xsl:call-template>
    </xsl:if>
  </xsl:template>

  <xsl:template name="date-type">
    <xsl:attribute name="dateType">
      <xsl:choose>
        <xsl:when test="dams:type = 'copyright'">Copyrighted</xsl:when>
        <xsl:when test="dams:type = 'collected'">Collected</xsl:when>
        <xsl:when test="dams:type = 'issued'"   >Issued</xsl:when>
        <xsl:otherwise                          >Created</xsl:otherwise>
      </xsl:choose>
    </xsl:attribute>
  </xsl:template>

  <xsl:template name="subject-element">
    <subject>
      <xsl:value-of select="mads:authoritativeLabel"/>
    </subject>
  </xsl:template>

  <xsl:template name="mime-type">
    <xsl:choose>
      <xsl:when test="contains(text(), ';')">
        <xsl:value-of select="substring-before(text(),';')"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="text()"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template name="format">
    <xsl:param name="n"/>
    <xsl:param name="done"/>
    <xsl:variable name="next" select="$n + 1"/>

    <xsl:for-each select="//dams:File[position() = $n]">
      <xsl:sort select="@rdf:about"/>
      <xsl:variable name="fileId">
        <xsl:call-template name="lastIndexOf">
          <xsl:with-param name="string"><xsl:value-of select="@rdf:about"/></xsl:with-param>
          <xsl:with-param name="char">/</xsl:with-param>
        </xsl:call-template>
      </xsl:variable>
      <xsl:variable name="parentId" select="substring-before(@rdf:about, $fileId)"/>
      <xsl:variable name="type">
        <xsl:choose>
          <xsl:when test="contains(dams:mimeType,';')">
            <xsl:value-of select="substring-before(dams:mimeType,';')"/>
          </xsl:when>
          <xsl:otherwise><xsl:value-of select="dams:mimeType"/></xsl:otherwise>
        </xsl:choose>
      </xsl:variable>
      <xsl:if test="contains(dams:use,'-service') and $parentId != $done">
        <format><xsl:value-of select="$type"/></format>
      </xsl:if>
      <xsl:if test="//dams:File[position() = $next]">
        <xsl:call-template name="format">
          <xsl:with-param name="n" select="$next"/>
          <xsl:with-param name="done" select="$parentId"/>
        </xsl:call-template>
      </xsl:if>
    </xsl:for-each>

  </xsl:template>

  <!-- tokenize polygon list on space -->
  <xsl:template name="polygon">
    <xsl:param name="point"/>
    <xsl:variable name="before" select="substring-before(concat($point,' '), ' ')"/>
    <xsl:variable name="after" select="substring-after($point, ' ')"/>
    <xsl:variable name="lat" select="substring-before($before, ',')"/>
    <xsl:variable name="rest" select="substring-after($before, ',')"/>
    <xsl:variable name="lon">
      <xsl:choose>
        <xsl:when test="contains($rest, ',')">
          <xsl:value-of select="substring-before($rest, ',')"/>
        </xsl:when>
        <xsl:otherwise><xsl:value-of select="$rest"/></xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <polygonPoint>
      <pointLongitude><xsl:value-of select="$lon"/></pointLongitude>
      <pointLatitude><xsl:value-of select="$lat"/></pointLatitude>
    </polygonPoint>              
    <xsl:if test="$after != ''">
      <xsl:call-template name="polygon">
        <xsl:with-param name="point" select="$after"/>
      </xsl:call-template>
    </xsl:if>
  </xsl:template>
  
  <xsl:template name="geo_string">
    <xsl:param name="point"/>
    <xsl:param name="type"/>
    <xsl:variable name="before" select="substring-before(concat($point,' '), ' ')"/>
    <xsl:variable name="lat" select="substring-before($before, ',')"/>
    <xsl:variable name="after" select="substring-after($point, ' ')"/>
    <xsl:variable name="rest" select="substring-after($before, ',')"/>
    <xsl:variable name="lon">
      <xsl:choose>
        <xsl:when test="contains($rest, ',')">
          <xsl:value-of select="substring-before($rest, ',')"/>
        </xsl:when>
        <xsl:otherwise><xsl:value-of select="$rest"/></xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:choose>
      <xsl:when test="contains($type, 'longitude')">
        <xsl:value-of select="$lon"/>,
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="$lat"/>,
      </xsl:otherwise>
    </xsl:choose>    
    <xsl:if test="$after != ''">
      <xsl:call-template name="geo_string">
        <xsl:with-param name="point" select="$after"/>
        <xsl:with-param name="type" select="$type"/>
      </xsl:call-template>
    </xsl:if>
  </xsl:template>

  <xsl:template name="getMax">
        <xsl:param name="string"/>
        <xsl:variable name="firstNbr" select="number(substring-before(concat($string,','),','))"/>
        <xsl:variable name="secondNbr">
            <xsl:if test="contains($string,',')">
                <xsl:value-of select="number(substring-before(concat(substring-after($string,','),','),','))"/>
            </xsl:if>
        </xsl:variable>
        <xsl:variable name="remainingNbrs" select="substring-after(substring-after($string,','),',')"/>
        <xsl:variable name="modString">
            <xsl:choose>
                <xsl:when test="$firstNbr > $secondNbr">
                    <!--Move $firstNbr to the end.-->
                    <xsl:value-of select="$remainingNbrs"/>
                    <xsl:if test="string($remainingNbrs)">,</xsl:if>
                    <xsl:value-of select="$firstNbr"/>
                </xsl:when>
                <xsl:otherwise>
                    <!--Drop $firstNbr.-->
                    <xsl:value-of select="$secondNbr"/>
                    <xsl:if test="string($remainingNbrs)">,</xsl:if>
                    <xsl:value-of select="$remainingNbrs"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:choose>
            <xsl:when test="string($secondNbr)">
                <xsl:call-template name="getMax">
                    <xsl:with-param name="string" select="$modString"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="$firstNbr"/>
            </xsl:otherwise>
        </xsl:choose>
  </xsl:template>
 
  <xsl:template name="getMin">
        <xsl:param name="string"/>
        <xsl:variable name="firstNbr" select="number(substring-before(concat($string,','),','))"/>
        <xsl:variable name="secondNbr">
            <xsl:if test="contains($string,',')">
                <xsl:value-of select="number(substring-before(concat(substring-after($string,','),','),','))"/>
            </xsl:if>
        </xsl:variable>
        <xsl:variable name="remainingNbrs" select="substring-after(substring-after($string,','),',')"/>
        <xsl:variable name="modString">
            <xsl:choose>
                <xsl:when test="$secondNbr > $firstNbr">
                    <!--Move $firstNbr to the end.-->
                    <xsl:value-of select="$remainingNbrs"/>
                    <xsl:if test="string($remainingNbrs)">,</xsl:if>
                    <xsl:value-of select="$firstNbr"/>
                </xsl:when>
                <xsl:otherwise>
                    <!--Drop $firstNbr.-->
                    <xsl:value-of select="$secondNbr"/>
                    <xsl:if test="string($remainingNbrs)">,</xsl:if>
                    <xsl:value-of select="$remainingNbrs"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:choose>
            <xsl:when test="string($secondNbr)">
                <xsl:call-template name="getMin">
                    <xsl:with-param name="string" select="$modString"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="$firstNbr"/>
            </xsl:otherwise>
        </xsl:choose>
  </xsl:template>

<xsl:template name="lastIndexOf">
   <xsl:param name="string" />
   <xsl:param name="char" />
   <xsl:choose>
      <xsl:when test="contains($string, $char)">
         <!-- call the template recursively... -->
         <xsl:call-template name="lastIndexOf">
            <xsl:with-param name="string" select="substring-after($string, $char)" />
            <xsl:with-param name="char" select="$char" />
         </xsl:call-template>
      </xsl:when>
      <!-- otherwise, return the value of the string -->
      <xsl:otherwise><xsl:value-of select="$string" /></xsl:otherwise>
   </xsl:choose>
</xsl:template>
</xsl:stylesheet>
