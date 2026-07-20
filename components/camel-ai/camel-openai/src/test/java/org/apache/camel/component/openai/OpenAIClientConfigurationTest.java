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
package org.apache.camel.component.openai;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.core.ClientOptions;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.infra.openai.mock.ResponseBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAIClientConfigurationTest extends CamelTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ResponseBuilder RESPONSE_BUILDER = new ResponseBuilder(OBJECT_MAPPER);

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("header-test")
            .thenRespondWith((exchange, input) -> {
                assertEquals("my-org", exchange.getRequestHeaders().getFirst("OpenAI-Organization"));
                assertEquals("azure-key", exchange.getRequestHeaders().getFirst("api-key"));
                try {
                    return RESPONSE_BUILDER.createSimpleTextResponse("header ok");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .end()
            .build();

    @Test
    void clientConfigurationDefaults() {
        OpenAIConfiguration config = new OpenAIConfiguration();

        assertEquals(0, config.getRequestTimeout());
        assertEquals(2, config.getMaxRetries());
        assertNull(config.getAdditionalHeader());
    }

    @Test
    void clientConfigurationFromUri() throws Exception {
        CamelContext camelContext = context();
        OpenAIEndpoint endpoint = (OpenAIEndpoint) camelContext.getEndpoint(
                "openai:chat-completion?apiKey=dummy"
                                                                            + "&requestTimeout=45000"
                                                                            + "&maxRetries=0"
                                                                            + "&additionalHeader.OpenAI-Organization=my-org"
                                                                            + "&additionalHeader.api-key=secret");

        OpenAIConfiguration config = endpoint.getConfiguration();

        assertEquals(45_000, config.getRequestTimeout());
        assertEquals(0, config.getMaxRetries());
        assertEquals("my-org", config.getAdditionalHeader().get("OpenAI-Organization"));
        assertEquals("secret", config.getAdditionalHeader().get("api-key"));
    }

    @Test
    void createClientAppliesRequestTimeout() throws Exception {
        OpenAIEndpoint endpoint = createEndpoint();
        endpoint.getConfiguration().setRequestTimeout(30_000);

        OpenAIClient client = endpoint.createClient();
        try {
            ClientOptions options = clientOptions(client);
            assertEquals(Duration.ofMillis(30_000), options.timeout().request());
        } finally {
            client.close();
        }
    }

    @Test
    void createClientUsesSdkDefaultTimeoutWhenUnset() throws Exception {
        OpenAIEndpoint endpoint = createEndpoint();
        endpoint.getConfiguration().setRequestTimeout(0);

        OpenAIClient clientWithDefault = endpoint.createClient();
        try {
            ClientOptions defaultOptions = clientOptions(clientWithDefault);
            Duration sdkDefault = defaultOptions.timeout().request();

            endpoint.getConfiguration().setRequestTimeout(15_000);
            OpenAIClient clientWithCustom = endpoint.createClient();
            try {
                ClientOptions customOptions = clientOptions(clientWithCustom);
                assertEquals(Duration.ofMillis(15_000), customOptions.timeout().request());
                assertNotEquals(sdkDefault, customOptions.timeout().request());
            } finally {
                clientWithCustom.close();
            }
        } finally {
            clientWithDefault.close();
        }
    }

    @Test
    void createClientAppliesMaxRetries() throws Exception {
        OpenAIEndpoint endpoint = createEndpoint();
        endpoint.getConfiguration().setMaxRetries(0);

        OpenAIClient client = endpoint.createClient();
        try {
            assertEquals(0, clientOptions(client).maxRetries());
        } finally {
            client.close();
        }

        endpoint.getConfiguration().setMaxRetries(5);
        client = endpoint.createClient();
        try {
            assertEquals(5, clientOptions(client).maxRetries());
        } finally {
            client.close();
        }
    }

    @Test
    void createClientAppliesAdditionalHeaders() throws Exception {
        OpenAIEndpoint endpoint = createEndpoint();
        endpoint.getConfiguration().setAdditionalHeader(Map.of(
                "OpenAI-Organization", "org-123",
                "OpenAI-Project", "proj-456"));

        OpenAIClient client = endpoint.createClient();
        try {
            ClientOptions options = clientOptions(client);
            assertEquals("org-123", options.headers().values("OpenAI-Organization").get(0));
            assertEquals("proj-456", options.headers().values("OpenAI-Project").get(0));
        } finally {
            client.close();
        }
    }

    @Test
    void createClientIgnoresNullHeaderValues() throws Exception {
        OpenAIEndpoint endpoint = createEndpoint();
        Map<String, Object> headers = new HashMap<>();
        headers.put("OpenAI-Organization", "org-123");
        headers.put("OpenAI-Project", null);
        endpoint.getConfiguration().setAdditionalHeader(headers);

        OpenAIClient client = endpoint.createClient();
        try {
            ClientOptions options = clientOptions(client);
            assertEquals("org-123", options.headers().values("OpenAI-Organization").get(0));
            assertTrue(options.headers().values("OpenAI-Project").isEmpty());
        } finally {
            client.close();
        }
    }

    @Test
    void additionalHeadersAreSentOnChatCompletionRequest() {
        Exchange result = template.request("direct:chat-with-headers", e -> e.getIn().setBody("header-test"));
        assertEquals("header ok", result.getMessage().getBody(String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:chat-with-headers")
                        .to("openai:chat-completion?model=gpt-5&apiKey=dummy&baseUrl=" + openAIMock.getBaseUrl()
                            + "/v1&additionalHeader.OpenAI-Organization=my-org&additionalHeader.api-key=azure-key");
            }
        };
    }

    private OpenAIEndpoint createEndpoint() throws Exception {
        OpenAIEndpoint endpoint = (OpenAIEndpoint) context().getEndpoint("openai:chat-completion?apiKey=dummy");
        endpoint.getConfiguration().setAdditionalHeader(null);
        return endpoint;
    }

    /**
     * Reads the SDK's {@link ClientOptions} via reflection. Coupled to the OpenAI Java SDK internal field name
     * {@code clientOptions}; tests may need updating if the SDK renames it.
     */
    private static ClientOptions clientOptions(OpenAIClient client) throws Exception {
        Field field = client.getClass().getDeclaredField("clientOptions");
        field.setAccessible(true);
        return (ClientOptions) field.get(client);
    }
}
