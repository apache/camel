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
package org.apache.camel.component.cxf.jaxrs;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CxfRsProducerStreamCacheTest extends CamelTestSupport {

    private int port;
    private Server rsServer;

    @Override
    @Deprecated
    protected boolean useJmx() {
        return false;
    }

    @Override
    @Deprecated
    protected void doPreSetup() throws Exception {
        port = AvailablePortFinder.getNextAvailable();
        startRsEchoServer();
    }

    @AfterEach
    public void stopServer() {
        if (rsServer != null) {
            rsServer.stop();
            rsServer.destroy();
        }
    }

    private void startRsEchoServer() {
        JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
        sf.setAddress("http://localhost:" + port + "/rs");
        sf.setServiceBeans(Collections.singletonList(new EchoResource()));
        rsServer = sf.create();
        rsServer.start();
    }

    @Path("/")
    public static class EchoResource {
        @POST
        @Path("/echo")
        @Consumes(MediaType.WILDCARD)
        @Produces(MediaType.TEXT_PLAIN)
        public String echo(String body) {
            return body;
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        final String cxfrsUri = "cxfrs://http://localhost:" + port + "/rs"
                                + "?httpClientAPI=true"
                                + "&throwExceptionOnFailure=false";

        return new RouteBuilder() {
            @Override
            public void configure() {
                // Ensure stream caching is ON for the context
                getContext().setStreamCaching(true);
                getContext().getStreamCachingStrategy().setSpoolEnabled(true);
                getContext().getStreamCachingStrategy().setSpoolThreshold(1024 * 1024 * 10); // 10MB

                from("direct:start")

                        .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                        .setHeader(Exchange.HTTP_PATH, constant("/echo"))
                        .setExchangePattern(ExchangePattern.InOut)
                        // 1) Call the REST endpoint via cxfrs PRODUCER
                        .to(cxfrsUri)
                        // 2) read response after cxfrs call multiple times
                        .process(e -> {
                            e.getIn().getBody(String.class);
                        })
                        .log("The body is ===> ${body}");

            }
        };
    }

    @Test
    public void testProducerStreamCacheWithCxfrs() throws InterruptedException {
        final String payload = "hello-cxfrs-producer-stream-cache";
        InputStream body = new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8));

        ProducerTemplate tpl = template;
        String response = tpl.requestBody("direct:start", body, String.class);

        assertEquals(payload, response, "Echo response must match original payload");
        getMockEndpoint("mock:result").expectedMessageCount(1);
    }
}
