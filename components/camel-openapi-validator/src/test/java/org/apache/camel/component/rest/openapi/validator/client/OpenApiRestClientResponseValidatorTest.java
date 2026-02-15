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
package org.apache.camel.component.rest.openapi.validator.client;

import java.io.IOException;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.apache.camel.Exchange;
import org.apache.camel.spi.RestClientResponseValidator;
import org.apache.camel.test.junit6.ExchangeTestSupport;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class OpenApiRestClientResponseValidatorTest extends ExchangeTestSupport {

    static OpenAPI openAPI;
    static OpenApiRestClientResponseValidator validator;

    @BeforeAll
    static void setup() throws IOException {
        String data = IOHelper.loadText(OpenApiRestClientResponseValidatorTest.class.getResourceAsStream("/petstore-v3.json"));
        OpenAPIV3Parser parser = new OpenAPIV3Parser();
        SwaggerParseResult out = parser.readContents(data);
        openAPI = out.getOpenAPI();
        validator = new OpenApiRestClientResponseValidator();
    }

    @Test
    public void testValidateBody() {
        exchange.setProperty(Exchange.REST_OPENAPI, openAPI);
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
        exchange.getMessage().setHeader(Exchange.HTTP_METHOD, "PUT");
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "pet");
        exchange.getMessage().setHeader("Accept", "application/json");
        exchange.getMessage().setBody("");

        RestClientResponseValidator.ValidationError error
                = validator.validate(exchange, new RestClientResponseValidator.ValidationContext(
                        "application/json", "application/json", null, null));

        Assertions.assertNotNull(error);
        Assertions
                .assertTrue(error.body().contains("PUT on path '/pet' defines a response schema but no response body found."));

        exchange.getMessage().setBody("{ \"name\": \"tiger\" }");
        error = validator.validate(exchange, new RestClientResponseValidator.ValidationContext(
                "application/json", "application/json", null, null));
        Assertions.assertTrue(error.body().contains("Object has missing required properties ([\\\"photoUrls\\\"])"));

        exchange.getMessage().setBody("{ \"name\": \"tiger\", \"photoUrls\": [\"image.jpg\"] }");
        error = validator.validate(exchange, new RestClientResponseValidator.ValidationContext(
                "application/json", "application/json", null, null));
        Assertions.assertNull(error);
    }

    @Test
    public void testValidateHeader() {
        exchange.setProperty(Exchange.REST_OPENAPI, openAPI);
        exchange.getMessage().setHeader(Exchange.HTTP_METHOD, "GET");
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "user/login");
        exchange.getMessage().setHeader("Accept", "application/json");
        exchange.getMessage().setBody("{ the user is here }");

        RestClientResponseValidator.ValidationError error
                = validator.validate(exchange, new RestClientResponseValidator.ValidationContext(
                        null, "application/json", null, null));

        Assertions.assertNotNull(error);
        Assertions.assertTrue(
                error.body().contains("X-Rate-Limit' is expected on path '/user/login' but not found in response."));

        exchange.getMessage().setHeader("X-Rate-Limit", 123);

        error = validator.validate(exchange, new RestClientResponseValidator.ValidationContext(
                "application/json", "application/json", null, null));
        Assertions.assertNull(error);
    }
}
