<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:h="http://www.webserviceX.NET/">
    <xsl:strip-space elements="*"/>
    <xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes"/>

	<!-- copy everything else -->
	<xsl:template match="node()|@*">
		<xsl:copy>
			<xsl:copy-of select="@*"/>
			<xsl:apply-templates select="node()"/>
		</xsl:copy>
	</xsl:template>
</xsl:stylesheet>