<?xml version = "1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:template match="/">
    <bank>
    	<name><xsl:value-of select="/hash/bank-name/text()"/></name>
    	<bic><xsl:value-of select="/hash/swift-bic/text()"/></bic>
    </bank>
  </xsl:template>

</xsl:stylesheet>
