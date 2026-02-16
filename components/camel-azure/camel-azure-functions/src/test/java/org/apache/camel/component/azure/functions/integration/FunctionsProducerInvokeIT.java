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
package org.apache.camel.component.azure.functions.integration;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.functions.FunctionsConstants;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for invoking Azure Functions.
 * <p>
 * Run with:
 *
 * <pre>
 * mvn verify -Dcamel.component.azure-functions.function-app=myFunctionApp \
 *            -Dcamel.component.azure-functions.function-name=myFunction \
 *            -Dcamel.component.azure-functions.function-key=myFunctionKey
 * </pre>
 */
@EnabledIfSystemProperty(named = BaseFunctionsTestSupport.FUNCTION_APP_PROPERTY, matches = ".*",
                         disabledReason = "Azure Functions credentials must be supplied to run this test, e.g: "
                                          + "mvn verify -D" + BaseFunctionsTestSupport.FUNCTION_APP_PROPERTY + "=myApp "
                                          + "-D" + BaseFunctionsTestSupport.FUNCTION_NAME_PROPERTY + "=myFunction "
                                          + "-D" + BaseFunctionsTestSupport.FUNCTION_KEY_PROPERTY + "=myKey")
public class FunctionsProducerInvokeIT extends BaseFunctionsTestSupport {

    private static final String DIRECT_INVOKE_GET = "direct:invokeGet";
    private static final String DIRECT_INVOKE_POST = "direct:invokePost";
    private static final String DIRECT_INVOKE_WITH_BODY = "direct:invokeWithBody";

    private ProducerTemplate producerTemplate;

    @BeforeEach
    void beforeEach() {
        producerTemplate = contextExtension.getProducerTemplate();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Invoke function with GET
                from(DIRECT_INVOKE_GET)
                        .toF("azure-functions:%s/%s?httpMethod=GET", FUNCTION_APP, FUNCTION_NAME);

                // Invoke function with POST (default)
                from(DIRECT_INVOKE_POST)
                        .toF("azure-functions:%s/%s", FUNCTION_APP, FUNCTION_NAME);

                // Invoke function with JSON body
                from(DIRECT_INVOKE_WITH_BODY)
                        .toF("azure-functions:%s/%s?httpMethod=POST", FUNCTION_APP, FUNCTION_NAME);
            }
        };
    }

    @RouteFixture
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(createRouteBuilder());
    }

    @Test
    void testInvokeFunctionWithGet() {
        Exchange result = producerTemplate.request(DIRECT_INVOKE_GET, exchange -> {
            // No body for GET request
        });

        assertNotNull(result);
        Integer statusCode = result.getMessage().getHeader(FunctionsConstants.STATUS_CODE, Integer.class);
        assertNotNull(statusCode, "Status code should be returned");
        assertTrue(statusCode >= 200 && statusCode < 300, "Expected successful response, got: " + statusCode);

        String body = result.getMessage().getBody(String.class);
        assertNotNull(body, "Response body should not be null");
    }

    @Test
    void testInvokeFunctionWithPost() {
        Exchange result = producerTemplate.request(DIRECT_INVOKE_POST, exchange -> {
            exchange.getIn().setBody("Hello from Camel");
        });

        assertNotNull(result);
        Integer statusCode = result.getMessage().getHeader(FunctionsConstants.STATUS_CODE, Integer.class);
        assertNotNull(statusCode, "Status code should be returned");
        assertTrue(statusCode >= 200 && statusCode < 300, "Expected successful response, got: " + statusCode);
    }

    @Test
    void testInvokeFunctionWithJsonBody() {
        String jsonBody = "{\"name\": \"Camel\"}";

        Exchange result = producerTemplate.request(DIRECT_INVOKE_WITH_BODY, exchange -> {
            exchange.getIn().setBody(jsonBody);
            exchange.getIn().setHeader("Content-Type", "application/json");
        });

        assertNotNull(result);
        Integer statusCode = result.getMessage().getHeader(FunctionsConstants.STATUS_CODE, Integer.class);
        assertNotNull(statusCode, "Status code should be returned");
        assertTrue(statusCode >= 200 && statusCode < 300, "Expected successful response, got: " + statusCode);
    }

    @Test
    void testInvokeFunctionWithCustomHeaders() {
        Map<String, String> customHeaders = new HashMap<>();
        customHeaders.put("X-Custom-Header", "custom-value");
        customHeaders.put("Content-Type", "application/json");

        Exchange result = producerTemplate.request(DIRECT_INVOKE_POST, exchange -> {
            exchange.getIn().setBody("{\"test\": true}");
            exchange.getIn().setHeader(FunctionsConstants.HTTP_HEADERS, customHeaders);
        });

        assertNotNull(result);
        Integer statusCode = result.getMessage().getHeader(FunctionsConstants.STATUS_CODE, Integer.class);
        assertNotNull(statusCode, "Status code should be returned");
    }

    @Test
    void testInvokeFunctionReturnsResponseHeaders() {
        Exchange result = producerTemplate.request(DIRECT_INVOKE_GET, exchange -> {
            // No body for GET
        });

        assertNotNull(result);
        Object responseHeaders = result.getMessage().getHeader(FunctionsConstants.RESPONSE_HEADERS);
        assertNotNull(responseHeaders, "Response headers should be returned");
    }

    @Test
    void testInvokeFunctionWithHttpMethodOverride() {
        Exchange result = producerTemplate.request(DIRECT_INVOKE_POST, exchange -> {
            // Override HTTP method via header
            exchange.getIn().setHeader(FunctionsConstants.HTTP_METHOD, "GET");
        });

        assertNotNull(result);
        Integer statusCode = result.getMessage().getHeader(FunctionsConstants.STATUS_CODE, Integer.class);
        assertNotNull(statusCode, "Status code should be returned");
    }
}
