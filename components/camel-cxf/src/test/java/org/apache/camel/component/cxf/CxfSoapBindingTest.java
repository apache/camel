package org.apache.camel.component.cxf;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.dom.DOMSource;

import junit.framework.TestCase;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.commons.io.IOUtils;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.staxutils.StaxUtils;

public class CxfSoapBindingTest extends TestCase {
    private DefaultCamelContext context = new DefaultCamelContext();
    private static final String REQUEST_STRING =
        "<testMethod xmlns=\"http://camel.apache.org/testService\"/>";

    // setup the default context for testing
    public void testGetCxfInMessage() throws Exception {
        org.apache.camel.Exchange exchange = new DefaultExchange(context);
        // String
        exchange.getIn().setBody("hello world");
        org.apache.cxf.message.Message message = CxfSoapBinding.getCxfInMessage(exchange, false);
        // test message
        InputStream is = message.getContent(InputStream.class);
        assertNotNull("The input stream should not be null", is);
        assertEquals("Don't get the right message", toString(is), "hello world");

        // DOMSource
        URL request = this.getClass().getResource("RequestBody.xml");
        File requestFile = new File(request.toURI());
        FileInputStream inputStream = new FileInputStream(requestFile);
        XMLStreamReader xmlReader = StaxUtils.createXMLStreamReader(inputStream);
        DOMSource source = new DOMSource(StaxUtils.read(xmlReader));
        exchange.getIn().setBody(source);
        message = CxfSoapBinding.getCxfInMessage(exchange, false);
        is = message.getContent(InputStream.class);
        assertNotNull("The input stream should not be null", is);
        assertEquals("Don't get the right message", toString(is), REQUEST_STRING);

        // File
        exchange.getIn().setBody(requestFile);
        message = CxfSoapBinding.getCxfInMessage(exchange, false);
        is = message.getContent(InputStream.class);
        assertNotNull("The input stream should not be null", is);
        assertEquals("Don't get the right message", toString(is), REQUEST_STRING);

    }

    private String toString(InputStream is) throws IOException {
        StringBuilder out = new StringBuilder();
        CachedOutputStream os = new CachedOutputStream();
        IOUtils.copy(is, os);
        is.close();
        os.writeCacheTo(out);
        return out.toString();

    }

}
