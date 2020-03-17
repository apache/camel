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
package org.apache.camel.component.jetty.rest;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.rest.RestConfigurationDefinition;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.SSLContextServerParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class RestHttpsClientAuthRouteTest extends CamelTestSupport {
    static int port = AvailablePortFinder.getNextAvailable();

    @Produce("direct:start")
    protected ProducerTemplate sender;

    @Test
    public void testGETClientRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived("Hello some-id");
        sender.sendBody(null);
        assertMockEndpointsSatisfied();
    }

    protected String getClientURI() {
        return "http://localhost:%d/TestResource/some-id?sslContextParameters=#clientSSLContextParameters";
    }

    protected void decorateRestConfiguration(RestConfigurationDefinition restConfig) {
        KeyStoreParameters keystoreParameters = new KeyStoreParameters();
        keystoreParameters.setResource("service.jks");
        keystoreParameters.setPassword("security");

        SSLContextParameters serviceSSLContextParameters = new SSLContextParameters();
        KeyManagersParameters serviceSSLKeyManagers = new KeyManagersParameters();
        serviceSSLKeyManagers.setKeyPassword("security");
        serviceSSLKeyManagers.setKeyStore(keystoreParameters);
        serviceSSLContextParameters.setKeyManagers(serviceSSLKeyManagers);

        KeyStoreParameters truststoreParameters = new KeyStoreParameters();
        truststoreParameters.setResource("truststore.jks");
        truststoreParameters.setPassword("storepass");

        TrustManagersParameters clientAuthServiceSSLTrustManagers = new TrustManagersParameters();
        clientAuthServiceSSLTrustManagers.setKeyStore(truststoreParameters);
        serviceSSLContextParameters.setTrustManagers(clientAuthServiceSSLTrustManagers);
        SSLContextServerParameters clientAuthSSLContextServerParameters = new SSLContextServerParameters();
        clientAuthSSLContextServerParameters.setClientAuthentication("REQUIRE");
        serviceSSLContextParameters.setServerParameters(clientAuthSSLContextServerParameters);

        SSLContextParameters clientSSLContextParameters = new SSLContextParameters();
        TrustManagersParameters clientSSLTrustManagers = new TrustManagersParameters();
        clientSSLTrustManagers.setKeyStore(truststoreParameters);
        clientSSLContextParameters.setTrustManagers(clientSSLTrustManagers);

        KeyManagersParameters clientAuthClientSSLKeyManagers = new KeyManagersParameters();
        clientAuthClientSSLKeyManagers.setKeyPassword("security");
        clientAuthClientSSLKeyManagers.setKeyStore(keystoreParameters);
        clientSSLContextParameters.setKeyManagers(clientAuthClientSSLKeyManagers);

        context.getRegistry().bind("serviceSSLContextParameters", serviceSSLContextParameters);
        context.getRegistry().bind("clientSSLContextParameters", clientSSLContextParameters);

        restConfig.endpointProperty("sslContextParameters", "#serviceSSLContextParameters");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                RestConfigurationDefinition restConfig = restConfiguration().scheme("https").host("localhost").port(port);
                decorateRestConfiguration(restConfig);

                rest("/TestParams").get().to("direct:get1").post().to("direct:post1");

                rest("/TestResource").get("/{id}").to("direct:get1").post("/{id}").to("direct:post1");

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
                        exchange.getOut().setBody("Hello " + id + ": " + exchange.getIn().getBody(String.class));
                        exchange.getOut().setHeader(Exchange.CONTENT_TYPE, ct);
                    }
                });

                from("direct:start").toF(getClientURI(), port).to("mock:result");
            }
        };
    }

}
