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
package org.apache.camel.coap;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.elements.tcp.TcpClientConnector;
import org.junit.Test;

public class CoAPRestComponentTCPTest extends CamelTestSupport {
    static int coapport = AvailablePortFinder.getNextAvailable();

    @Test
    public void testCoAP() throws Exception {
        NetworkConfig.createStandardWithoutFile();
        CoapClient client;
        CoapResponse rsp;

        client = new CoapClient("coap+tcp://localhost:" + coapport + "/TestResource/Ducky");
        decorateWithTCP(client);
        rsp = client.get();
        assertEquals(ResponseCode.CONTENT, rsp.getCode());
        assertEquals("Hello Ducky", rsp.getResponseText());
        rsp = client.post("data", MediaTypeRegistry.TEXT_PLAIN);
        assertEquals(ResponseCode.CONTENT, rsp.getCode());
        assertEquals("Hello Ducky: data", rsp.getResponseText());

        client = new CoapClient("coap+tcp://localhost:" + coapport + "/TestParams?id=Ducky");
        decorateWithTCP(client);
        client.setTimeout(1000000L);
        rsp = client.get();
        assertEquals(ResponseCode.CONTENT, rsp.getCode());
        assertEquals("Hello Ducky", rsp.getResponseText());
        rsp = client.post("data", MediaTypeRegistry.TEXT_PLAIN);
        assertEquals(ResponseCode.CONTENT, rsp.getCode());
        assertEquals("Hello Ducky: data", rsp.getResponseText());
        assertEquals(MediaTypeRegistry.TEXT_PLAIN, rsp.getOptions().getContentFormat());
    }

    @Test
    public void testCoAPMethodNotAllowedResponse() throws Exception {
        NetworkConfig.createStandardWithoutFile();
        CoapClient client = new CoapClient("coap+tcp://localhost:" + coapport + "/TestResource/Ducky");
        decorateWithTCP(client);
        client.setTimeout(1000000L);
        CoapResponse rsp = client.delete();
        assertEquals(ResponseCode.METHOD_NOT_ALLOWED, rsp.getCode());
    }

    @Test
    public void testCoAPNotFoundResponse() throws Exception {
        NetworkConfig.createStandardWithoutFile();
        CoapClient client = new CoapClient("coap+tcp://localhost:" + coapport + "/foo/bar/cheese");
        decorateWithTCP(client);
        client.setTimeout(1000000L);
        CoapResponse rsp = client.get();
        assertEquals(ResponseCode.NOT_FOUND, rsp.getCode());
    }
    
    private void decorateWithTCP(CoapClient client) {
        NetworkConfig config = NetworkConfig.createStandardWithoutFile();
        int tcpThreads = config.getInt(NetworkConfig.Keys.TCP_WORKER_THREADS);
        int tcpConnectTimeout = config.getInt(NetworkConfig.Keys.TCP_CONNECT_TIMEOUT);
        int tcpIdleTimeout = config.getInt(NetworkConfig.Keys.TCP_CONNECTION_IDLE_TIMEOUT);
        TcpClientConnector tcpConnector = new TcpClientConnector(tcpThreads, tcpConnectTimeout, tcpIdleTimeout);
        CoapEndpoint.Builder tcpBuilder = new CoapEndpoint.Builder();
        tcpBuilder.setConnector(tcpConnector);

        client.setEndpoint(tcpBuilder.build());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration("coap").scheme("coap+tcp").host("localhost").port(coapport);

                rest("/TestParams")
                    .get().to("direct:get1")
                    .post().to("direct:post1");

                rest("/TestResource")
                    .get("/{id}").to("direct:get1")
                    .post("/{id}").to("direct:post1");
                
                from("direct:get1").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String id = exchange.getIn().getHeader("id", String.class);
                        exchange.getOut().setBody("Hello " + id);
                    }
                });

                from("direct:post1").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String id = exchange.getIn().getHeader("id", String.class);
                        String ct = exchange.getIn().getHeader(Exchange.CONTENT_TYPE, String.class);
                        if (!"text/plain".equals(ct)) {
                            throw new Exception("No content type");
                        }
                        exchange.getOut().setBody("Hello " + id + ": " + exchange.getIn().getBody(String.class));
                        exchange.getOut().setHeader(Exchange.CONTENT_TYPE, ct);
                    }
                });
            }
        };
    }
}
