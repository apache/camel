<?xml version="1.0" encoding="utf-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="xml" indent="no"/>
    <xsl:strip-space elements="*"/>

    <xsl:param name="xmlToAggregate"/>

    <xsl:template match="/">
        <item>
            <xsl:value-of select="."/>
            <xsl:value-of select="$xmlToAggregate"/>
        </item>
    </xsl:template>

</xsl:stylesheet>