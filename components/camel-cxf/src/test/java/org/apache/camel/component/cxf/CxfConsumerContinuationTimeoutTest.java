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

import java.util.concurrent.ExecutorService;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class CxfConsumerContinuationTimeoutTest extends CamelTestSupport {

    private static final String ECHO_METHOD = "ns1:echo xmlns:ns1=\"http://cxf.component.camel.apache.org/\"";

    private static final String ECHO_RESPONSE = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            + "<soap:Body><ns1:echoResponse xmlns:ns1=\"http://cxf.component.camel.apache.org/\">"
            + "<return xmlns=\"http://cxf.component.camel.apache.org/\">echo Hello World!</return>"
            + "</ns1:echoResponse></soap:Body></soap:Envelope>";
    private static final String ECHO_BOOLEAN_RESPONSE = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            + "<soap:Body><ns1:echoBooleanResponse xmlns:ns1=\"http://cxf.component.camel.apache.org/\">"
            + "<return xmlns=\"http://cxf.component.camel.apache.org/\">true</return>"
            + "</ns1:echoBooleanResponse></soap:Body></soap:Envelope>";

    protected final String simpleEndpointAddress = "http://localhost:"
        + CXFTestSupport.getPort1() + "/" + getClass().getSimpleName() + "/test";
    protected final String simpleEndpointURI = "cxf://" + simpleEndpointAddress
        + "?serviceClass=org.apache.camel.component.cxf.HelloService";

    protected ExecutorService pool;
    
    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                pool = context.getExecutorServiceManager().newSingleThreadExecutor(this, "MyPool");

                from("direct:start")
                    .setBody(constant("Sensitive Data"))
                    .to(simpleEndpointURI + "&continuationTimeout=5000&dataFormat=RAW");

                from(simpleEndpointURI + "&continuationTimeout=5000&dataFormat=RAW").process(new AsyncProcessorSupport() {
                    @Override
                    public boolean process(Exchange exchange, AsyncCallback asyncCallback) {
                        Message in = exchange.getIn();
                        // check the content-length header is filtered
                        Object value = in.getHeader("Content-Length");
                        assertNull("The Content-Length header should be removed", value);
                        // Get the request message
                        String request = in.getBody(String.class);
                        String priority = in.getHeader("priority", "fast", String.class);

                        // need not to block this thread to simulate slow response so use a thread pool to wait
                        if ("slow".equalsIgnoreCase(priority)) {
                            pool.submit(() -> {
                                try {
                                    log.info("Sleeping for 10 seconds to simulate slow response");
                                    Thread.sleep(10000);
                                } catch (InterruptedException e) {
                                    // ignore
                                } finally {
                                    asyncCallback.done(false);
                                }
                            });
                            return false;
                        } else {
                            // Send the response message back
                            if (request.indexOf(ECHO_METHOD) > 0) {
                                exchange.getOut().setBody(ECHO_RESPONSE);
                            } else { // echoBoolean call
                                exchange.getOut().setBody(ECHO_BOOLEAN_RESPONSE);
                            }
                        }
                        asyncCallback.done(true);
                        return true;
                    }
                });
            }
        };
    }

    @Test
    public void testNoTimeout() throws Exception {
        Object out = template.requestBody("direct:start", "Hello World", String.class);
        assertEquals(ECHO_BOOLEAN_RESPONSE, out);
    }

    @Test
    public void testTimeout() throws Exception {
        String out = template.requestBodyAndHeader("direct:start", "Bye World", "priority", "slow", String.class);
        assertTrue(out.contains("The OUT message was not received within: 5000 millis."));
    }

}
