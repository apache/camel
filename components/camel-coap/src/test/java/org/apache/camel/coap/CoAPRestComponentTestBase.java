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

import java.io.IOException;
import java.security.GeneralSecurityException;

import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.rest.RestConfigurationDefinition;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.elements.config.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

abstract class CoAPRestComponentTestBase extends CamelTestSupport {
    static int coapport = AvailablePortFinder.getNextAvailable();

    @Produce("direct:start")
    protected ProducerTemplate sender;

    @Test
    void testCoAP() throws Exception {
        Configuration.createStandardWithoutFile();
        CoapClient client;
        CoapResponse rsp;

        client = new CoapClient(getProtocol() + "://localhost:" + coapport + "/TestResource/Ducky");
        decorateClient(client);
        rsp = client.get();
        assertEquals(ResponseCode.CONTENT, rsp.getCode());
        assertEquals("Hello Ducky", rsp.getResponseText());
        rsp = client.post("data", MediaTypeRegistry.TEXT_PLAIN);
        assertEquals(ResponseCode.CONTENT, rsp.getCode());
        assertEquals("Hello Ducky: data", rsp.getResponseText());

        client = new CoapClient(getProtocol() + "://localhost:" + coapport + "/TestParams?id=Ducky");
        decorateClient(client);
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
    void testCoAPMethodNotAllowedResponse() throws Exception {
        Configuration.createStandardWithoutFile();
        CoapClient client = new CoapClient(getProtocol() + "://localhost:" + coapport + "/TestResource/Ducky");
        decorateClient(client);
        client.setTimeout(1000000L);
        CoapResponse rsp = client.delete();
        assertEquals(ResponseCode.METHOD_NOT_ALLOWED, rsp.getCode());
    }

    @Test
    void testCoAPNotFoundResponse() throws Exception {
        Configuration.createStandardWithoutFile();
        CoapClient client = new CoapClient(getProtocol() + "://localhost:" + coapport + "/foo/bar/cheese");
        decorateClient(client);
        client.setTimeout(1000000L);
        CoapResponse rsp = client.get();
        assertEquals(ResponseCode.NOT_FOUND, rsp.getCode());
    }

    @Test
    void testPOSTClientRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived("Hello some-id: xyz");
        mock.expectedHeaderReceived(CoAPConstants.COAP_RESPONSE_CODE, CoAP.ResponseCode.CONTENT.toString());
        sender.sendBodyAndHeader("xyz", CoAPConstants.COAP_METHOD, "POST");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testGETClientRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived("Hello some-id");
        mock.expectedHeaderReceived(CoAPConstants.COAP_RESPONSE_CODE, CoAP.ResponseCode.CONTENT.toString());
        sender.sendBody(null);
        MockEndpoint.assertIsSatisfied(context);
    }

    protected abstract String getProtocol();

    protected abstract void decorateClient(CoapClient client) throws GeneralSecurityException, IOException;

    protected abstract void decorateRestConfiguration(RestConfigurationDefinition restConfig);

    protected String getClientURI() {
        return getProtocol() + "://localhost:%d/TestResource/some-id";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                RestConfigurationDefinition restConfig
                        = restConfiguration().scheme(getProtocol()).host("localhost").port(coapport);
                decorateRestConfiguration(restConfig);

                rest("/TestParams").get().to("direct:get1").post().to("direct:post1");

                rest("/TestResource").get("/{id}").to("direct:get1").post("/{id}").to("direct:post1");

                from("direct:get1").process(exchange -> {
                    String id = exchange.getIn().getHeader("id", String.class);
                    exchange.getMessage().setBody("Hello " + id);
                });

                from("direct:post1").process(exchange -> {
                    String id = exchange.getIn().getHeader("id", String.class);
                    String ct = exchange.getIn().getHeader(CoAPConstants.CONTENT_TYPE, String.class);
                    exchange.getMessage().setBody("Hello " + id + ": " + exchange.getIn().getBody(String.class));
                    exchange.getMessage().setHeader(CoAPConstants.CONTENT_TYPE, ct);
                });

                from("direct:start").toF(getClientURI(), coapport).to("mock:result");
            }
        };
    }

}
