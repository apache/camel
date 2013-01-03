<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:m0="http://services.samples/xsd"
    xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
    
    <xsl:output method="xml" omit-xml-declaration="yes" indent="yes" exclude-result-prefixes="m0" />

    <xsl:template match="m0:buyStocks">
        <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
            <soapenv:Body>
                <m:skcotSyub xmlns:m="http://services.samples/xsd">
                    <xsl:for-each select="order">
                        <redro>
                            <lobmys>
                                <xsl:value-of select="symbol" />
                            </lobmys>
                            <DIreyub>
                                <xsl:value-of select="buyerID" />
                            </DIreyub>
                            <ecirp>
                                <xsl:value-of select="price" />
                            </ecirp>
                            <emulov>
                                <xsl:value-of select="volume" />
                            </emulov>
                        </redro>
                    </xsl:for-each>
                </m:skcotSyub>
            </soapenv:Body>
        </soapenv:Envelope>
    </xsl:template>

    <xsl:template match="soapenv:Header"></xsl:template>
</xsl:stylesheet>