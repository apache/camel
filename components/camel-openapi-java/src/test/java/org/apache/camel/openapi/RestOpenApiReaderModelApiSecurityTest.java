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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.openapi.models.OasDocument;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class RestOpenApiReaderModelApiSecurityTest extends CamelTestSupport {

    @BindToRegistry("dummy-rest")
    private DummyRestConsumerFactory factory = new DummyRestConsumerFactory();

    @BindToRegistry("userService")
    private Object dummy = new Object();

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                rest("/user").tag("dude").description("User rest service")
                    // setup security definitions
                    .securityDefinitions().oauth2("petstore_auth").authorizationUrl("http://petstore.swagger.io/oauth/dialog").end().apiKey("api_key").withHeader("myHeader").end()
                    .end().consumes("application/json").produces("application/json")

                    .get("/{id}/{date}").description("Find user by id and date").outType(User.class).responseMessage().message("The user returned").endResponseMessage()
                    // setup security for this rest verb
                    .security("api_key").param().name("id").type(RestParamType.path).description("The id of the user to get").endParam().param().name("date")
                    .type(RestParamType.path).description("The date").dataFormat("date").endParam().to("bean:userService?method=getUser(${header.id})")

                    .put().description("Updates or create a user").type(User.class)
                    // setup security for this rest verb
                    .security("petstore_auth", "write:pets,read:pets").param().name("body").type(RestParamType.body).description("The user to update or create").endParam()
                    .to("bean:userService?method=updateUser")

                    .get("/findAll").description("Find all users").outType(User[].class).responseMessage().message("All the found users").endResponseMessage()
                    .to("bean:userService?method=listUsers");
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
        config.setVersion("2.0");
        RestOpenApiReader reader = new RestOpenApiReader();

        OasDocument openApi = reader.read(context.getRestDefinitions(), null, config, context.getName(), new DefaultClassResolver());
        assertNotNull(openApi);

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        Object dump = Library.writeNode(openApi);
        String json = mapper.writeValueAsString(dump);

        log.info(json);

        assertTrue(json.contains("\"securityDefinitions\" : {"));
        assertTrue(json.contains("\"type\" : \"oauth2\""));
        assertTrue(json.contains("\"authorizationUrl\" : \"http://petstore.swagger.io/oauth/dialog\""));
        assertTrue(json.contains("\"flow\" : \"implicit\""));
        assertTrue(json.contains("\"type\" : \"apiKey\","));
        assertTrue(json.contains("\"in\" : \"header\""));
        assertTrue(json.contains("\"host\" : \"localhost:8080\""));
        assertTrue(json.contains("\"security\" : [ {"));
        assertTrue(json.contains("\"petstore_auth\" : [ \"write:pets\", \"read:pets\" ]"));
        assertTrue(json.contains("\"api_key\" : [ ]"));
        assertTrue(json.contains("\"description\" : \"The user returned\""));
        assertTrue(json.contains("\"$ref\" : \"#/definitions/User\""));
        assertTrue(json.contains("\"x-className\""));
        assertTrue(json.contains("\"format\" : \"org.apache.camel.openapi.User\""));
        assertTrue(json.contains("\"type\" : \"string\""));
        assertTrue(json.contains("\"format\" : \"date\""));
        assertFalse(json.contains("\"enum\""));
        context.stop();
    }

    @Test
    public void testReaderReadV3() throws Exception {
        BeanConfig config = new BeanConfig();
        config.setHost("localhost:8080");
        config.setSchemes(new String[] {"http"});
        config.setBasePath("/api");
        config.setTitle("Camel User store");
        config.setLicense("Apache 2.0");
        config.setLicenseUrl("http://www.apache.org/licenses/LICENSE-2.0.html");
        RestOpenApiReader reader = new RestOpenApiReader();

        OasDocument openApi = reader.read(context.getRestDefinitions(), null, config, context.getName(), new DefaultClassResolver());
        assertNotNull(openApi);

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        Object dump = Library.writeNode(openApi);
        String json = mapper.writeValueAsString(dump);
        
        log.info(json);

        assertTrue(json.contains("securitySchemes"));
        assertTrue(json.contains("\"type\" : \"oauth2\""));
        assertTrue(json.contains("\"authorizationUrl\" : \"http://petstore.swagger.io/oauth/dialog\""));
        assertTrue(json.contains("\"flows\" : {"));
        assertTrue(json.contains("\"implicit\""));
        assertTrue(json.contains("\"type\" : \"apiKey\","));
        assertTrue(json.contains("\"in\" : \"header\""));
        assertTrue(json.contains("\"url\" : \"http://localhost:8080/api\""));
        assertTrue(json.contains("\"security\" : [ {"));
        assertTrue(json.contains("\"petstore_auth\" : [ \"write:pets\", \"read:pets\" ]"));
        assertTrue(json.contains("\"api_key\" : [ ]"));
        assertTrue(json.contains("\"description\" : \"The user returned\""));
        assertTrue(json.contains("\"$ref\" : \"#/components/schemas/User\""));
        assertTrue(json.contains("\"x-className\""));
        assertTrue(json.contains("\"format\" : \"org.apache.camel.openapi.User\""));
        assertTrue(json.contains("\"type\" : \"string\""));
        assertTrue(json.contains("\"format\" : \"date\""));
        assertFalse(json.contains("\"enum\""));
        context.stop();
    }
}
