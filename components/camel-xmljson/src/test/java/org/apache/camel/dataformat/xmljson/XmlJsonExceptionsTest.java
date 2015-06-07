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
package org.apache.camel.dataformat.xmljson;

import java.util.List;

import net.sf.json.JSONException;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * Tests for exception cases in the XML JSON data format
 */
public class XmlJsonExceptionsTest extends AbstractJsonTestSupport {

    @Test
    public void testMalformedXML() throws Exception {
        String in = "<noRoot>abc</noRoot><noRoot>abc</noRoot>";

        MockEndpoint mockJSON = getMockEndpoint("mock:json");
        mockJSON.expectedMessageCount(0);

        MockEndpoint mockException = getMockEndpoint("mock:exception");
        mockException.expectedMessageCount(1);

        try {
            template.requestBody("direct:marshal", in);
            fail("Exception expected");
        } catch (CamelExecutionException e) {
            assertEquals("JSONException expected", JSONException.class, e.getCause().getClass());
        }

        List<Exchange> exchs = mockException.getExchanges();
        assertEquals("Only one exchange was expected in mock:exception", 1, exchs.size());

        Exception e = (Exception) exchs.get(0).getProperty(Exchange.EXCEPTION_CAUGHT);
        assertNotNull("Exception expected", e);
        assertEquals("JSONException expected", JSONException.class, e.getClass());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testMalformedJson() throws Exception {
        String in = "{ \"a\": 123, \"b\": true, \"c\": true2 }";

        MockEndpoint mockXML = getMockEndpoint("mock:xml");
        mockXML.expectedMessageCount(0);

        MockEndpoint mockException = getMockEndpoint("mock:exception");
        mockException.expectedMessageCount(1);

        try {
            template.requestBody("direct:unmarshal", in);
            fail("Exception expected");
        } catch (CamelExecutionException e) {
            assertEquals("JSONException expected", JSONException.class, e.getCause().getClass());
        }

        List<Exchange> exchs = mockException.getExchanges();
        assertEquals("Only one exchange was expected in mock:exception", 1, exchs.size());

        Exception e = (Exception) exchs.get(0).getProperty(Exchange.EXCEPTION_CAUGHT);
        assertNotNull("Exception expected", e);
        assertEquals("JSONException expected", JSONException.class, e.getClass());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendJsonToXML() throws Exception {
        String in = "{ \"a\": 123, \"b\": true, \"c\": true2 }";

        MockEndpoint mockJSON = getMockEndpoint("mock:xml");
        mockJSON.expectedMessageCount(0);

        MockEndpoint mockException = getMockEndpoint("mock:exception");
        mockException.expectedMessageCount(1);

        try {
            template.requestBody("direct:unmarshal", in);
            fail("Exception expected");
        } catch (CamelExecutionException e) {
            assertEquals("JSONException expected", JSONException.class, e.getCause().getClass());
        }

        List<Exchange> exchs = mockException.getExchanges();
        assertEquals("Only one exchange was expected in mock:exception", 1, exchs.size());

        Exception e = (Exception) exchs.get(0).getProperty(Exchange.EXCEPTION_CAUGHT);
        assertNotNull("Exception expected", e);
        assertEquals("JSONException expected", JSONException.class, e.getClass());

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                XmlJsonDataFormat format = new XmlJsonDataFormat();

                onException(Exception.class).handled(false).to("mock:exception");

                // from XML to JSON
                from("direct:marshal").marshal(format).to("mock:json");
                // from JSON to XML
                from("direct:unmarshal").unmarshal(format).to("mock:xml");

            }
        };
    }

}
