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
package org.apache.camel.itest.jetty;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JettyBridgeHostHeaderIssueTest extends CamelTestSupport {

    private int port;
    private int port2;
    private int port3;

    @Test
    public void testHostHeader() throws Exception {
        // TODO: the host header is removed in bridgeEndpoint in the http4 producer, that seems wrong
        // as Camel as a reverse-proxy should update the host header accordingly


        Exchange reply = template.request("http4:localhost:" + port + "/myapp", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello World");
            }
        });
        assertNotNull(reply);
        assertEquals("foo", reply.getOut().getBody(String.class));

        Exchange reply2 = template.request("http4:localhost:" + port + "/myapp", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Bye World");
            }
        });
        assertNotNull(reply2);
        assertEquals("bar", reply2.getOut().getBody(String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        port = AvailablePortFinder.getNextAvailable(12000);
        port2 = AvailablePortFinder.getNextAvailable(12100);
        port3 = AvailablePortFinder.getNextAvailable(12200);

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jetty:http://localhost:" + port + "/myapp?matchOnUriPrefix=true")
                    .loadBalance().roundRobin()
                        .to("http4://localhost:" + port2 + "/foo?bridgeEndpoint=true&throwExceptionOnFailure=false")
                        .to("http4://localhost:" + port3 + "/bar?bridgeEndpoint=true&throwExceptionOnFailure=false");

                from("jetty:http://localhost:" + port2 + "/foo").transform().constant("foo");

                from("jetty:http://localhost:" + port3 + "/bar").transform().constant("bar");
            }
        };
    }
}

