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
package org.apache.camel.component.rest.postman;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.rest.postman.support.PostmanRequestBinding;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The value of a path parameter of a collection request is decoded as a path segment (RFC 3986), as the Rest DSL
 * consumers do.
 */
class RestPostmanPathParameterDecodingTest {

    private static final String COLLECTION = "classpath:petstore-collection.json";

    private DefaultCamelContext context;

    @BeforeEach
    void setUp() throws Exception {
        context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:getPetById").to("mock:out");
            }
        });
        context.start();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "/v3/pet/caf%C3%A9|café",
            "/v3/pet/X%20Y|X Y",
            "/v3/pet/a%2Fb|a/b" })
    void testPathParameterIsDecoded(String path, String expected) throws Exception {
        List<PostmanRequestBinding> bindings
                = context.getEndpoint("rest-postman:" + COLLECTION, RestPostmanEndpoint.class).resolveBindings();
        DefaultRestPostmanProcessorStrategy strategy = new DefaultRestPostmanProcessorStrategy();
        strategy.setCamelContext(context);
        strategy.setMissingRequest("ignore");
        RestPostmanProcessor processor
                = new RestPostmanProcessor(bindings, null, COLLECTION, "/v3", null, false, strategy);
        processor.setCamelContext(context);
        processor.afterPropertiesConfigured(context);

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, path);
        exchange.getMessage().setHeader(Exchange.HTTP_METHOD, "GET");
        processor.process(exchange, done -> {
        });

        assertEquals(expected, exchange.getMessage().getHeader("petId"));
    }
}
