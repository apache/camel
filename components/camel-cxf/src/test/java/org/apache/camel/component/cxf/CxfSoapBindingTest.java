/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.commons.io.IOUtils;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.staxutils.StaxUtils;

public class CxfSoapBindingTest extends TestCase {
    private static final String REQUEST_STRING =
        "<testMethod xmlns=\"http://camel.apache.org/testService\"/>";
    private DefaultCamelContext context = new DefaultCamelContext();


    // setup the default context for testing
    public void testGetCxfInMessage() throws Exception {
        HeaderFilterStrategy headerFilterStrategy = new CxfHeaderFilterStrategy();
        org.apache.camel.Exchange exchange = new DefaultExchange(context);
        // String
        exchange.getIn().setBody("hello world");
        org.apache.cxf.message.Message message = CxfSoapBinding.getCxfInMessage(
                headerFilterStrategy, exchange, false);
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
        message = CxfSoapBinding.getCxfInMessage(headerFilterStrategy, exchange, false);
        is = message.getContent(InputStream.class);
        assertNotNull("The input stream should not be null", is);
        assertEquals("Don't get the right message", toString(is), REQUEST_STRING);

        // File
        exchange.getIn().setBody(requestFile);
        message = CxfSoapBinding.getCxfInMessage(headerFilterStrategy, exchange, false);
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
