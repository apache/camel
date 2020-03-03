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
package org.apache.camel.swagger;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.swagger.jaxrs.config.BeanConfig;
import io.swagger.models.Swagger;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RestSwaggerReaderModelBookOrderTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(RestSwaggerReaderModelBookOrderTest.class);

    @BindToRegistry("dummy-rest")
    private DummyRestConsumerFactory factory = new DummyRestConsumerFactory();

    @BindToRegistry("bookService")
    private Object dummy = new Object();

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // this user REST service is json only
                rest("/books").tag("dude").description("Book order service").consumes("application/json").produces("application/json")

                    .get("/{id}").description("Find order by id").outType(BookOrder.class).responseMessage().message("The order returned").endResponseMessage().param().name("id")
                    .type(RestParamType.path).description("The id of the order to get").dataType("integer").endParam().to("bean:bookService?method=getOrder(${header.id})")
                    .get("/books/{id}/line/{lineNum}").outType(LineItem.class).to("bean:bookService?method=getOrder(${header.id})");
            }
        };
    }

    @Test
    public void testReaderRead() throws Exception {
        BeanConfig config = new BeanConfig();
        config.setHost("localhost:8080");
        config.setSchemes(new String[] {"http"});
        config.setBasePath("/api");
        config.setTitle("Camel User store");
        config.setLicense("Apache 2.0");
        config.setLicenseUrl("http://www.apache.org/licenses/LICENSE-2.0.html");
        RestSwaggerReader reader = new RestSwaggerReader();

        Swagger swagger = reader.read(context.getRestDefinitions(), null, config, context.getName(), new DefaultClassResolver());
        assertNotNull(swagger);

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        String json = mapper.writeValueAsString(swagger);

        LOG.info(json);

        assertTrue(json.contains("\"host\" : \"localhost:8080\""));
        assertTrue(json.contains("\"description\" : \"The order returned\""));
        assertTrue(json.contains("\"BookOrder\""));
        assertTrue(json.contains("\"LineItem\""));
        assertTrue(json.contains("\"$ref\" : \"#/definitions/BookOrder\""));
        assertTrue(json.contains("\"$ref\" : \"#/definitions/LineItem\""));
        assertTrue(json.contains("\"x-className\""));
        assertTrue(json.contains("\"format\" : \"org.apache.camel.swagger.BookOrder\""));
        assertTrue(json.contains("\"format\" : \"org.apache.camel.swagger.LineItem\""));

        context.stop();
    }

}
