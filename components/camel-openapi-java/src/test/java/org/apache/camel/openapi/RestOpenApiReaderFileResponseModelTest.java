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

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RestOpenApiReaderFileResponseModelTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(RestOpenApiReaderFileResponseModelTest.class);

    @BindToRegistry("dummy-rest")
    private DummyRestConsumerFactory factory = new DummyRestConsumerFactory();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                rest("/hello").consumes("application/json").produces("application/octet-stream").get("/pdf/{name}").description("Saying hi").param().name("name")
                    .type(RestParamType.path).dataType("string").description("Who is it").example("Donald Duck").endParam().responseMessage().code(200)
                    .message("A document as reply").responseModel(java.io.File.class).endResponseMessage().to("log:hi");
            }
        };
    }

    @ParameterizedTest
    @ValueSource(strings = { "3.1", "3.0", "2.0" })
    public void testReaderRead(String version) throws Exception {
        BeanConfig config = new BeanConfig();
        config.setHost("localhost:8080");
        config.setSchemes(new String[] { "http" });
        config.setBasePath("/api");
        config.setInfo(new Info());
        config.setVersion(version);
        RestOpenApiReader reader = new RestOpenApiReader();

        OpenAPI openApi = reader.read(context, context.getRestDefinitions(), config, context.getName(),
                new DefaultClassResolver());
        assertNotNull(openApi);

        String json = RestOpenApiSupport.getJsonFromOpenAPIAsString(openApi, config);
        LOG.info(json);

        if (config.isOpenApi2()) {
            assertTrue(json.contains("\"type\" : \"file\""));
        } else {
            assertTrue(json.contains("\"format\" : \"binary\""));
            assertTrue(json.contains("\"type\" : \"string\""));
        }

        context.stop();
    }

}
