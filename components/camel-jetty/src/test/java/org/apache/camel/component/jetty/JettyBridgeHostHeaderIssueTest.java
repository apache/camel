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
package org.apache.camel.component.jetty;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JettyBridgeHostHeaderIssueTest extends CamelTestSupport {

    @RegisterExtension
    AvailablePortFinder.Port port = AvailablePortFinder.find();
    @RegisterExtension
    AvailablePortFinder.Port port2 = AvailablePortFinder.find();
    @RegisterExtension
    AvailablePortFinder.Port port3 = AvailablePortFinder.find();
    @RegisterExtension
    AvailablePortFinder.Port port4 = AvailablePortFinder.find();
    @RegisterExtension
    AvailablePortFinder.Port port5 = AvailablePortFinder.find();
    private String receivedHostHeaderEndpoint1;
    private String receivedHostHeaderEndpoint2;
    private String receivedHostHeaderEndpoint3;
    private String receivedHostHeaderEndpoint4;

    @Test
    void testHostHeader() {

        //The first two calls will test http producers

        //The first call to our service will hit the first destination in the round robin load balancer
        //this destination has the preserveProxyHeader parameter set to true, so we verify the Host header
        //received by our downstream instance matches the address and port of the proxied service
        Exchange reply = template.request("http:localhost:" + port.getPort() + "/myapp",
                exchange -> exchange.getIn().setBody("Hello World"));
        assertNotNull(reply);
        assertEquals("foo", reply.getMessage().getBody(String.class));
        //assert the received Host header is localhost:port (where port matches the /myapp port)
        assertEquals("localhost:" + port.getPort(), receivedHostHeaderEndpoint1);

        //The second call to our service will hit the second destination in the round robin load balancer
        //this destination does not have the preserveProxyHeader, so we expect the Host header received by the destination
        //to match the url of the destination service itself
        Exchange reply2 = template.request("http:localhost:" + port.getPort() + "/myapp",
                exchange -> exchange.getIn().setBody("Bye World"));
        assertNotNull(reply2);
        assertEquals("bar", reply2.getMessage().getBody(String.class));
        //assert the received Host header is localhost:port3 (where port3 matches the /bar destination server)
        assertEquals("localhost:" + port3.getPort(), receivedHostHeaderEndpoint2);

        //The next two calls will use/test the jetty producers in the round robin load balancer

        //The first has the preserveHostHeader option set to true, so we would expect to receive a Host header matching the /myapp proxied service
        Exchange reply3 = template.request("http:localhost:" + port.getPort() + "/myapp",
                exchange -> exchange.getIn().setBody("Bye JWorld"));
        assertNotNull(reply3);
        assertEquals("jbar", reply3.getMessage().getBody(String.class));
        //assert the received Host header is localhost:port (where port matches the /myapp destination server)
        assertEquals("localhost:" + port.getPort(), receivedHostHeaderEndpoint3);

        //The second does not have a preserveHostHeader (preserveHostHeader=false), we would expect to see a Host header matching the destination service
        Exchange reply4 = template.request("http:localhost:" + port.getPort() + "/myapp",
                exchange -> exchange.getIn().setBody("JAVA!!!!"));
        assertNotNull(reply4);
        assertEquals("java???", reply4.getMessage().getBody(String.class));
        //assert the received Host header is localhost:port5 (where port3 matches the /jbarf destination server)
        assertEquals("localhost:" + port5.getPort(), receivedHostHeaderEndpoint4);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jetty:http://localhost:" + port.getPort() + "/myapp?matchOnUriPrefix=true")
                        .loadBalance().roundRobin()
                        .to("http://localhost:" + port2.getPort()
                            + "/foo?bridgeEndpoint=true&throwExceptionOnFailure=false&preserveHostHeader=true")
                        .to("http://localhost:" + port3.getPort()
                            + "/bar?bridgeEndpoint=true&throwExceptionOnFailure=false")
                        .to("http://localhost:" + port4.getPort()
                            + "/jbar?bridgeEndpoint=true&throwExceptionOnFailure=false&preserveHostHeader=true")
                        .to("http://localhost:" + port5.getPort()
                            + "/jbarf?bridgeEndpoint=true&throwExceptionOnFailure=false");

                from("jetty:http://localhost:" + port2.getPort() + "/foo")
                        .process(exchange -> receivedHostHeaderEndpoint1 = exchange.getIn().getHeader("Host", String.class))
                        .transform().constant("foo");

                from("jetty:http://localhost:" + port3.getPort() + "/bar")
                        .process(exchange -> receivedHostHeaderEndpoint2 = exchange.getIn().getHeader("Host", String.class))
                        .transform().constant("bar");

                from("jetty:http://localhost:" + port4.getPort() + "/jbar")
                        .process(exchange -> receivedHostHeaderEndpoint3 = exchange.getIn().getHeader("Host", String.class))
                        .transform().constant("jbar");

                from("jetty:http://localhost:" + port5.getPort() + "/jbarf")
                        .process(exchange -> receivedHostHeaderEndpoint4 = exchange.getIn().getHeader("Host", String.class))
                        .transform().constant("java???");
            }
        };
    }
}
