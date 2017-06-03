<?xml version="1.0"?>
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
    This XSL is based heavily on the Spring Frameworks' XSL script
-->
<!DOCTYPE xsl:stylesheet [
	<!ENTITY admon_gfx_path "src/docbkx/images/admons/">
	<!ENTITY copyright "&#xA9;">
	]>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
	xmlns:fo="http://www.w3.org/1999/XSL/Format" version="1.0">
	<xsl:import href="urn:docbkx:stylesheet"/>
	<xsl:template match="imagedata">
		<xsl:variable name="imagefile">
			<xsl:value-of select="substring-after(@fileref,'images/')"/>
		</xsl:variable>
		<fo:block>
			<!-- TODO:  make this more dynamic-->
			<fo:external-graphic 
				src="{concat('file:target/site/book/images/',$imagefile)}"/>
		</fo:block>
	</xsl:template>
	<!--###################################################
                     Custom Title Page
      ################################################### -->
	<xsl:template name="book.titlepage.recto">
		<fo:block>
			<fo:table table-layout="fixed" width="175mm">
				<fo:table-column column-width="175mm"/>
				<fo:table-body>
					<fo:table-row>
						<fo:table-cell text-align="center">
							<fo:block>
								<fo:external-graphic 
									src="file:src/docbkx/images/logo.jpeg"/>
							</fo:block>
							<fo:block font-family="Helvetica" font-size="22pt" 
								padding-before="10mm">
								<xsl:value-of select="bookinfo/subtitle"/>
							</fo:block>
							<fo:block font-family="Helvetica" font-size="12pt" 
								padding="10mm"> Version <xsl:value-of 
								select="bookinfo/releaseinfo"/> </fo:block>
						</fo:table-cell>
					</fo:table-row>
					<fo:table-row>
						<fo:table-cell text-align="center">
							<fo:block font-family="Helvetica" font-size="14pt" 
								padding="10mm">
								<xsl:value-of select="bookinfo/pubdate"/>
							</fo:block>
						</fo:table-cell>
					</fo:table-row>
					<fo:table-row>
						<fo:table-cell text-align="center">
							<fo:block font-family="Helvetica" font-size="12pt" 
								padding="10mm">
								<xsl:text>Copyright &copyright; 2004-2016, 
									Apache Software Foundation</xsl:text>
							</fo:block>
							<!--<fo:block>-->
							<!--<fo:external-graphic src="file:src/docbkx/images/asf-logo.gif"/>-->
							<!--</fo:block>-->
							<fo:block font-family="Helvetica" font-size="10pt" 
								padding="1mm">
								<xsl:apply-templates 
									select="bookinfo/legalnotice/*"/>
								<!--<xsl:value-of select="bookinfo/legalnotice"/>-->
							</fo:block>
						</fo:table-cell>
					</fo:table-row>
				</fo:table-body>
			</fo:table>
		</fo:block>
	</xsl:template>
	<!-- Prevent blank pages in output -->
	<xsl:template name="book.titlepage.before.verso">
	</xsl:template>
	<xsl:template name="book.titlepage.verso">
	</xsl:template>
	<xsl:template name="book.titlepage.separator">
	</xsl:template>
	<!--###################################################
                        Header
      ################################################### -->
	<!-- More space in the center header for long text -->
	<xsl:attribute-set name="header.content.properties">
		<xsl:attribute name="font-family">
			<xsl:value-of select="$body.font.family"/>
		</xsl:attribute>
		<xsl:attribute name="margin-left">-5em</xsl:attribute>
		<xsl:attribute name="margin-right">-5em</xsl:attribute>
	</xsl:attribute-set>
	<!--###################################################
                        Custom Footer
      ################################################### -->
	<xsl:template name="footer.content">
		<xsl:param name="pageclass" select="''"/>
		<xsl:param name="sequence" select="''"/>
		<xsl:param name="position" select="''"/>
		<xsl:param name="gentext-key" select="''"/>
		<xsl:variable name="Version">
			<xsl:if test="//releaseinfo">
				<xsl:text>Apache ActiveMQ (</xsl:text>
				<xsl:value-of select="//releaseinfo"/>
				<xsl:text>)</xsl:text>
			</xsl:if>
		</xsl:variable>
		<xsl:choose>
			<xsl:when test="$sequence='blank'">
				<xsl:if test="$position = 'center'">
					<xsl:value-of select="$Version"/>
				</xsl:if>
			</xsl:when>
			<!-- for double sided printing, print page numbers on alternating sides (of the page) -->
			<xsl:when test="$double.sided != 0">
				<xsl:choose>
					<xsl:when test="$sequence = 'even' and $position='left'">
						<fo:page-number/>
					</xsl:when>
					<xsl:when test="$sequence = 'odd' and $position='right'">
						<fo:page-number/>
					</xsl:when>
					<xsl:when test="$position='center'">
						<xsl:value-of select="$Version"/>
					</xsl:when>
				</xsl:choose>
			</xsl:when>
			<!-- for single sided printing, print all page numbers on the right (of the page) -->
			<xsl:when test="$double.sided = 0">
				<xsl:choose>
					<xsl:when test="$position='center'">
						<xsl:value-of select="$Version"/>
					</xsl:when>
					<xsl:when test="$position='right'">
						<fo:page-number/>
					</xsl:when>
				</xsl:choose>
			</xsl:when>
		</xsl:choose>
	</xsl:template>
	<!--###################################################
                        Extensions
      ################################################### -->
	<!-- These extensions are required for table printing and other stuff -->
	<xsl:param name="use.extensions">1</xsl:param>
	<xsl:param name="tablecolumns.extension">0</xsl:param>
	<xsl:param name="callout.extensions">1</xsl:param>
	<!-- FOP provide only PDF Bookmarks at the moment -->
	<xsl:param name="fop.extensions">1</xsl:param>
	<!--###################################################
                        Table Of Contents
      ################################################### -->
	<!-- Generate the TOCs for named components only -->
	<xsl:param name="generate.toc"> book toc </xsl:param>
	<!-- Show only Sections up to level 3 in the TOCs -->
	<!--<xsl:param name="toc.section.depth">2</xsl:param>-->
	<!-- Dot and Whitespace as separator in TOC between Label and Title-->
	<!--<xsl:param name="autotoc.label.separator" select="'.  '"/>-->
	<!--###################################################
                     Paper & Page Size
      ################################################### -->
	<!-- Paper type, no headers on blank pages, no double sided printing -->
	<xsl:param name="paper.type" select="'A4'"/>
	<xsl:param name="double.sided">0</xsl:param>
	<xsl:param name="headers.on.blank.pages">0</xsl:param>
	<xsl:param name="footers.on.blank.pages">0</xsl:param>
	<!-- Space between paper border and content (chaotic stuff, don't touch) -->
	<xsl:param name="page.margin.top">5mm</xsl:param>
	<xsl:param name="region.before.extent">10mm</xsl:param>
	<xsl:param name="body.margin.top">10mm</xsl:param>
	<xsl:param name="body.margin.bottom">15mm</xsl:param>
	<xsl:param name="region.after.extent">10mm</xsl:param>
	<xsl:param name="page.margin.bottom">0mm</xsl:param>
	<xsl:param name="page.margin.outer">18mm</xsl:param>
	<xsl:param name="page.margin.inner">18mm</xsl:param>
	<!-- No intendation of Titles -->
	<xsl:param name="title.margin.left">0pc</xsl:param>
	<!--###################################################
                     Fonts & Styles
      ################################################### -->
	<!-- Left aligned text and no hyphenation -->
	<xsl:param name="alignment">justify</xsl:param>
	<xsl:param name="hyphenate">false</xsl:param>
	<!-- Default Font size -->
	<xsl:param name="body.font.master">11</xsl:param>
	<xsl:param name="body.font.small">8</xsl:param>
	<!-- Line height in body text -->
	<xsl:param name="line-height">1.4</xsl:param>
	<!-- Monospaced fonts are smaller than regular text -->
	<xsl:attribute-set name="monospace.properties">
		<xsl:attribute name="font-family">
			<xsl:value-of select="$monospace.font.family"/>
		</xsl:attribute>
		<xsl:attribute name="font-size">0.8em</xsl:attribute>
	</xsl:attribute-set>
	<!--###################################################
                     Tables
      ################################################### -->
	<!-- The table width should be adapted to the paper size -->
	<xsl:param name="default.table.width">17.4cm</xsl:param>
	<!-- Some padding inside tables -->
	<xsl:attribute-set name="table.cell.padding">
		<xsl:attribute name="padding-left">4pt</xsl:attribute>
		<xsl:attribute name="padding-right">4pt</xsl:attribute>
		<xsl:attribute name="padding-top">4pt</xsl:attribute>
		<xsl:attribute name="padding-bottom">4pt</xsl:attribute>
	</xsl:attribute-set>
	<!-- Only hairlines as frame and cell borders in tables -->
	<xsl:param name="table.frame.border.thickness">0.1pt</xsl:param>
	<xsl:param name="table.cell.border.thickness">0.1pt</xsl:param>
	<!--###################################################
                           Labels
      ################################################### -->
	<!-- Label Chapters and Sections (numbering) -->
	<xsl:param name="chapter.autolabel">1</xsl:param>
	<xsl:param name="section.autolabel" select="1"/>
	<xsl:param name="section.label.includes.component.label" select="1"/>
	<!--###################################################
                           Titles
      ################################################### -->
	<!-- Chapter title size -->
	<xsl:attribute-set name="chapter.titlepage.recto.style">
		<xsl:attribute name="text-align">left</xsl:attribute>
		<xsl:attribute name="font-weight">bold</xsl:attribute>
		<xsl:attribute name="font-size">
			<xsl:value-of select="$body.font.master * 1.8"/>
			<xsl:text>pt</xsl:text>
		</xsl:attribute>
	</xsl:attribute-set>
	<!-- Why is the font-size for chapters hardcoded in the XSL FO templates?
   Let's remove it, so this sucker can use our attribute-set only... -->
	<xsl:template match="title" mode="chapter.titlepage.recto.auto.mode">
		<fo:block xmlns:fo="http://www.w3.org/1999/XSL/Format" 
			xsl:use-attribute-sets="chapter.titlepage.recto.style">
			<xsl:call-template name="component.title">
				<xsl:with-param name="node" 
					select="ancestor-or-self::chapter[1]"/>
			</xsl:call-template>
		</fo:block>
	</xsl:template>
	<!-- Sections 1, 2 and 3 titles have a small bump factor and padding -->
	<xsl:attribute-set name="section.title.level1.properties">
		<xsl:attribute name="space-before.optimum">0.8em</xsl:attribute>
		<xsl:attribute name="space-before.minimum">0.8em</xsl:attribute>
		<xsl:attribute name="space-before.maximum">0.8em</xsl:attribute>
		<xsl:attribute name="font-size">
			<xsl:value-of select="$body.font.master * 1.5"/>
			<xsl:text>pt</xsl:text>
		</xsl:attribute>
		<xsl:attribute name="space-after.optimum">0.1em</xsl:attribute>
		<xsl:attribute name="space-after.minimum">0.1em</xsl:attribute>
		<xsl:attribute name="space-after.maximum">0.1em</xsl:attribute>
	</xsl:attribute-set>
	<xsl:attribute-set name="section.title.level2.properties">
		<xsl:attribute name="space-before.optimum">0.6em</xsl:attribute>
		<xsl:attribute name="space-before.minimum">0.6em</xsl:attribute>
		<xsl:attribute name="space-before.maximum">0.6em</xsl:attribute>
		<xsl:attribute name="font-size">
			<xsl:value-of select="$body.font.master * 1.25"/>
			<xsl:text>pt</xsl:text>
		</xsl:attribute>
		<xsl:attribute name="space-after.optimum">0.1em</xsl:attribute>
		<xsl:attribute name="space-after.minimum">0.1em</xsl:attribute>
		<xsl:attribute name="space-after.maximum">0.1em</xsl:attribute>
	</xsl:attribute-set>
	<xsl:attribute-set name="section.title.level3.properties">
		<xsl:attribute name="space-before.optimum">0.4em</xsl:attribute>
		<xsl:attribute name="space-before.minimum">0.4em</xsl:attribute>
		<xsl:attribute name="space-before.maximum">0.4em</xsl:attribute>
		<xsl:attribute name="font-size">
			<xsl:value-of select="$body.font.master * 1.0"/>
			<xsl:text>pt</xsl:text>
		</xsl:attribute>
		<xsl:attribute name="space-after.optimum">0.1em</xsl:attribute>
		<xsl:attribute name="space-after.minimum">0.1em</xsl:attribute>
		<xsl:attribute name="space-after.maximum">0.1em</xsl:attribute>
	</xsl:attribute-set>
	<!-- Titles of formal objects (tables, examples, ...) -->
	<xsl:attribute-set name="formal.title.properties" 
		use-attribute-sets="normal.para.spacing">
		<xsl:attribute name="font-weight">bold</xsl:attribute>
		<xsl:attribute name="font-size">
			<xsl:value-of select="$body.font.master"/>
			<xsl:text>pt</xsl:text>
		</xsl:attribute>
		<xsl:attribute name="hyphenate">false</xsl:attribute>
		<xsl:attribute name="space-after.minimum">0.4em</xsl:attribute>
		<xsl:attribute name="space-after.optimum">0.6em</xsl:attribute>
		<xsl:attribute name="space-after.maximum">0.8em</xsl:attribute>
	</xsl:attribute-set>
	<!--###################################################
                        Programlistings
      ################################################### -->
	<!-- Verbatim text formatting (programlistings) -->
	<xsl:attribute-set name="monospace.verbatim.properties">
		<xsl:attribute name="font-size">
			<xsl:value-of select="$body.font.small * 1.0"/>
			<xsl:text>pt</xsl:text>
		</xsl:attribute>
		<xsl:attribute name="wrap-option">wrap</xsl:attribute>
	</xsl:attribute-set>
	<xsl:attribute-set name="verbatim.properties">
		<xsl:attribute name="space-before.minimum">1em</xsl:attribute>
		<xsl:attribute name="space-before.optimum">1em</xsl:attribute>
		<xsl:attribute name="space-before.maximum">1em</xsl:attribute>
		<xsl:attribute name="border-color">#444444</xsl:attribute>
		<xsl:attribute name="border-style">solid</xsl:attribute>
		<xsl:attribute name="border-width">0.1pt</xsl:attribute>
		<xsl:attribute name="padding-top">0.5em</xsl:attribute>
		<xsl:attribute name="padding-left">0.5em</xsl:attribute>
		<xsl:attribute name="padding-right">0.5em</xsl:attribute>
		<xsl:attribute name="padding-bottom">0.5em</xsl:attribute>
		<xsl:attribute name="margin-left">0.5em</xsl:attribute>
		<xsl:attribute name="margin-right">0.5em</xsl:attribute>
	</xsl:attribute-set>
	<!-- Shade (background) programlistings -->
	<xsl:param name="shade.verbatim">1</xsl:param>
	<xsl:attribute-set name="shade.verbatim.style">
		<xsl:attribute name="background-color">#F0F0F0</xsl:attribute>
	</xsl:attribute-set>
	<!--###################################################
                           Callouts
      ################################################### -->
	<!-- Use images for callouts instead of (1) (2) (3) -->
	<xsl:param name="callout.graphics">0</xsl:param>
	<xsl:param name="callout.unicode">1</xsl:param>
	<!-- Place callout marks at this column in annotated areas -->
	<xsl:param name="callout.defaultcolumn">90</xsl:param>
	<!--###################################################
                         Admonitions
      ################################################### -->
	<!-- Use nice graphics for admonitions -->
	<xsl:param name="admon.graphics">'1'</xsl:param>
	<xsl:param name="admon.graphics.path">&admon_gfx_path;</xsl:param>
	<!--###################################################
                            Misc
      ################################################### -->
	<!-- Placement of titles -->
	<xsl:param name="formal.title.placement"> figure after example before 
		equation before table before procedure before </xsl:param>
	<!-- Format Variable Lists as Blocks (prevents horizontal overflow) -->
	<xsl:param name="variablelist.as.blocks">1</xsl:param>
	<!-- The horrible list spacing problems -->
	<xsl:attribute-set name="list.block.spacing">
		<xsl:attribute name="space-before.optimum">0.8em</xsl:attribute>
		<xsl:attribute name="space-before.minimum">0.8em</xsl:attribute>
		<xsl:attribute name="space-before.maximum">0.8em</xsl:attribute>
		<xsl:attribute name="space-after.optimum">0.1em</xsl:attribute>
		<xsl:attribute name="space-after.minimum">0.1em</xsl:attribute>
		<xsl:attribute name="space-after.maximum">0.1em</xsl:attribute>
	</xsl:attribute-set>
	<!--###################################################
                colored and hyphenated links
      ################################################### -->
	<xsl:template match="ulink">
		<fo:basic-link external-destination="{@url}" 
			xsl:use-attribute-sets="xref.properties" 
			text-decoration="underline" color="blue">
			<xsl:choose>
				<xsl:when test="count(child::node())=0">
					<xsl:value-of select="@url"/>
				</xsl:when>
				<xsl:otherwise>
					<xsl:apply-templates/>
				</xsl:otherwise>
			</xsl:choose>
		</fo:basic-link>
	</xsl:template>
</xsl:stylesheet>