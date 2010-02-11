<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

    <xsl:include href="../staff_other_template.xsl"/>

    <xsl:template match="staff/programmer">
        <html>
            <body>
                <xsl:apply-templates select="age"/>
                <br/>
            </body>
        </html>
    </xsl:template>

</xsl:stylesheet>