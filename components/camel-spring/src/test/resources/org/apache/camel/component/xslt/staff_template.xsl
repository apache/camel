<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

    <xsl:template match="staff/programmer">
        <html>
            <body>
                <xsl:apply-templates select="name"/>
                <xsl:apply-templates select="dob"/>
                <xsl:apply-templates select="age"/>
                <br/>
            </body>
        </html>
    </xsl:template>

    <xsl:template match="name">
        <span style="font-size=22px;">
            <xsl:value-of select="."/>
        </span>
        <br/>
    </xsl:template>

    <xsl:template match="dob">
        DOB:
        <span style="color:blue;">
            <xsl:value-of select="."/>
        </span>
        <br/>
    </xsl:template>

    <xsl:template match="age">
        AGE:
        <span style="color:green;">
            <xsl:value-of select="."/>
        </span>
        <br/>
    </xsl:template>

</xsl:stylesheet>