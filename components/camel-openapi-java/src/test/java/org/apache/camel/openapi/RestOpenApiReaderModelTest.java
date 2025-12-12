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

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import io.swagger.v3.oas.models.OpenAPI;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RestOpenApiReaderModelTest extends CamelTestSupport {

    private Logger log = LoggerFactory.getLogger(getClass());

    @BindToRegistry("dummy-rest")
    private DummyRestConsumerFactory factory = new DummyRestConsumerFactory();

    @BindToRegistry("userService")
    private Object dummy = new Object();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // this user REST service is json only
                rest("/user").tag("dude").description("User rest service").consumes("application/json")
                        .produces("application/json")

                        .get("/{id}/{date}").description("Find user by id and date").outType(User.class).responseMessage()
                        .message("The user returned").endResponseMessage().param()
                        .name("id").type(RestParamType.path).description("The id of the user to get").endParam().param()
                        .name("date").type(RestParamType.path).description("The date")
                        .dataFormat("date").endParam().to("bean:userService?method=getUser(${header.id})")

                        .put().description("Updates or create a user").type(User.class).param().name("body")
                        .type(RestParamType.body).description("The user to update or create")
                        .endParam().to("bean:userService?method=updateUser")

                        .get("/findAll").description("Find all users").outType(User[].class).responseMessage()
                        .message("All the found users").endResponseMessage()
                        .to("bean:userService?method=listUsers")

                        .post().description("Update all users").type(User[].class).to("bean:userService?method=updateAllUsers");
            }
        };
    }

    @ParameterizedTest
    @ValueSource(strings = { "3.1", "3.0" })
    public void testReaderReadV3(String version) throws Exception {
        BeanConfig config = new BeanConfig();
        config.setHost("localhost:8080");
        config.setSchemes(new String[] { "http" });
        config.setBasePath("/api");
        config.setTitle("Camel User store");
        config.setLicense("Apache 2.0");
        config.setLicenseUrl("http://www.apache.org/licenses/LICENSE-2.0.html");
        config.setVersion(version);
        RestOpenApiReader reader = new RestOpenApiReader();

        OpenAPI openApi = reader.read(context, context.getRestDefinitions(), config, context.getName(),
                new DefaultClassResolver());
        assertNotNull(openApi);

        String json = RestOpenApiSupport.getJsonFromOpenAPIAsString(openApi, config);
        log.info(json);

        DocumentContext doc = JsonPath.parse(json);

        assertEquals("http://localhost:8080/api", doc.read("$.servers[0].url"));
        assertEquals("User rest service", doc.read("$.tags[0].description"));
        assertEquals("#/components/schemas/User",
                doc.read("$.paths['/user'].put.requestBody.content['application/json'].schema['$ref']"));
        assertEquals("org.apache.camel.openapi.User", doc.read("$.components.schemas.User['x-className'].format"));

        assertEquals("string", doc.read("$.paths['/user/{id}/{date}'].get.parameters[1].schema.type"));
        assertEquals("date", doc.read("$.paths['/user/{id}/{date}'].get.parameters[1].schema.format"));

        assertEquals(44, doc.read("$.components.schemas.User.properties.age.example", Integer.class));

        // Ensure valid schema for array request body and response body CAMEL-21076
        assertEquals("array", doc.read("$.paths['/user'].post.requestBody.content['application/json'].schema.type"));
        assertEquals("#/components/schemas/User",
                doc.read("$.paths['/user'].post.requestBody.content['application/json'].schema.items['$ref']"));

        assertEquals("array",
                doc.read("$.paths['/user/findAll'].get.responses['200'].content['application/json'].schema.type"));
        assertEquals("#/components/schemas/User",
                doc.read("$.paths['/user/findAll'].get.responses['200'].content['application/json'].schema.items['$ref']"));

        if (config.isOpenApi30()) {
            // nullable is only supported in OpenAPI 3.0
            assertEquals(true, doc.read("$.components.schemas.User.properties.age.nullable", Boolean.class));
        }

        // Ensure valid array output ref CAMEL-19818
        assertFalse(json.contains("\"$ref\" : \"#/components/schemas/org.apache.camel.openapi.User\""));

        // do not populate enum when no allowable values are set
        assertFalse(json.contains("\"enum\""));

        context.stop();
    }
}
