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
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.apache.camel.Exchange;
import org.apache.camel.spi.RestClientRequestValidator;
import org.apache.camel.test.junit6.ExchangeTestSupport;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class OpenApiRestClientRequestValidatorTest extends ExchangeTestSupport {

    static OpenAPI openAPI;
    static OpenApiRestClientRequestValidator validator;

    @BeforeAll
    static void setup() throws IOException {
        String data = IOHelper.loadText(OpenApiRestClientRequestValidatorTest.class.getResourceAsStream("/petstore-v3.json"));
        OpenAPIV3Parser parser = new OpenAPIV3Parser();
        SwaggerParseResult out = parser.readContents(data);
        openAPI = out.getOpenAPI();
        validator = new OpenApiRestClientRequestValidator();
    }

    @Test
    public void testValidateBody() {
        exchange.setProperty(Exchange.REST_OPENAPI, openAPI);
        exchange.getMessage().setHeader(Exchange.HTTP_METHOD, "PUT");
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "pet");
        exchange.getMessage().setHeader("Content-Type", "application/json");
        exchange.getMessage().setHeader("Accept", "application/json");
        exchange.getMessage().setBody("");

        RestClientRequestValidator.ValidationError error
                = validator.validate(exchange, new RestClientRequestValidator.ValidationContext(
                        "application/json", "application/json", true, null, null, null, null));
        Assertions.assertNotNull(error);
        Assertions.assertTrue(error.body().contains("A request body is required but none found"));

        exchange.getMessage().setBody("{ \"name\": \"tiger\" }");
        error = validator.validate(exchange, new RestClientRequestValidator.ValidationContext(
                "application/json", "application/json", true, null, null, null, null));
        Assertions.assertNotNull(error);
        Assertions.assertTrue(error.body().contains("Object has missing required properties ([\\\"photoUrls\\\"])"));

        exchange.getMessage().setBody("{ \"name\": \"tiger\", \"photoUrls\": [\"image.jpg\"] }");
        error = validator.validate(exchange, new RestClientRequestValidator.ValidationContext(
                "application/json", "application/json", true, null, null, null, null));
        Assertions.assertNull(error);

        // turn off required validator
        exchange.getMessage().setBody("{ \"name\": \"tiger\" }");
        context.getRestConfiguration().setValidationLevels(Map.of("validation.request.body.schema.required", "INFO"));
        error = validator.validate(exchange, new RestClientRequestValidator.ValidationContext(
                "application/json", "application/json", true, null, null, null, null));
        Assertions.assertNull(error);
        context.getRestConfiguration().setValidationLevels(null);
    }

    @Test
    public void testValidateQueryParam() {
        exchange.setProperty(Exchange.REST_OPENAPI, openAPI);
        exchange.getMessage().setHeader(Exchange.HTTP_METHOD, "GET");
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "pet/findByStatus");
        exchange.getMessage().setHeader("Accept", "application/json");
        exchange.getMessage().setBody("");

        RestClientRequestValidator.ValidationError error
                = validator.validate(exchange, new RestClientRequestValidator.ValidationContext(
                        "application/json", "application/json", true, null, null, null, null));

        Assertions.assertNotNull(error);
        Assertions.assertTrue(error.body().contains("Query parameter 'status' is required"));

        exchange.getMessage().setHeader(Exchange.HTTP_QUERY, "status=available");

        error = validator.validate(exchange, new RestClientRequestValidator.ValidationContext(
                "application/json", "application/json", true, null, null, null, null));
        Assertions.assertNull(error);
    }

    @Test
    public void testValidateHeader() {
        exchange.setProperty(Exchange.REST_OPENAPI, openAPI);
        exchange.setProperty(Exchange.CONTENT_TYPE, "application/json");
        exchange.getMessage().setHeader(Exchange.HTTP_METHOD, "GET");
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "pet/findByTags");
        exchange.getMessage().setHeader("Accept", "application/json");
        exchange.getMessage().setBody("");

        RestClientRequestValidator.ValidationError error
                = validator.validate(exchange, new RestClientRequestValidator.ValidationContext(
                        "application/json", "application/json", true, null, null, null, null));

        Assertions.assertNotNull(error);
        Assertions.assertTrue(error.body().contains("Header parameter 'tags' is required"));

        exchange.getMessage().setHeader("tags", "dog");

        error = validator.validate(exchange, new RestClientRequestValidator.ValidationContext(
                "application/json", "application/json", true, null, null, null, null));
        Assertions.assertNull(error);
    }

    @Test
    public void testValidateRepeatedScalarHeader() {
        exchange.setProperty(Exchange.REST_OPENAPI, openAPI);
        exchange.setProperty(Exchange.CONTENT_TYPE, "application/json");
        exchange.getMessage().setHeader(Exchange.HTTP_METHOD, "DELETE");
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "pet/123");
        exchange.getMessage().setHeader("Accept", "application/json");
        exchange.getMessage().setBody("");

        // A header sent more than once arrives as a List, exactly as CollectionHelper.appendEntry
        // leaves it. api_key is declared "type": "string", so two values violate the contract.
        exchange.getMessage().setHeader("api_key", List.of("key-one", "key-two"));

        RestClientRequestValidator.ValidationError error
                = validator.validate(exchange, new RestClientRequestValidator.ValidationContext(
                        "application/json", "application/json", false, null, null, null, null));

        Assertions.assertNotNull(error, "a repeated scalar header parameter must be reported");
        Assertions.assertEquals(400, error.statusCode());
        Assertions.assertFalse(error.body().contains("[key-one, key-two]"),
                "the collection must not be stringified into the validated value");
    }

    @Test
    public void testValidateSingleScalarHeaderStillPasses() {
        exchange.setProperty(Exchange.REST_OPENAPI, openAPI);
        exchange.setProperty(Exchange.CONTENT_TYPE, "application/json");
        exchange.getMessage().setHeader(Exchange.HTTP_METHOD, "DELETE");
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "pet/123");
        exchange.getMessage().setHeader("Accept", "application/json");
        exchange.getMessage().setHeader("api_key", "key-one");
        exchange.getMessage().setBody("");

        RestClientRequestValidator.ValidationError error
                = validator.validate(exchange, new RestClientRequestValidator.ValidationContext(
                        "application/json", "application/json", false, null, null, null, null));

        Assertions.assertNull(error);
    }

    @Test
    public void testValidateRepeatedArrayHeaderIsReported() {
        exchange.setProperty(Exchange.REST_OPENAPI, openAPI);
        exchange.setProperty(Exchange.CONTENT_TYPE, "application/json");
        exchange.getMessage().setHeader(Exchange.HTTP_METHOD, "GET");
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "pet/findByTags");
        exchange.getMessage().setHeader("Accept", "application/json");
        exchange.getMessage().setBody("");

        // tags is "type": "array", but a header array is serialized as ONE header with a
        // comma-separated value (style "simple", explode=false) - repeating the header is not the
        // wire form the contract describes, and is reported as such once the values reach the
        // validator at all.
        exchange.getMessage().setHeader("tags", List.of("dog", "cat"));

        RestClientRequestValidator.ValidationError error
                = validator.validate(exchange, new RestClientRequestValidator.ValidationContext(
                        "application/json", "application/json", true, null, null, null, null));

        Assertions.assertNotNull(error);
        Assertions.assertTrue(error.body().contains("expected an array style of 'simple'"), error.body());
    }

    @Test
    public void testValidateArrayHeaderInSimpleStyleStillPasses() {
        exchange.setProperty(Exchange.REST_OPENAPI, openAPI);
        exchange.setProperty(Exchange.CONTENT_TYPE, "application/json");
        exchange.getMessage().setHeader(Exchange.HTTP_METHOD, "GET");
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "pet/findByTags");
        exchange.getMessage().setHeader("Accept", "application/json");
        exchange.getMessage().setHeader("tags", "dog,cat");
        exchange.getMessage().setBody("");

        RestClientRequestValidator.ValidationError error
                = validator.validate(exchange, new RestClientRequestValidator.ValidationContext(
                        "application/json", "application/json", true, null, null, null, null));

        Assertions.assertNull(error, "the form the contract does describe must stay valid");
    }

    @Test
    public void testValidateRepeatedScalarHeaderInMixedCase() {
        exchange.setProperty(Exchange.REST_OPENAPI, openAPI);
        exchange.setProperty(Exchange.CONTENT_TYPE, "application/json");
        exchange.getMessage().setHeader(Exchange.HTTP_METHOD, "DELETE");
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "pet/123");
        exchange.getMessage().setHeader("Accept", "application/json");
        exchange.getMessage().setBody("");

        // HTTP header names are case-insensitive, and so is Camel's header map: a client repeating
        // the header under a different spelling still produces one entry holding both values.
        exchange.getMessage().setHeader("Api_Key", List.of("key-one", "key-two"));

        RestClientRequestValidator.ValidationError error
                = validator.validate(exchange, new RestClientRequestValidator.ValidationContext(
                        "application/json", "application/json", false, null, null, null, null));

        Assertions.assertNotNull(error, "the spelling on the wire must not decide whether it is checked");
    }
}
