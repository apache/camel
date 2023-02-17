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
package org.apache.camel.component.cxf.jaxws;

import org.w3c.dom.Node;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

//Modified from https://issues.apache.org/jira/secure/attachment/12730161/0001-CAMEL-8419-Camel-StreamCache-does-not-work-with-CXF-.patch
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CxfConsumerStreamCacheTest extends CamelTestSupport {

    protected static final String REQUEST_MESSAGE
            = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:ser=\"test/service\">"
              + "<soapenv:Header/><soapenv:Body><ser:ping/></soapenv:Body></soapenv:Envelope>";

    protected static final String RESPONSE_MESSAGE_BEGINE
            = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
              + "<soap:Body><pong xmlns=\"test/service\"";
    protected static final String RESPONSE_MESSAGE_END = "/></soap:Body></soap:Envelope>";

    protected static final String RESPONSE = "<pong xmlns=\"test/service\"/>";

    protected final String simpleEndpointAddress = "http://localhost:"
                                                   + CXFTestSupport.getPort1() + "/" + getClass().getSimpleName() + "/test";
    protected final String simpleEndpointURI = "cxf://" + simpleEndpointAddress
                                               + "?synchronous=" + isSynchronous()
                                               + "&serviceClass=org.apache.camel.component.cxf.jaxws.ServiceProvider&dataFormat=PAYLOAD";

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                getContext().setStreamCaching(true);
                getContext().getStreamCachingStrategy().setSpoolThreshold(1L);
                errorHandler(noErrorHandler());
                from(getFromEndpointUri()).process(new Processor() {
                    public void process(final Exchange exchange) throws Exception {
                        Message in = exchange.getIn();
                        Node node = in.getBody(Node.class);
                        assertNotNull(node);
                        CachedOutputStream cos = new CachedOutputStream(exchange);
                        cos.write(RESPONSE.getBytes("UTF-8"));
                        cos.close();
                        exchange.getMessage().setBody(cos.newStreamCache());

                        exchange.getExchangeExtension().addOnCompletion(new Synchronization() {
                            @Override
                            public void onComplete(Exchange exchange) {
                                template.sendBody("mock:onComplete", "");
                            }

                            @Override
                            public void onFailure(Exchange exchange) {

                            }
                        });
                    }
                });
            }
        };
    }

    @Test
    public void testInvokingServiceFromHttpCompnent() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:onComplete");
        mock.expectedMessageCount(2);

        // call the service with right post message

        String response = template.requestBody(simpleEndpointAddress, REQUEST_MESSAGE, String.class);
        assertTrue(response.startsWith(RESPONSE_MESSAGE_BEGINE), "Get a wrong response");
        assertTrue(response.endsWith(RESPONSE_MESSAGE_END), "Get a wrong response");
        try {
            template.requestBody(simpleEndpointAddress, null, String.class);
            fail("Excpetion to get exception here");
        } catch (Exception ex) {
            // do nothing here
        }

        response = template.requestBody(simpleEndpointAddress, REQUEST_MESSAGE, String.class);
        assertTrue(response.startsWith(RESPONSE_MESSAGE_BEGINE), "Get a wrong response");
        assertTrue(response.endsWith(RESPONSE_MESSAGE_END), "Get a wrong response");
        mock.assertIsSatisfied();
    }

    protected String getFromEndpointUri() {
        return simpleEndpointURI;
    }

    protected boolean isSynchronous() {
        return false;
    }
}
