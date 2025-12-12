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

import java.util.Map;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.test.spring.junit6.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpringRestOpenApiReaderModelApiSecurityPlaceholderTest extends CamelSpringTestSupport {

    private Logger log = LoggerFactory.getLogger(getClass());

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        Map<String, Object> properties = Map.ofEntries(
                Map.entry("oauth.key", "petstore_auth"),
                Map.entry("oauth.description", "OAuth test"),
                Map.entry("oauth.authorization.url", "http://petstore.swagger.io/oauth/dialog"),
                Map.entry("oauth.flow", "implicit"),
                Map.entry("oauth.token.url", "http://petstore.swagger.io/oauth/token"),
                Map.entry("oauth.refresh.url", "http://petstore.swagger.io/oauth/refresh"),
                Map.entry("oauth.scopes", "read:pets,write:pets"),
                Map.entry("oauth.scope.read", "read"),
                Map.entry("oauth.scope.readers", "pets"),
                Map.entry("oauth.scope.write", "write"),
                Map.entry("oauth.scope.writers", "pets"),
                Map.entry("apiKey.key", "api_key"),
                Map.entry("apiKey.description", "API Key Test"),
                Map.entry("apiKey.header.name", "myHeader"),
                Map.entry("apiKey.inHeader", "true"),
                Map.entry("apiKey.inCookie", "false"),
                Map.entry("apiKey.inQuery", "false"),
                Map.entry("bearer.key", "bearer"),
                Map.entry("bearer.description", "Bearer Auth Test"),
                Map.entry("bearer.format", "org.apache.camel.openapi.User"),
                Map.entry("mtls.key", "mTLS"),
                Map.entry("mtls.description", "mTLS Auth Test"),
                Map.entry("oidc.key", "oidc"),
                Map.entry("oidc.description", "OpenID Connect OAuth Test"),
                Map.entry("oidc.url", "http://petstore.swagger.io/oauth/.well-known/openid-configuration"),
                Map.entry("basicAuth.key", "basic"),
                Map.entry("basicAuth.description", "Basic Auth Test"));
        System.getProperties().putAll(properties);
        return new ClassPathXmlApplicationContext(
                "org/apache/camel/openapi/SpringRestOpenApiReaderModelApiSecurityPlaceholderTest.xml");
    }

    @Test
    public void testReaderReadV3() throws Exception {
        BeanConfig config = new BeanConfig();
        config.setHost("localhost:8080");
        config.setSchemes(new String[] { "http" });
        config.setBasePath("/api");
        config.setTitle("Camel User store");
        config.setLicense("Apache 2.0");
        config.setLicenseUrl("http://www.apache.org/licenses/LICENSE-2.0.html");
        config.setVersion(BeanConfig.OPENAPI_VERSION_31.getVersion()); // Setting the version to 3.1 to test mTLS
        Info info = new Info();
        info.setDescription("OpenAPI 3.1 code first placeholder resolver test for security definitions");
        info.setVersion("1.0");
        info.setTitle("OpenAPI Security Definitions Placeholder Resolver Test 1.0");
        config.setInfo(info);
        RestOpenApiReader reader = new RestOpenApiReader();

        OpenAPI openApi = reader.read(context, context.getRestDefinitions(), config, context.getName(),
                new DefaultClassResolver());
        assertNotNull(openApi);

        String json = RestOpenApiSupport.getJsonFromOpenAPIAsString(openApi, config);
        log.info(json);

        assertTrue(json.contains("\"securitySchemes\" : {"));
        assertTrue(json.contains("\"type\" : \"oauth2\""));
        assertTrue(json.contains("\"authorizationUrl\" : \"http://petstore.swagger.io/oauth/dialog\""));
        assertTrue(json.contains("\"tokenUrl\" : \"http://petstore.swagger.io/oauth/token\""));
        assertTrue(json.contains("\"refreshUrl\" : \"http://petstore.swagger.io/oauth/refresh\""));
        assertTrue(json.contains("\"flows\" : {"));
        assertTrue(json.contains("\"implicit\" : {"));
        assertTrue(json.contains("\"scopes\" : {"));
        assertTrue(json.contains("\"read\" : \"pets\""));
        assertTrue(json.contains("\"write\" : \"pets\""));
        assertTrue(json.contains("\"type\" : \"apiKey\","));
        assertTrue(json.contains("\"in\" : \"header\""));
        assertTrue(json.contains("\"url\" : \"http://localhost:8080/api\""));
        assertTrue(json.contains("\"security\" : [ {"));
        assertTrue(json.contains("\"petstore_auth\" : [ \"write:pets\", \"read:pets\" ]"));
        assertTrue(json.contains("\"api_key\" : [ ]"));
        assertTrue(json.contains("\"basic\" : [ ]"));
        assertTrue(json.contains("\"bearer\" : [ ]"));
        assertTrue(json.contains("\"oidc\" : [ ]"));
        assertTrue(json.contains("\"mTLS\" : [ ]"));
        assertTrue(json.contains("\"description\" : \"The user returned\""));
        assertTrue(json.contains("\"$ref\" : \"#/components/schemas/User\""));
        assertTrue(json.contains("\"format\" : \"org.apache.camel.openapi.User\""));
        assertTrue(json.contains("\"type\" : \"string\""));
        assertTrue(json.contains("\"format\" : \"date\""));
        assertTrue(json.contains("\"description\" : \"OAuth test\""));
        assertTrue(json.contains("\"description\" : \"API Key Test\""));
        assertTrue(json.contains("\"description\" : \"Basic Auth Test\""));
        assertTrue(json.contains("\"description\" : \"Bearer Auth Test\""));
        assertTrue(json.contains("\"description\" : \"OpenID Connect OAuth Test\""));
        assertTrue(json.contains("\"description\" : \"mTLS Auth Test\""));
        assertTrue(json.contains("\"type\" : \"apiKey\""));
        assertTrue(json.contains("\"type\" : \"http\""));
        assertTrue(json.contains("\"type\" : \"mutualTLS\""));
        assertTrue(json.contains("\"type\" : \"openIdConnect\""));
        assertTrue(json.contains("\"name\" : \"myHeader\""));
        assertTrue(json.contains("\"in\" : \"header\""));
        assertTrue(json.contains("\"scheme\" : \"basic\""));
        assertTrue(json.contains("\"scheme\" : \"bearer\""));
        assertTrue(json.contains("\"bearerFormat\" : \"org.apache.camel.openapi.User\""));
        assertTrue(
                json.contains("\"openIdConnectUrl\" : \"http://petstore.swagger.io/oauth/.well-known/openid-configuration\""));
        assertFalse(json.contains("\"enum\""));
        assertFalse(json.contains("\"{{\""));

        context.stop();
    }
}
