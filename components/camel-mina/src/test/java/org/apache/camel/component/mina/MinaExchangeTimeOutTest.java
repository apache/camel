/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.apache.camel.component.mina;

import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;

/**
 * To test timeout.
 *
 * @version $Revision$
 */
public class MinaExchangeTimeOutTest extends ContextTestSupport {

    private static final int PORT = 6335;
    protected String uri = "mina:tcp://localhost:" + PORT + "?textline=true&sync=true";

    public void testDefaultTimeOut() {
        try {
            String result = (String)template.requestBody(uri, "Hello World");
            assertEquals("Okay I will be faster in the future", result);
        } catch (RuntimeCamelException e) {
            fail("Should not get a RuntimeCamelException");
        }
    }

    public void testUsingTimeoutParameter() throws Exception {

        // use a timeout value of 2 seconds (timeout is in millis) so we should actually get a response in this test
        Endpoint endpoint = this.context.getEndpoint("mina:tcp://localhost:" + PORT + "?textline=true&sync=true&timeout=2000");
        Producer producer = endpoint.createProducer();
        producer.start();
        Exchange exchange = producer.createExchange();
        exchange.getIn().setBody("Hello World");
        try {
            producer.process(exchange);
            fail("Should have thrown an ExchangeTimedOutException wrapped in a RuntimeCamelException");
        } catch (Exception e) {
            assertTrue("Should have thrown an ExchangeTimedOutException", e instanceof ExchangeTimedOutException);
        }
        producer.stop();
    }

    public void testTimeoutInvalidParameter() throws Exception {
        // invalid timeout parameter that can not be converted to a number
        try {
            this.context.getEndpoint("mina:tcp://localhost:" + PORT + "?textline=true&sync=true&timeout=XXX");
            fail("Should have thrown a ResolveEndpointFailedException due to invalid timeout parameter");
        } catch (ResolveEndpointFailedException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
            assertEquals("The timeout parameter is not a number: XXX", e.getCause().getMessage());
        }
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(uri).process(new Processor() {
                    public void process(Exchange e) throws Exception {
                        assertEquals("Hello World", e.getIn().getBody(String.class));
                        // MinaProducer has a default timeout of 30 seconds so we just wait 5 seconds
                        // (template.requestBody is a MinaProducer behind the doors)
                        Thread.sleep(5000);

                        e.getOut().setBody("Okay I will be faster in the future");
                    }
                });
            }
        };
    }

}
