<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" indent="yes"/>

  <!-- The supplier's order becomes the packing slip the warehouse prints: one item per line, and
       the number of pieces to pick in total. -->
  <xsl:template match="/order">
    <packingSlip order="{@id}" customer="{customer}" country="{@country}">
      <xsl:for-each select="line">
        <item sku="{@sku}" pieces="{@qty}"/>
      </xsl:for-each>
      <pieces><xsl:value-of select="sum(line/@qty)"/></pieces>
    </packingSlip>
  </xsl:template>
</xsl:stylesheet>
