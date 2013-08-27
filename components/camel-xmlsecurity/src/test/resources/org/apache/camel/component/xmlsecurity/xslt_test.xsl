<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:template match="/">
		<n0:XMLSecurity xmlns:n0="https://org.apache/camel/xmlsecurity/test"
			xmlns:nn0="http://www.w3.org/2000/09/xmldsig#" xmlns:n1="http://test/test">
			<n0:Content>
				<!-- must start with the Object element! -->
				<xsl:value-of select="//n1:root/n1:test" />
			</n0:Content>
		</n0:XMLSecurity>
	</xsl:template>
</xsl:stylesheet>
