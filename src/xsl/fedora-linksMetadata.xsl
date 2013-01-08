<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
    xmlns:ns0="info:fedora/fedora-system:def/model#">
  <xsl:param name="objid"/>
  <xsl:template match="/">
    <rdf:RDF>
      <rdf:Description rdf:about="info:fedora/{$objid}">
        <ns0:hasModel rdf:resource="info:fedora/afmodel:DamsObject"/>
      </rdf:Description>
    </rdf:RDF>
  </xsl:template>
</xsl:stylesheet>
