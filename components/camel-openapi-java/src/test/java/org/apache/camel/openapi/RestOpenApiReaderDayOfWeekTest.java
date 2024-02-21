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

public class RestOpenApiReaderDayOfWeekTest extends CamelTestSupport {

    private Logger log = LoggerFactory.getLogger(getClass());

    @BindToRegistry("dummy-rest")
    private DummyRestConsumerFactory factory = new DummyRestConsumerFactory();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // this user REST service is json only
                rest("/day").tag("dude").description("Day service").consumes("application/json").produces("application/json")

                        .get("/week").description("Day of week").param().name("day").type(RestParamType.query)
                        .description("Day of week").defaultValue("friday").dataType("string")
                        .allowableValues("monday", "tuesday", "wednesday", "thursday", "friday").endParam().responseMessage()
                        .code(200).responseModel(DayResponse.class)
                        .header("X-Rate-Limit-Limit").description("The number of allowed requests in the current period")
                        .dataType("integer").endHeader().endResponseMessage()
                        .to("log:week");
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
        config.setTitle("Day");
        config.setLicense("Apache 2.0");
        config.setLicenseUrl("http://www.apache.org/licenses/LICENSE-2.0.html");
        config.setVersion(version);
        RestOpenApiReader reader = new RestOpenApiReader();

        OpenAPI openApi = reader.read(context, context.getRestDefinitions(), config, context.getName(),
                new DefaultClassResolver());
        assertNotNull(openApi);

        String json = RestOpenApiSupport.getJsonFromOpenAPIAsString(openApi, config);
        log.info(json);

        if (config.isOpenApi2()) {
            assertTrue(json.contains("\"host\" : \"localhost:8080\""));
            assertTrue(json.contains("\"$ref\" : \"#/definitions/DayResponse\""));
        } else {
            assertTrue(json.contains("\"url\" : \"http://localhost:8080/api\""));
            assertTrue(json.contains("\"$ref\" : \"#/components/schemas/DayResponse\""));
        }
        assertTrue(json.contains("\"default\" : \"friday\""));
        assertTrue(json.contains("\"enum\" : [ \"monday\", \"tuesday\", \"wednesday\", \"thursday\", \"friday\" ]"));
        assertTrue(json.contains("\"format\" : \"org.apache.camel.openapi.DayResponse\""));
        assertTrue(json.contains("\"X-Rate-Limit-Limit\" : {"));
        assertTrue(json.contains("\"description\" : \"The number of allowed requests in the current period\""));

        context.stop();
    }
}
