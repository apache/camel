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
package org.apache.camel.component.netty;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * @version $Revision$
 */
public class NettyExchangeTimeoutTest extends CamelTestSupport {

    private static final int PORT = 6336;
    protected String uri = "netty:tcp://localhost:" + PORT;

    @Test
    public void testUsingTimeoutParameter() throws Exception {
        // use a timeout value of 2 seconds (timeout is in millis) so we should actually get a response in this test
        Endpoint endpoint = this.context.getEndpoint("netty:tcp://localhost:" + PORT + "?timeout=2000");

        try {
            template.sendBody(endpoint, "Hello World");
            fail("Should have thrown a exception");
        } catch (Exception e) {
            ExchangeTimedOutException timeout = assertIsInstanceOf(ExchangeTimedOutException.class, e.getCause());
            assertEquals(2000, timeout.getTimeout());
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
