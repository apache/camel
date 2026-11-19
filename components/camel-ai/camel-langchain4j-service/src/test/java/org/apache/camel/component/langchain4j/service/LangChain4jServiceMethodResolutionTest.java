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
package org.apache.camel.component.langchain4j.service;

import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.component.langchain4j.service.support.MockAiServices;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LangChain4jServiceMethodResolutionTest extends LangChain4jServiceTestSupport {

    // Mock AI Service interfaces
    public interface SingleMethodService {
        String chat(String userMessage);
    }

    public interface MultiMethodService {
        String chat(String userMessage);

        String analyze(String userMessage);
    }

    public interface OverloadedMethodService {
        String chat(String userMessage);

        String chat(String userMessage, String context);
    }

    @Test
    void testSingleMethodAutoSelection() throws Exception {
        // Register mock service
        SingleMethodService mockService = MockAiServices.create(SingleMethodService.class);
        context.getRegistry().bind("myAssistant", mockService);

        startWithRouteTo("langchain4j-service:myAssistant");

        String result = template.requestBody("direct:start", "Hello", String.class);
        assertThat(result).isEqualTo("response from chat");
    }

    @Test
    void testMultiMethodWithExplicitMethod() throws Exception {
        // Register mock service
        MultiMethodService mockService = MockAiServices.create(MultiMethodService.class);
        context.getRegistry().bind("myAssistant", mockService);

        startWithRouteTo("langchain4j-service:myAssistant?method=analyze");

        String result = template.requestBody("direct:start", "Hello", String.class);
        assertThat(result).isEqualTo("response from analyze");
    }

    @Test
    void testMultiMethodWithoutMethodParameterFails() {
        // Register mock service
        MultiMethodService mockService = MockAiServices.create(MultiMethodService.class);
        context.getRegistry().bind("myAssistant", mockService);

        assertThatThrownBy(() -> {
            startWithRouteTo("langchain4j-service:myAssistant");
        })
                .as("a multi-method interface without an explicit method parameter is ambiguous, "
                    + "so route startup must fail fast and list the candidates")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("has multiple methods")
                .hasMessageContaining("Specify method parameter")
                .hasMessageContaining("Available methods: analyze, chat")
                // a proxy declares toString itself, and Camel's bean binding does not discard it, so the producer
                // filters it; the names are sorted, so only this excludes it from the list
                .hasMessageNotContaining("toString");
    }

    @Test
    void testServiceNotFoundFails() {
        // Do NOT register any service as 'nonexistent'

        assertThatThrownBy(() -> {
            startWithRouteTo("langchain4j-service:nonexistent");
        })
                .as("a service name that resolves to no registry bean must fail route startup, "
                    + "not the first message")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("No bean could be found in the registry for: nonexistent");
    }

    @Test
    void testInvalidMethodNameFails() {
        // Register mock service with only 'chat' method
        SingleMethodService mockService = MockAiServices.create(SingleMethodService.class);
        context.getRegistry().bind("myAssistant", mockService);

        assertThatThrownBy(() -> {
            startWithRouteTo("langchain4j-service:myAssistant?method=analyze");
        })
                .as("an explicitly configured method name that does not exist must never be ignored silently, "
                    + "even when the interface has a single method")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("Method 'analyze' not found")
                .hasMessageContaining("Available methods: chat");
    }

    @Test
    void testSingleMethodWithMatchingExplicitMethod() throws Exception {
        // Register mock service
        SingleMethodService mockService = MockAiServices.create(SingleMethodService.class);
        context.getRegistry().bind("myAssistant", mockService);

        startWithRouteTo("langchain4j-service:myAssistant?method=chat");

        String result = template.requestBody("direct:start", "Hello", String.class);

        assertThat(result)
                .as("an explicit method parameter that matches the single business method "
                    + "must be accepted, not rejected by the single-method shortcut")
                .isEqualTo("response from chat");
    }

    @Test
    void testInvalidMethodNameOnMultiMethodInterfaceFails() {
        // Register mock service with 'chat' and 'analyze' methods only
        MultiMethodService mockService = MockAiServices.create(MultiMethodService.class);
        context.getRegistry().bind("myAssistant", mockService);

        assertThatThrownBy(() -> {
            startWithRouteTo("langchain4j-service:myAssistant?method=translate");
        })
                .as("a method name matching none of several business methods must fail route startup "
                    + "exactly (no fuzzy or case-insensitive matching) and list the candidates")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("Method 'translate' not found")
                .hasMessageContaining("Available methods: analyze, chat")
                .hasMessageNotContaining("toString");
    }

    @Test
    void testOverloadedMethodFails() {
        // Register mock service
        OverloadedMethodService mockService = MockAiServices.create(OverloadedMethodService.class);
        context.getRegistry().bind("myAssistant", mockService);

        assertThatThrownBy(() -> {
            startWithRouteTo("langchain4j-service:myAssistant?method=chat");
        })
                .as("method resolution is by name only, so an overloaded name is ambiguous "
                    + "and must fail route startup")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("has multiple methods named 'chat'")
                .hasMessageContaining("Method overloading is not supported");
    }

    @Test
    void testEmptyMethodParameterIsTreatedAsUnspecified() throws Exception {
        context.getRegistry().bind("myAssistant", MockAiServices.create(SingleMethodService.class));

        startWithRouteTo("langchain4j-service:myAssistant?method=");

        assertThat(template.requestBody("direct:start", "Hello", String.class))
                .as("an empty method name says nothing, so a single-method service still resolves rather than "
                    + "failing to match a method called the empty string")
                .isEqualTo("response from chat");
    }

    @Test
    void testEmptyMethodParameterStillFailsOnAMultiMethodService() {
        context.getRegistry().bind("myAssistant", MockAiServices.create(MultiMethodService.class));

        assertThatThrownBy(() -> {
            addRouteTo("langchain4j-service:myAssistant?method=");
            context.start();
        })
                .as("an empty method name is not a choice between the methods, so it must fail the same way a "
                    + "missing one does")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("has multiple methods. Specify method parameter");
    }
}
