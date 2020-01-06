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
package org.apache.camel.component.netty;

import io.netty.handler.timeout.ReadTimeoutException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class NettyRequestTimeoutTest extends BaseNettyTest {

    @Test
    public void testRequestTimeoutOK() throws Exception {
        String out = template.requestBody("netty:tcp://localhost:{{port}}?textline=true&sync=true&requestTimeout=500", "Hello Camel", String.class);
        assertEquals("Bye World", out);
    }

    @Test
    public void testRequestTimeout() throws Exception {
        try {
            template.requestBody("netty:tcp://localhost:{{port}}?textline=true&sync=true&requestTimeout=100", "Hello Camel", String.class);
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            ReadTimeoutException cause = assertIsInstanceOf(ReadTimeoutException.class, e.getCause());
            assertNotNull(cause);
        }
    }

    @Test
    public void testKeepingTimeoutHeader() throws Exception {
        Endpoint endpoint = this.resolveMandatoryEndpoint("netty:tcp://localhost:{{port}}?textline=true&sync=true&requestTimeout=100");
        try {
            String out = template.requestBody(endpoint, "Hello", String.class);
            assertEquals("Bye World", out);
            template.requestBody(endpoint, "Hello Camel", String.class);
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            ReadTimeoutException cause = assertIsInstanceOf(ReadTimeoutException.class, e.getCause());
            assertNotNull(cause);
        }
    }


    @Test
    public void testRequestTimeoutViaHeader() throws Exception {
        try {
            template.requestBodyAndHeader("netty:tcp://localhost:{{port}}?textline=true&sync=true", "Hello Camel", NettyConstants.NETTY_REQUEST_TIMEOUT, 100, String.class);
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            ReadTimeoutException cause = assertIsInstanceOf(ReadTimeoutException.class, e.getCause());
            assertNotNull(cause);
        }
    }

    @Test
    public void testRequestTimeoutAndOk() throws Exception {
        try {
            template.requestBody("netty:tcp://localhost:{{port}}?textline=true&sync=true&requestTimeout=100", "Hello Camel", String.class);
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            ReadTimeoutException cause = assertIsInstanceOf(ReadTimeoutException.class, e.getCause());
            assertNotNull(cause);
        }

        // now we try again but this time the is no delay on server and thus faster
        String out = template.requestBody("netty:tcp://localhost:{{port}}?textline=true&sync=true&requestTimeout=100", "Hello World", String.class);
        assertEquals("Bye World", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty:tcp://localhost:{{port}}?textline=true&sync=true")
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            String body = exchange.getIn().getBody(String.class);

                            if (body.contains("Camel")) {
                                Thread.sleep(200);
                            }
                        }
                    })
                    .transform().constant("Bye World");

            }
        };
    }
}
