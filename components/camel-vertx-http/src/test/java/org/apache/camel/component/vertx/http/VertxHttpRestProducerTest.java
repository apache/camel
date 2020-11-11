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
package org.apache.camel.component.vertx.http;

import java.io.InputStream;
import java.net.URI;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.rest.openapi.RestOpenApiComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VertxHttpRestProducerTest extends VertxHttpTestSupport {

    @Test
    public void testVertxHttpRestProducer() throws InterruptedException {
        String response = template.requestBodyAndHeader("petstore:getPetById", null, "petId", 1, String.class);
        assertNotNull(response);
        assertTrue(response.contains("\"name\": \"Cat 1\""));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        RestOpenApiComponent petstore = new RestOpenApiComponent(camelContext);
        petstore.setHost(getTestServerUrl());
        petstore.setSpecificationUri(new URI(getTestServerUrl() + "/api/v3/petstore.json"));
        petstore.setComponentName("vertx-http");
        camelContext.addComponent("petstore", petstore);
        return camelContext;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(getTestServerUri() + "/api/v3/petstore.json").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        InputStream stream = VertxHttpRestProducerTest.class.getResourceAsStream("/openapi/petstore.json");
                        String json = exchange.getContext().getTypeConverter().convertTo(String.class, stream);
                        exchange.getMessage().setBody(json);
                    }
                });

                from(getTestServerUri() + "/api/v3/pet/?matchOnUriPrefix=true").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        InputStream stream = VertxHttpRestProducerTest.class.getResourceAsStream("/openapi/pet.json");
                        String json = exchange.getContext().getTypeConverter().convertTo(String.class, stream);
                        exchange.getMessage().setBody(json);
                    }
                });
            }
        };
    }
}
