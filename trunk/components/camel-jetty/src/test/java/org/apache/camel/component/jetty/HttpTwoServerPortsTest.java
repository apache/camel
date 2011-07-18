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
package org.apache.camel.component.jetty;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * @version 
 */
public class HttpTwoServerPortsTest extends BaseJettyTest {

    private int port1;
    private int port2;

    @Test
    public void testTwoServerPorts() throws Exception {
        String reply = template.requestBody("direct:a", "World", String.class);
        assertEquals("Bye World", reply);

        reply = template.requestBody("direct:b", "Camel", String.class);
        assertEquals("Hi Camel", reply);

        reply = template.requestBody("direct:a", "Earth", String.class);
        assertEquals("Bye Earth", reply);

        reply = template.requestBody("direct:b", "Moon", String.class);
        assertEquals("Hi Moon", reply);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                port1 = getPort();
                port2 = getNextPort();

                from("direct:a").to("http://localhost:" + port1 + "/myapp");

                from("direct:b").to("http://localhost:" + port2 + "/myotherapp");

                from("jetty://http://localhost:" + port1 + "/myapp").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String in = exchange.getIn().getBody(String.class);
                        exchange.getOut().setBody("Bye " + in);
                    }
                });

                from("jetty://http://localhost:" + port2 + "/myotherapp").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String in = exchange.getIn().getBody(String.class);
                        exchange.getOut().setBody("Hi " + in);
                    }
                });
            }
        };
    }
}