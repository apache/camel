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
package org.apache.camel.component.mina2;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * Unit test for wiki documentation
 */
public class Mina2ConsumerTest extends BaseMina2Test {

    int port1;
    int port2;

    @Test
    public void testSendTextlineText() throws Exception {
        // START SNIPPET: e2
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("mina2:tcp://localhost:" + port1 + "?textline=true&sync=false", "Hello World");

        assertMockEndpointsSatisfied();
        // END SNIPPET: e2
    }

    @Test
    public void testSendTextlineSyncText() throws Exception {
        // START SNIPPET: e4
        String response = (String) template.requestBody("mina2:tcp://localhost:" + port2 + "?textline=true&sync=true", "World");
        assertEquals("Bye World", response);
        // END SNIPPET: e4
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            public void configure() throws Exception {
                port1 = getPort();
                port2 = getNextPort();

                // START SNIPPET: e1
                from("mina2:tcp://localhost:" + port1 + "?textline=true&sync=false").to("mock:result");
                // END SNIPPET: e1

                // START SNIPPET: e3
                from("mina2:tcp://localhost:" + port2 + "?textline=true&sync=true").process(new Processor() {

                    public void process(Exchange exchange) throws Exception {
                        String body = exchange.getIn().getBody(String.class);
                        exchange.getOut().setBody("Bye " + body);
                    }
                });
                // END SNIPPET: e3
            }
        };
    }
}
