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
package org.apache.camel.component.azure.functions;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.Exchange;
import org.apache.camel.component.azure.functions.client.FunctionsClientFactory;
import org.apache.camel.component.azure.functions.client.FunctionsInvocationClient;
import org.apache.camel.component.azure.functions.client.FunctionsInvocationClient.FunctionInvocationResponse;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The URL that invokeFunction builds from the function app and the function name.
 */
class FunctionsProducerFunctionUrlTest extends CamelTestSupport {

    private final List<String> urls = new CopyOnWriteArrayList<>();

    @Test
    void urlIsOnTheHostOfTheFunctionApp() {
        Exchange result = send(functionsEndpoint(), null, null);

        assertNull(result.getException());
        assertEquals(List.of("https://myapp.azurewebsites.net/api/myfunction"), urls);
    }

    @Test
    void functionAppOfHeaderIsUsed() {
        Exchange result = send(functionsEndpoint(), "my-other-app2", null);

        assertNull(result.getException());
        assertEquals(List.of("https://my-other-app2.azurewebsites.net/api/myfunction"), urls);
    }

    @Test
    void routeWithSeveralSegmentsIsAccepted() {
        Exchange result = send(functionsEndpoint(), null, "products/42");

        assertNull(result.getException());
        assertEquals(List.of("https://myapp.azurewebsites.net/api/products/42"), urls);
    }

    @Test
    void functionNameIsEncoded() {
        Exchange result = send(functionsEndpoint(), null, "my function/Grüße");

        assertNull(result.getException());
        assertEquals(List.of("https://myapp.azurewebsites.net/api/my%20function/Gr%C3%BC%C3%9Fe"), urls);
    }

    @Test
    void missingFunctionAppIsRejected() {
        FunctionsEndpoint endpoint = functionsEndpoint();
        endpoint.getConfiguration().setFunctionApp(null);

        assertRejected(send(endpoint, null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "my.app", "myapp/api", "myapp:8080", "user@myapp", "myapp?x=1", "myapp#top", "my app", "-myapp", "myapp-",
            "a123456789012345678901234567890123456789012345678901234567890" })
    void functionAppThatIsNotAnAppNameIsRejected(String functionApp) {
        assertRejected(send(functionsEndpoint(), functionApp, null));
    }

    @ParameterizedTest
    @ValueSource(strings = { "myfunction?x=1", "myfunction#top", "myfunction/?x=1" })
    void functionNameWithQueryOrFragmentIsRejected(String functionName) {
        assertRejected(send(functionsEndpoint(), null, functionName));
    }

    @ParameterizedTest
    @ValueSource(strings = { ".", "..", "./myfunction", "../myfunction", "products/../myfunction", "products/." })
    void functionNameWithDotSegmentsIsRejected(String functionName) {
        assertRejected(send(functionsEndpoint(), null, functionName));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/myfunction", "products//42", "products/", "products\\42", "products%2F42" })
    void functionNameThatIsNotARelativePathIsRejected(String functionName) {
        assertRejected(send(functionsEndpoint(), null, functionName));
    }

    @Test
    void serviceUrlIsOnTheHostOfTheFunctionApp() {
        assertEquals("https://myapp.azurewebsites.net", functionsEndpoint().getServiceUrl());
    }

    @Test
    void serviceUrlOfFunctionAppThatIsNotAnAppNameIsNull() {
        FunctionsEndpoint endpoint = functionsEndpoint();
        endpoint.getConfiguration().setFunctionApp("my.app");

        assertNull(endpoint.getServiceUrl());
    }

    private Exchange send(FunctionsEndpoint endpoint, String functionApp, String functionName) {
        return template.send(endpoint, e -> {
            e.getIn().setBody("Hello");
            e.getIn().setHeader(FunctionsConstants.FUNCTION_APP, functionApp);
            e.getIn().setHeader(FunctionsConstants.FUNCTION_NAME, functionName);
        });
    }

    private void assertRejected(Exchange result) {
        assertInstanceOf(IllegalArgumentException.class, result.getException());
        assertTrue(urls.isEmpty(), "the function must not be invoked");
    }

    /**
     * The azure-functions endpoint of the test, with an invocation client that records the URL instead of sending.
     */
    private FunctionsEndpoint functionsEndpoint() {
        FunctionsEndpoint endpoint = context.getEndpoint(
                "azure-functions:myapp/myfunction?functionKey=secret", FunctionsEndpoint.class);
        endpoint.setClientFactory(new FunctionsClientFactory() {
            @Override
            public FunctionsInvocationClient createInvocationClient(FunctionsConfiguration configuration) {
                return new FunctionsInvocationClient(
                        configuration.getFunctionKey(), configuration.getHostKey(),
                        configuration.getConnectionTimeout(), configuration.getReadTimeout()) {
                    @Override
                    public FunctionInvocationResponse invoke(
                            String url, String method, Object body, Map<String, String> headers) {
                        urls.add(url);
                        return new FunctionInvocationResponse(200, "{\"result\":\"ok\"}", Map.of());
                    }
                };
            }
        });
        return endpoint;
    }
}
