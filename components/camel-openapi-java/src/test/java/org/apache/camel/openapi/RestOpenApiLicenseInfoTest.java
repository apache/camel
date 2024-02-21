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
package org.apache.camel.openapi;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RestOpenApiLicenseInfoTest {

    @ParameterizedTest
    @ValueSource(strings = { "3.1", "3.0", "2.0" })
    public void testLicenseInfo(String openApiVersion) throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration()
                        .apiProperty("openapi.version", openApiVersion)
                        .apiProperty("api.contact.name", "Mr Camel")
                        .apiProperty("api.contact.email", "camel@apache.org")
                        .apiProperty("api.contact.url", "https://camel.apache.org")
                        .apiProperty("api.license.name", "Apache V2")
                        .apiProperty("api.license.url", "https://www.apache.org/licenses/LICENSE-2.0");

                rest("/api")
                        .get("/api").to("direct:api");
                from("direct:api").setBody().constant("Hello World");
            }
        });

        RestConfiguration restConfiguration = context.getRestConfiguration();
        RestOpenApiProcessor processor
                = new RestOpenApiProcessor(restConfiguration.getApiProperties(), restConfiguration);
        Exchange exchange = new DefaultExchange(context);
        processor.process(exchange);

        String json = exchange.getMessage().getBody(String.class);
        assertNotNull(json);

        assertTrue(json.contains("\"url\" : \"https://www.apache.org/licenses/LICENSE-2.0\""));
        assertTrue(json.contains("\"name\" : \"Apache V2\""));
        assertTrue(json.contains("\"name\" : \"Mr Camel\""));
        assertTrue(json.contains("\"email\" : \"camel@apache.org\""));
        assertTrue(json.contains("\"url\" : \"https://camel.apache.org\""));
    }
}
