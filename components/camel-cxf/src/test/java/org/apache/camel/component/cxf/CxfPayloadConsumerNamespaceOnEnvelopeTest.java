/*
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

import org.w3c.dom.Document;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CxfPayloadConsumerNamespaceOnEnvelopeTest extends CamelTestSupport {
    /*
     * The request message is generated directly. The issue here is that the xsi
     * and xs namespaces are defined on the SOAP envelope but are used within
     * the payload. This can cause issues with some type conversions in PAYLOAD
     * mode, as the Camel-CXF endpoint will return some kind of window within
     * the StAX parsing (and the namespace definitions are outside).
     * 
     * If some CXF proxy is used to send the message the namespaces will be
     * defined within the payload (and everything works fine).
     */
    protected static final String RESPONSE_PAYLOAD = 
        "<ns2:getTokenResponse xmlns:ns2=\"http://camel.apache.org/cxf/namespace\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
        + "<return xsi:type=\"xs:string\">Return Value</return></ns2:getTokenResponse>";
    protected static final String REQUEST_MESSAGE =
        "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
        + "<soap:Body><ns2:getToken xmlns:ns2=\"http://camel.apache.org/cxf/namespace\"><arg0 xsi:type=\"xs:string\">Send</arg0></ns2:getToken></soap:Body></soap:Envelope>";

    private AbstractXmlApplicationContext applicationContext;

    // Don't remove this, it initializes the CXFTestSupport class
    static {
        CXFTestSupport.getPort1();
        // Works without streaming...
        // System.setProperty("org.apache.camel.component.cxf.streaming", "false");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return SpringCamelContext.springCamelContext(applicationContext, true);
    }

    @Override
    @Before
    public void setUp() throws Exception {
        applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/GetTokenBeans.xml");
        super.setUp();
        assertNotNull("Should have created a valid spring context", applicationContext);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        IOHelper.close(applicationContext);
        super.tearDown();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:router")
                        // Use message mode to send the raw message
                        .to("cxf:bean:serviceEndpoint?dataFormat=RAW")
                        // Convert to String to make testing the result easier
                        .convertBodyTo(String.class);
                // The consumer is running in payload mode
                from("cxf:bean:serviceEndpoint?dataFormat=PAYLOAD")
                        // Convert the CxfPayload to a String to trigger the issue
                        .convertBodyTo(String.class)
                        // Parse to DOM to make sure it's still valid XML
                        .convertBodyTo(Document.class).setBody().constant(RESPONSE_PAYLOAD);
            }
        };
    }

    @Test
    public void testInvokeRouter() {
        Object returnValue = template.requestBody("direct:router", REQUEST_MESSAGE);
        assertNotNull(returnValue);
        assertTrue(returnValue instanceof String);
        assertTrue(((String) returnValue).contains("Return Value"));
        assertTrue(((String) returnValue).contains("http://www.w3.org/2001/XMLSchema-instance"));
    }
}
