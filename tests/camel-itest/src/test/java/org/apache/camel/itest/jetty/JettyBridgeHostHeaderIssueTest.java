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
    private int port4;
    private int port5;
    private String receivedHostHeaderEndpoint1;
    private String receivedHostHeaderEndpoint2;
    private String receivedHostHeaderEndpoint3;
    private String receivedHostHeaderEndpoint4;

    @Test
    public void testHostHeader() throws Exception {

        //The first two calls will test http4 producers

        //The first call to our service will hit the first destination in the round robin load balancer
        //this destination has the preserveProxyHeader parameter set to true, so we verify the Host header
        //received by our downstream instance matches the address and port of the proxied service
        Exchange reply = template.request("http4:localhost:" + port + "/myapp", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello World");
            }
        });
        assertNotNull(reply);
        assertEquals("foo", reply.getOut().getBody(String.class));
        //assert the received Host header is localhost:port (where port matches the /myapp port)
        assertEquals("localhost:" + port, receivedHostHeaderEndpoint1);

        //The second call to our service will hit the second destination in the round robin load balancer
        //this destination does not have the preserveProxyHeader, so we expect the Host header received by the destination
        //to match the url of the destination service itself
        Exchange reply2 = template.request("http4:localhost:" + port + "/myapp", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Bye World");
            }
        });
        assertNotNull(reply2);
        assertEquals("bar", reply2.getOut().getBody(String.class));
        //assert the received Host header is localhost:port3 (where port3 matches the /bar destination server)
        assertEquals("localhost:" + port3, receivedHostHeaderEndpoint2);


        //The next two calls will use/test the jetty producers in the round robin load balancer

        //The first has the preserveHostHeader option set to true, so we would expect to receive a Host header matching the /myapp proxied service
        Exchange reply3 = template.request("http4:localhost:" + port + "/myapp", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Bye JWorld");
            }
        });
        assertNotNull(reply3);
        assertEquals("jbar", reply3.getOut().getBody(String.class));
        //assert the received Host header is localhost:port (where port matches the /myapp destination server)
        assertEquals("localhost:" + port, receivedHostHeaderEndpoint3);

        //The second does not have a preserveHostHeader (preserveHostHeader=false), we would expect to see a Host header matching the destination service
        Exchange reply4 = template.request("http4:localhost:" + port + "/myapp", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("JAVA!!!!");
            }
        });
        assertNotNull(reply4);
        assertEquals("java???", reply4.getOut().getBody(String.class));
        //assert the received Host header is localhost:port5 (where port3 matches the /jbarf destination server)
        assertEquals("localhost:" + port5, receivedHostHeaderEndpoint4);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        port = AvailablePortFinder.getNextAvailable(12000);
        port2 = AvailablePortFinder.getNextAvailable(12100);
        port3 = AvailablePortFinder.getNextAvailable(12200);
        port4 = AvailablePortFinder.getNextAvailable(12300);
        port5 = AvailablePortFinder.getNextAvailable(12400);

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jetty:http://localhost:" + port + "/myapp?matchOnUriPrefix=true")
                    .loadBalance().roundRobin()
                        .to("http4://localhost:" + port2 + "/foo?bridgeEndpoint=true&throwExceptionOnFailure=false&preserveHostHeader=true")
                        .to("http4://localhost:" + port3 + "/bar?bridgeEndpoint=true&throwExceptionOnFailure=false")
                        .to("jetty:http://localhost:" + port4 + "/jbar?bridgeEndpoint=true&throwExceptionOnFailure=false&preserveHostHeader=true")
                        .to("jetty:http://localhost:" + port5 + "/jbarf?bridgeEndpoint=true&throwExceptionOnFailure=false");

                from("jetty:http://localhost:" + port2 + "/foo")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                receivedHostHeaderEndpoint1 = exchange.getIn().getHeader("Host", String.class);
                            }
                        })
                        .transform().constant("foo");

                from("jetty:http://localhost:" + port3 + "/bar")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                receivedHostHeaderEndpoint2 = exchange.getIn().getHeader("Host", String.class);
                            }
                        })
                        .transform().constant("bar");

                from("jetty:http://localhost:" + port4 + "/jbar")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                receivedHostHeaderEndpoint3 = exchange.getIn().getHeader("Host", String.class);
                            }
                        })
                        .transform().constant("jbar");

                from("jetty:http://localhost:" + port5 + "/jbarf")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                receivedHostHeaderEndpoint4 = exchange.getIn().getHeader("Host", String.class);
                            }
                        })
                        .transform().constant("java???");
            }
        };
    }
}

