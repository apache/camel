<?xml version="1.0" encoding="UTF-8"?>
<xsl:transform version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <!-- input cacheValue -->
    <xsl:param name="cacheValue" select="node-set"/>

    <xsl:template match="##match_token##">
        <xsl:apply-templates select="$cacheValue"/>
    </xsl:template>

    <!-- Identity transform template -->
	<xsl:template match="node()|@*">
        <!-- Copy the current node -->
        <xsl:copy>
            <!-- Including any attributes it has and any child nodes -->
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:transform>
