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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RestOpenApiV2SecuritySchemesTest extends CamelTestSupport {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                rest()
                        .securityDefinitions()
                            .oauth2("petstore_auth_implicit")// OAuth implicit
                                .authorizationUrl("https://petstore.swagger.io/oauth/dialog")
                            .end()
                            .oauth2("oauth_password")
                                .flow("password")
                                .tokenUrl("https://petstore.swagger.io/oauth/token")
                            .end()
                            .oauth2("oauth2_accessCode")// OAuth access/authorization code
                                .authorizationUrl("https://petstore.swagger.io/oauth/dialog")
                                .tokenUrl("https://petstore.swagger.io/oauth/token")
                            .end()
                            .apiKey("api_key_header")
                                .withHeader("myHeader")
                            .end()
                            .apiKey("api_key_query")
                                .withQuery("myQuery")
                            .end()
                        .end();
            }
        };
    }

    @Test
    public void testSecuritySchemesV2() throws Exception {
        BeanConfig config = new BeanConfig();
        config.setHost("localhost:8080");
        config.setSchemes(new String[] { "http" });
        config.setBasePath("/api");
        config.setTitle("Camel User store");
        config.setLicense("Apache 2.0");
        config.setLicenseUrl("https://www.apache.org/licenses/LICENSE-2.0.html");
        config.setVersion("2.0");

        RestOpenApiReader reader = new RestOpenApiReader();
        OasDocument openApi = reader.read(context, context.getRestDefinitions(), config, context.getName(),
                new DefaultClassResolver());
        assertNotNull(openApi);

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        Object dump = Library.writeNode(openApi);
        String json = mapper.writeValueAsString(dump);

        log.info(json);

        json = json.replace("\n", " ").replaceAll("\\s+", " ");

        assertTrue(json.contains("\"petstore_auth_implicit\" : { \"flow\" : \"implicit\", \"authorizationUrl\" : " +
                                 "\"https://petstore.swagger.io/oauth/dialog\", \"type\" : \"oauth2\" }"));
        assertTrue(json.contains("\"oauth_password\" : { \"flow\" : \"password\", \"tokenUrl\" : " +
                                 "\"https://petstore.swagger.io/oauth/token\", \"type\" : \"oauth2\" }"));
        assertTrue(json.contains("\"oauth2_accessCode\" : { \"flow\" : \"accessCode\", \"authorizationUrl\" : " +
                                 "\"https://petstore.swagger.io/oauth/dialog\", \"tokenUrl\" : " +
                                 "\"https://petstore.swagger.io/oauth/token\", \"type\" : \"oauth2\" }"));
        assertTrue(
                json.contains("\"api_key_header\" : { \"type\" : \"apiKey\", \"name\" : \"myHeader\", \"in\" : \"header\" }"));
        assertTrue(json.contains("\"api_key_query\" : { \"type\" : \"apiKey\", \"name\" : \"myQuery\", \"in\" : \"query\" }"));
    }
}
