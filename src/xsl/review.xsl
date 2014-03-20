<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
      xmlns:dams="http://library.ucsd.edu/ontology/dams#"
      xmlns:mads="http://www.loc.gov/mads/rdf/v1#"
      xmlns:owl="http://www.w3.org/2002/07/owl#"
      xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
      xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
      xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#"
	  exclude-result-prefixes="dams mads owl rdf rdfs">
  <xsl:output method="html" encoding="utf-8" version="4.0"/>
  
	<xsl:template match="/rdf:RDF">
		<xsl:text disable-output-escaping="yes">&lt;!DOCTYPE HTML&gt;</xsl:text>
		<html xmlns="http://www.w3.org/1999/xhtml">
			<head>
				<xsl:text disable-output-escaping="yes">&lt;/META&gt;</xsl:text>
				<title>DAMS4 Data Review</title>
				
				<style>
					body {text-align: center}
					ul {list-style-type: none; border: 1px solid #bbb; border-bottom: none; border-right: none; padding-left:60px; padding-top: 0px;padding-bottom: 2px; margin: 0px;}
					li {list-style-type: none;margin:0px;padding:0px;width:100%;border: none;}
					table {border-collapse:collapse;width:100%;cell-spacing:0px;cell-padding:0px;margin:0px;padding:0px;}
					
					h4 {margin-bottom: 0.25em; }
					
					.damsPath {font-family: Arial, Helvetica, sans-serif;color: #C00;text-align: right;font-size: 12px;white-space: nowrap;float: right;display: inline;padding-right: 5px;}
					.propertyBox {width: 1200px;align: center;}
					.record {font-family: Arial, Helvetica, sans-serif;font-size: 16px;font-weight: bold;text-align: left;background-color: #444;padding-left: 5px;color: #FFF;width: 140px;height:24px;}
					.recordValue {background-color: #444;color: #FFF;width: 500px;padding-left:10px;}
					.subGroup{margin-bottom:5px;border-bottom: 1px solid #bbb;border-right: 1px solid #bbb;}
					.subGroup_hl{margin-bottom:5px;border: 2px solid #FF6633;}
					.damsResource {font-family: Arial, Helvetica, sans-serif;font-size: 12px;font-weight: normal;}
					.popertyLabel_1_value {font-size: 12px;font-weight: bold;text-align: left;background-color: #bbb;color:#003399;padding-left: 5px;vertical-align: top;min-width: 100px;white-space:nowrap;}					
					.popertyLabel_1 {font-family: Arial, Helvetica, sans-serif;font-size: 12px;font-weight: bold;text-align: left;background-color: #bbb;padding-left: 5px;vertical-align: top;width: 132px;color: #003399;}
					.popertyLabel_2 {margin-left:15;font-family: Arial, Helvetica, sans-serif;font-size: 12px;font-weight: bold;text-align: left;background-color: #D4D4D4;width: 120px;vertical-align: top;}
					.propertyIcon {width:16px;height:16px;background-image: url("http://library.ucsd.edu/dc/images/glyphicons-halflings-white.png");background-position:  -46px  -118px;}
					.popertyValue {font-family: Arial, Helvetica, sans-serif;font-size: 12px;font-weight: normal;text-align: left;background-color: #D4D4D4;padding-left: 3px;color: #003399;vertical-align: top;}	
					.title {font-family: Arial, Helvetica, sans-serif;font-size: 24px;font-weight: bold;text-align: center;padding: 20px;color: #333;}
					
				</style>
			</head>
		
			<body>
				<xsl:for-each select="*">
					<table>
						<tr>
							<td align="center">
								<div class="title">UC San Diego DAMS 4 Data Review</div>
								<div class="propertyBox" >
									<xsl:call-template name="damsResource"/>
									<xsl:for-each select="*[local-name()!='event' and not(contains(name(), 'Component') or contains(name(), 'File') or contains(name(), 'Collection'))]">
										<xsl:sort select="name()" order="descending"/>
										<xsl:sort select="*/dams:order" order="ascending"/>
										<xsl:sort select="*/@rdf:about" order="ascending"/>
										<xsl:call-template name="damsTopElement"/>
									</xsl:for-each>
									<xsl:for-each select="*[contains(name(), 'Collection')]">
										<xsl:sort select="name()" order="descending"/>
										<xsl:sort select="*/dams:order" order="ascending"/>
										<xsl:sort select="*/@rdf:about" order="ascending"/>
										<xsl:call-template name="damsTopElement"/>
									</xsl:for-each>
									<xsl:for-each select="*[contains(name(), 'File')]">
										<xsl:sort select="name()" order="descending"/>
										<xsl:sort select="*/dams:order" order="ascending"/>
										<xsl:sort select="*/@rdf:about" order="ascending"/>
										<xsl:call-template name="damsTopElement"/>
									</xsl:for-each>
									<xsl:for-each select="*[contains(name(), 'Component')]">
										<xsl:sort select="name()" order="descending"/>
										<xsl:sort select="*/dams:order" order="ascending"/>
										<xsl:sort select="*/@rdf:about" order="ascending"/>
										<xsl:call-template name="damsTopElement"/>
									</xsl:for-each>
								</div>
							</td>
						</tr>
					</table>
				</xsl:for-each>
			</body>
		</html>
	</xsl:template>
	 
	<xsl:template name="damsResource">
		<div>
			<table>
				<tr>
					<td class="record"><xsl:value-of select="local-name()"/></td>
					<td class="recordValue"><xsl:value-of select="@rdf:about"/></td>
					<td style="background-color:#444;color:#fff;"><span class="damsPath" style="color:#fff;"><xsl:call-template name="xPath"/></span></td>
				</tr>
			</table>
		</div>
	</xsl:template>
	<xsl:template name="damsTopElement">
		<ul style="border: none;padding-top: 10px;padding-left: 0;">
			<xsl:call-template name="prop">
				<xsl:with-param name="parentPath"><xsl:call-template name="xPath"><xsl:with-param name="node" select="."/></xsl:call-template></xsl:with-param>
  				<xsl:with-param name="propNode" select="."/>
			</xsl:call-template>
			<xsl:if test="@rdf:resource">
				<xsl:variable name="resid"><xsl:value-of select="@rdf:resource"/></xsl:variable>
				<xsl:for-each select="//*[@rdf:about=$resid]">
					<li>
						<ul class="subGroup" onmouseover="javascript:this.className='subGroup_hl';" onmouseout="javascript:this.className='subGroup';">
							<xsl:call-template name="damsThing">
									<xsl:with-param name="parentPath"><xsl:call-template name="xPath"><xsl:with-param name="node" select=".."/></xsl:call-template></xsl:with-param>
							</xsl:call-template>
						</ul>
					</li>
				</xsl:for-each>
			</xsl:if>
			<xsl:for-each select="*">
				<li>
					<ul class="subGroup" onmouseover="javascript:this.className='subGroup_hl';" onmouseout="javascript:this.className='subGroup';">
						<xsl:call-template name="damsThing"/>
					</ul>
				</li>
			</xsl:for-each>
		</ul>
	</xsl:template>
	<xsl:template name="damsThing">
		<xsl:param name="parentPath"/>
	  	<xsl:variable name="xPath">
		  	<xsl:call-template name="xPath">
		  		<xsl:with-param name="node" select="."/>
		  	</xsl:call-template>
	  	</xsl:variable>
	  	<xsl:variable name="resid"><xsl:value-of select="@rdf:about"/></xsl:variable>
		<xsl:variable name="curParentPath">
			<xsl:choose>
	    		<xsl:when test="$parentPath"><xsl:value-of select="$parentPath"/></xsl:when>
	    		<xsl:otherwise><xsl:value-of select="$xPath"/></xsl:otherwise>
	    	</xsl:choose>
		</xsl:variable>
		<xsl:choose>
			<xsl:when test="*">
				<li>
					<table>
						<tr>
							<td class="popertyLabel_1"><xsl:value-of select="local-name()"/></td>
							<td class="popertyLabel_1_value"><xsl:value-of select="@rdf:about"/></td>
							<td style="background-color: #bbb;">
								<span class="damsPath">
							    	<xsl:choose>
							    		<xsl:when test="$parentPath"><xsl:value-of select="substring-after($xPath, concat($parentPath,'/'))"/></xsl:when>
							    		<xsl:otherwise><xsl:value-of select="$xPath"/></xsl:otherwise>
							    	</xsl:choose>
						    	<xsl:text> </xsl:text>
						    	</span>				
							</td>
						</tr>
					</table>
				</li>
				<xsl:for-each select="*[local-name() != 'event']">
					<xsl:sort select="name()" order="descending"/>
					<xsl:sort select="dams:order" order="ascending"/>
					<xsl:sort select="*/@rdf:about" order="ascending"/>
						<xsl:choose>
							<xsl:when test="string-length(*[@rdf:about])=0 and string-length(@rdf:parseType)=0">
								<!-- properties, Instance -->
								
								<xsl:call-template name="prop">
									<xsl:with-param name="propNode" select="."/>
									<xsl:with-param name="parentPath"><xsl:call-template name="xPath"><xsl:with-param name="node" select=".."/></xsl:call-template></xsl:with-param>
								</xsl:call-template>
								<xsl:choose>
									<xsl:when test="@rdf:resource">
										<xsl:variable name="resid"><xsl:value-of select="@rdf:resource"/></xsl:variable>
										<xsl:for-each select="//*[@rdf:about=$resid and not (contains(name(), 'Collection'))]">
											<li>
												<ul>
													<xsl:call-template name="damsThing">
															<xsl:with-param name="parentPath"><xsl:call-template name="xPath"><xsl:with-param name="node" select="../.."/></xsl:call-template></xsl:with-param>
													</xsl:call-template>
												</ul>
											</li>
										</xsl:for-each>
									</xsl:when>
									<xsl:otherwise>
										<xsl:for-each select="*">
											<li>
												<ul>
													<xsl:call-template name="damsThing">
														<xsl:with-param name="parentPath"><xsl:value-of select="$curParentPath"/></xsl:with-param>
													</xsl:call-template>
												</ul>
											</li>
										</xsl:for-each>
									</xsl:otherwise>
								</xsl:choose>
							</xsl:when>
							<xsl:when test="./*[@rdf:about] or @rdf:parseType">
								<!-- linked subjects, blankNode, componentList-->
								<xsl:call-template name="prop">
									<xsl:with-param name="propNode" select="."/>
									<xsl:with-param name="parentPath"><xsl:call-template name="xPath"><xsl:with-param name="node" select=".."/></xsl:call-template></xsl:with-param>
								</xsl:call-template>
								
								<xsl:choose>
										<xsl:when test="@rdf:parseType='Resource'">
											<!-- blankNode -->
											<li>
												<ul>
													<xsl:for-each select="*">
														<xsl:call-template name="prop">
															<xsl:with-param name="propNode" select="."/>
															<xsl:with-param name="parentPath"><xsl:call-template name="xPath"><xsl:with-param name="node" select=".."/></xsl:call-template></xsl:with-param>
														</xsl:call-template>
													</xsl:for-each>
												</ul>
											</li>
										</xsl:when>
										<xsl:otherwise>
											<xsl:for-each select="*">
												<li>
													<ul>
														<xsl:call-template name="damsThing">
															<xsl:with-param name="parentPath"><xsl:call-template name="xPath"><xsl:with-param name="node" select="../.."/></xsl:call-template></xsl:with-param>
														</xsl:call-template>
													</ul>
												</li>
											</xsl:for-each>
										</xsl:otherwise>
									</xsl:choose>
							</xsl:when>
							
							<xsl:when test="@rdf:parseType">
								<!-- blankNode, componentList -->
								<xsl:call-template name="prop">
									<xsl:with-param name="propNode" select="."/>
									<xsl:with-param name="parentPath"><xsl:call-template name="xPath"><xsl:with-param name="node" select=".."/></xsl:call-template></xsl:with-param>
								</xsl:call-template>
								
								<xsl:for-each select="*">
									<li>
										<ul>
											<xsl:call-template name="damsThing">
												<xsl:with-param name="parentPath"><xsl:value-of select="$curParentPath"/></xsl:with-param>
											</xsl:call-template>
										</ul>
									</li>
								</xsl:for-each>
							</xsl:when>
						</xsl:choose>
				</xsl:for-each>
			</xsl:when>
			<xsl:otherwise>
				<!-- Empty linked resource -->
				<xsl:for-each select="//*[@rdf:about=$resid and ./*]">
					<xsl:call-template name="damsThing">
							<xsl:with-param name="parentPath"><xsl:call-template name="xPath"><xsl:with-param name="node" select="../.."/></xsl:call-template></xsl:with-param>
					</xsl:call-template>
				</xsl:for-each>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	
	<xsl:template name="prop">
		<xsl:param name="parentPath"/>
		<xsl:param name="propName"/>
	  	<xsl:param name="propNode"/>
	  	<xsl:if test="$propNode">
		  	<xsl:variable name="xPath">
			  	<xsl:call-template name="xPath">
			  		<xsl:with-param name="node" select="$propNode"/>
			  	</xsl:call-template>
		  	</xsl:variable>
		  	<xsl:variable name="propLabel">
			  	<xsl:choose>
			    		<xsl:when test="$propName"><xsl:value-of select="$propName"/></xsl:when>
			    		<xsl:otherwise><xsl:value-of select="name($propNode/.)"/></xsl:otherwise>
			    	</xsl:choose>
		  	</xsl:variable>
		  	<xsl:variable name="resid"><xsl:value-of select="@rdf:resource"/></xsl:variable>
		  	<xsl:variable name="propValue">
			  	<xsl:choose>
			    		<xsl:when test="$propNode/text() | @rdf:about"><xsl:value-of select="$propNode/text() | @rdf:about"/></xsl:when>
			    		<xsl:when test="//*[@rdf:about=$resid and not (contains(name(), 'Collection'))]"></xsl:when>
			    		<xsl:otherwise><xsl:value-of select="$resid"/></xsl:otherwise>
			    	</xsl:choose>
		  	</xsl:variable>
		  	<li>
			  	<table>
			  		<tr>
			  			<td class="popertyLabel_2" style="width:16px;"><div class="propertyIcon"></div></td>
					    <td class="popertyLabel_2"><xsl:value-of select="$propLabel"/></td>
					    <td class="popertyValue">
					    	<xsl:choose>
						    	<xsl:when test="contains($propValue, '&amp;')"><xsl:text disable-output-escaping="yes"><xsl:value-of select="translate($propValue, '&amp;', '&amp;amp;')"/></xsl:text></xsl:when>
								<xsl:otherwise><xsl:value-of select="$propValue" disable-output-escaping="yes"/></xsl:otherwise>
						    </xsl:choose>
						</td>
					    <!-- 
					    <td class="damsPath">
					    	<xsl:choose>
					    		<xsl:when test="$parentPath"><xsl:value-of select="substring-after($xPath, concat($parentPath,'/'))"/></xsl:when>
					    		<xsl:otherwise><xsl:value-of select="$xPath"/></xsl:otherwise>
					    	</xsl:choose>
					    	<xsl:text> </xsl:text>
					    </td>
					     -->
				    </tr>
				 </table>
		  	 </li>
	  </xsl:if>
	</xsl:template>
	
	<xsl:template name="xPath">
		<xsl:param name="node"/>
		<xsl:choose>
		<xsl:when test="$node">
		  <xsl:for-each select="$node/ancestor-or-self::*">
		    <xsl:text>/</xsl:text><xsl:value-of select="name()" />
		  </xsl:for-each>	
		</xsl:when>
		<xsl:otherwise>
		  <xsl:for-each select="ancestor-or-self::*">
		    <xsl:text>/</xsl:text><xsl:value-of select="name()" />
		  </xsl:for-each>
	  </xsl:otherwise>
	  </xsl:choose>
	</xsl:template>
</xsl:stylesheet>
