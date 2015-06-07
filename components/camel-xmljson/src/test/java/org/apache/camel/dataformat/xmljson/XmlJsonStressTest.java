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

import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * Stress tests for the XML JSON data format: concurrency and large JSON and XML documents
 */
public class XmlJsonStressTest extends AbstractJsonTestSupport {

    @Test
    public void testNoConcurrentProducers() throws Exception {
        doSendMessages(1, 1, "direct:startFromXML", "org/apache/camel/dataformat/xmljson/testMessage1.xml");
    }

    @Test
    public void testConcurrentProducers() throws Exception {
        doSendMessages(1000, 5, "direct:startFromXML", "org/apache/camel/dataformat/xmljson/testMessage1.xml");
    }

    @Test
    public void testLargeMessages() throws Exception {
        doSendMessages(5, 2, "direct:startFromXML", "org/apache/camel/dataformat/xmljson/testMessage3-large.xml");
        resetMocks();
        doSendMessages(5, 2, "direct:startFromJson", "org/apache/camel/dataformat/xmljson/testMessage3-large.json");
    }

    private void doSendMessages(int files, int poolSize, final String endpointURI, String resourceURI) throws Exception {
        MockEndpoint mockJSON = getMockEndpoint("mock:json");
        MockEndpoint mockXML = getMockEndpoint("mock:xml");
        mockJSON.expectedMessageCount(files);
        mockXML.expectedMessageCount(files);

        InputStream inStream = getClass().getClassLoader().getResourceAsStream(resourceURI);
        final String in = context.getTypeConverter().convertTo(String.class, inStream);

        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        for (int i = 0; i < files; i++) {
            executor.submit(new Callable<Object>() {
                public Object call() throws Exception {
                    template.sendBody(endpointURI, in);
                    return null;
                }
            });
        }

        // give much time as slow servers may take long time
        assertMockEndpointsSatisfied(60, TimeUnit.SECONDS);

        // test that all messages are equal
        Object jsonBody = mockJSON.getExchanges().get(0).getIn().getBody(String.class);
        Object xmlBody = mockXML.getExchanges().get(0).getIn().getBody(String.class);

        for (Exchange e : mockJSON.getExchanges()) {
            assertEquals("Bodies are expected to be equal (json mock endpoint)", jsonBody, e.getIn().getBody(String.class));
        }

        for (Exchange e : mockXML.getExchanges()) {
            assertEquals("Bodies are expected to be equal (xml mock endpoint)", xmlBody, e.getIn().getBody(String.class));
        }

        mockJSON.reset();
        mockXML.reset();

        executor.shutdownNow();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                XmlJsonDataFormat format = new XmlJsonDataFormat();

                // from XML to JSON
                from("direct:startFromXML").marshal(format).to("mock:json").to("direct:unmarshalToJson");
                // from JSON to XML
                from("direct:unmarshalToJson").unmarshal(format).to("mock:xml");

                // from JSON to XML
                from("direct:startFromJson").unmarshal(format).to("mock:json").to("direct:marshalToXML");
                // from XML to JSON
                from("direct:marshalToXML").marshal(format).to("mock:xml");
            }
        };
    }

}
