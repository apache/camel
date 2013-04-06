<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:m0="http://services.samples/xsd">
    
    <xsl:output method="xml" omit-xml-declaration="yes" indent="yes" exclude-result-prefixes="m0" />

    <xsl:template match="m0:skcotSyub">
        <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
            <soapenv:Body>
                <m:buyStocks xmlns:m="http://services.samples/xsd">
                    <xsl:for-each select="redro">
                        <order>
                            <symbol>
                                <xsl:value-of select="lobmys" />
                            </symbol>
                            <buyerID>
                                <xsl:value-of select="DIreyub" />
                            </buyerID>
                            <price>
                                <xsl:value-of select="ecirp" />
                            </price>
                            <volume>
                                <xsl:value-of select="emulov" />
                            </volume>
                        </order>
                    </xsl:for-each>
                </m:buyStocks>
            </soapenv:Body>
        </soapenv:Envelope>
    </xsl:template>
</xsl:stylesheet>