package org.apache.camel.builder.xml;

import junit.framework.TestCase;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;

import javax.xml.transform.Source;

public class XsltUriResolverTest extends TestCase {

    public void testResolveUri() throws Exception {
        CamelContext context = new DefaultCamelContext();
        XsltUriResolver xsltUriResolver = new XsltUriResolver(context, "classpath:xslt/staff/staff.xsl");
        Source source = xsltUriResolver.resolve("../../xslt/common/staff_template.xsl", "classpath:xslt/staff/staff.xsl");
        assertNotNull(source);
        assertEquals("classpath:xslt/common/staff_template.xsl", source.getSystemId());
    }
}
