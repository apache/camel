<?xml version="1.0" encoding="UTF-8"?>
<!--

    Licensed to the Apache Software Foundation (ASF) under one or more
    contributor license agreements.  See the NOTICE file distributed with
    this work for additional information regarding copyright ownership.
    The ASF licenses this file to You under the Apache License, Version 2.0
    (the "License"); you may not use this file except in compliance with
    the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

-->
<!--
    Extract embedded Schematron schemas in W3C XML Schemas schemas    
        
	For usage details, see http://www.topologi.com/resources/schtrn_xsd_paper.html

        based on an original transform by Eddie Robertsson
        2001/04/21      fn: added support for included schemas
        2001/06/27      er: changed XMl Schema prefix from xsd: to xs: and changed to the Rec namespace
        2010/04/14    	rj: Update for ISO Schematron using xslt2	MIT licensed 2010-07-10
-->

<!--
Open Source Initiative OSI - The MIT License:Licensing
[OSI Approved License]

Attribution is polite.

The MIT License

Copyright (c) 2002-2010 Rick Jelliffe and Topologi Pty. Ltd.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
-->
<xsl:transform version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
xmlns:sch="http://purl.oclc.org/dsdl/schematron" xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <!-- Set the output to be XML with an XML declaration and use indentation -->
        <xsl:output method="xml" omit-xml-declaration="no" indent="yes" standalone="yes"/>
        <!-- -->
        <!-- match schema and call recursive template to extract included schemas -->
        <!-- -->
        <xsl:template match="xs:schema">
                <!-- call the schema definition template ... -->
                <xsl:call-template name="gatherSchema">
                        <!-- ... with current current root as the $schemas parameter ... -->
                        <xsl:with-param name="schemas" select="/"/>
                        <!-- ... and any includes in the $include parameter -->
                        <xsl:with-param name="includes" 
						select="document(/xs:schema/xs:*[self::xs:include or self::xs:import or self::xs:redefine]/@schemaLocation)"/>
                </xsl:call-template>
        </xsl:template>
        <!-- -->
        <!-- gather all included schemas into a single parameter variable -->
        <!-- -->
        <xsl:template name="gatherSchema">
                <xsl:param name="schemas"/>
                <xsl:param name="includes"/>
                <xsl:choose>
                        <xsl:when test="count($schemas) &lt; count($schemas | $includes)">
                                <!-- when $includes includes something new, recurse ... -->
                                <xsl:call-template name="gatherSchema">
                                        <!-- ... with current $includes added to the $schemas parameter ... -->
                                        <xsl:with-param name="schemas" select="$schemas | $includes"/>
                                        <!-- ... and any *new* includes in the $include parameter -->
                                        <xsl:with-param name="includes" 
										select="document($includes/xs:schema/xs:*[self::xs:include or self::xs:import or self::xs:redefine]/@schemaLocation)"/>
                                </xsl:call-template>
                        </xsl:when>
                        <xsl:otherwise>
                                <!-- we have the complete set of included schemas, 
								so now let's output the embedded schematron -->
                                <xsl:call-template name="output">
                                        <xsl:with-param name="schemas" select="$schemas"/>
                                </xsl:call-template>
                        </xsl:otherwise>
                </xsl:choose>
        </xsl:template>
        <!-- -->
        <!-- output the schematron information -->
        <!-- -->
        <xsl:template name="output">
                <xsl:param name="schemas"/>
                <!-- -->
                <sch:schema queryBinding="xslt2">
                        <!-- get header-type elements - eg title and especially ns -->
                        <!-- title (just one) -->
                        <xsl:copy-of select="$schemas//xs:appinfo/sch:title[1]"/>
                        <!-- get remaining schematron schema children -->
                        <!-- get non-blank namespace elements, dropping duplicates -->
                        <xsl:for-each select="$schemas//xs:appinfo/sch:ns">
                                <xsl:if test="generate-id(.) = 
								generate-id($schemas//xs:appinfo/sch:ns[@prefix = current()/@prefix][1])">
                                        <xsl:copy-of select="."/>
                                </xsl:if>
                        </xsl:for-each>
                        <xsl:copy-of select="$schemas//xs:appinfo/sch:phase"/>
                        <xsl:copy-of select="$schemas//xs:appinfo/sch:pattern"/>
                        <sch:diagnostics>
                                <xsl:copy-of select="$schemas//xs:appinfo/sch:diagnostics/*"/>
                        </sch:diagnostics>
                </sch:schema>
        </xsl:template>
        <!-- -->
</xsl:transform>