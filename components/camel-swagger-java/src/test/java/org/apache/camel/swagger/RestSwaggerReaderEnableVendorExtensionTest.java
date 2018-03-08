/**
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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultClassResolver;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class RestSwaggerReaderEnableVendorExtensionTest extends CamelTestSupport {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("dummy-rest", new DummyRestConsumerFactory());
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // enable vendor extensions
                restConfiguration().apiVendorExtension(true);

                // this user REST service is json only
                rest("/user").tag("dude").description("User rest service")
                    .consumes("application/json").produces("application/json")

                    .get("/{id}").description("Find user by id").outType(User.class)
                        .responseMessage().message("The user returned").endResponseMessage()
                        .param().name("id").type(RestParamType.path).description("The id of the user to get").dataType("integer").endParam()
                        .to("bean:userService?method=getUser(${header.id})")

                    .put().description("Updates or create a user").type(User.class)
                        .param().name("body").type(RestParamType.body).description("The user to update or create").endParam()
                        .to("bean:userService?method=updateUser")

                    .get("/findAll").description("Find all users").outTypeList(User.class)
                        .responseMessage().message("All the found users").endResponseMessage()
                        .to("bean:userService?method=listUsers");
            }
        };
    }

    @Test
    public void testEnableVendorExtension() throws Exception {
        BeanConfig config = new BeanConfig();
        config.setHost("localhost:8080");
        config.setSchemes(new String[]{"http"});
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

        log.info(json);

        String camelId = context.getName();
        String routeId = context.getRouteDefinitions().get(0).getId();

        assertTrue(json.contains("\"host\" : \"localhost:8080\""));
        assertTrue(json.contains("\"description\" : \"The user returned\""));
        assertTrue(json.contains("\"$ref\" : \"#/definitions/User\""));
        assertFalse(json.contains("\"enum\""));
        assertTrue(json.contains("\"x-camelContextId\" : \"" + camelId + "\""));
        assertTrue(json.contains("\"x-routeId\" : \"" + routeId + "\""));
        context.stop();
    }

}
