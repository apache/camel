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

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.component.langchain4j.service.support.MockAiServices;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LangChain4jServiceParameterBindingTest extends LangChain4jServiceTestSupport {

    // Mock AI Service interfaces
    public interface UserMessageService {
        String chat(@UserMessage String text);
    }

    public interface TemplateVariableService {
        String analyze(@UserMessage String text, @V("context") String context);
    }

    public interface MemoryIdService {
        String chat(@UserMessage String text, @MemoryId String memoryId);
    }

    public interface VoidReturnService {
        void log(@UserMessage String text);
    }

    public interface ZeroParameterService {
        @UserMessage("Give me a tip")
        String tip();
    }

    public interface TypeConversionService {
        String process(@UserMessage Integer number);
    }

    public interface NullReturnService {
        String chat(@UserMessage String text);
    }

    public interface ConvertedVariableService {
        String chat(@UserMessage String text, @V("count") Integer count);
    }

    public interface ConvertedMemoryIdService {
        String chat(@UserMessage String text, @MemoryId Integer memoryId);
    }

    public interface AnnotatedOnInterfaceService {
        String chat(@UserMessage String text, @V("tone") String tone, @MemoryId String memoryId);
    }

    /**
     * An ordinary class implementing the annotated interface, the shape a framework generates: the annotations stay on
     * the interface and the implementation carries none. A JDK proxy cannot stand in for it, because a proxy's methods
     * are the interface's own and already carry them.
     */
    public static class PlainImplementation implements AnnotatedOnInterfaceService {

        // Deliberately fields rather than getters: a getter would be a second business method and would force the
        // method parameter, which is not what this test is about
        String text;
        String tone;
        String memoryId;

        @Override
        public String chat(String text, String tone, String memoryId) {
            this.text = text;
            this.tone = tone;
            this.memoryId = memoryId;
            return "response from chat";
        }
    }

    @Test
    void testUserMessageFromBody() throws Exception {
        // Track invocation
        AtomicReference<String> capturedText = new AtomicReference<>();

        // Register mock service
        UserMessageService mockService = MockAiServices.create(UserMessageService.class, (proxy, method, args) -> {
            capturedText.set((String) args[0]);
            return "response from chat";
        });
        context.getRegistry().bind("myAssistant", mockService);

        startWithRouteTo("langchain4j-service:myAssistant");

        String result = template.requestBody("direct:start", "Hello", String.class);

        assertThat(capturedText.get()).isEqualTo("Hello");
        assertThat(result).isEqualTo("response from chat");
    }

    @Test
    void testTemplateVariableFromHeader() throws Exception {
        // Track invocation
        AtomicReference<String> capturedText = new AtomicReference<>();
        AtomicReference<String> capturedContext = new AtomicReference<>();

        // Register mock service
        TemplateVariableService mockService = MockAiServices.create(TemplateVariableService.class, (proxy, method, args) -> {
            capturedText.set((String) args[0]);
            capturedContext.set((String) args[1]);
            return "response from analyze";
        });
        context.getRegistry().bind("myAssistant", mockService);

        startWithRouteTo("langchain4j-service:myAssistant");

        String result = template.requestBodyAndHeader("direct:start", "Hello",
                LangChain4jServiceHeaders.VARIABLES, Map.of("context", "background info"), String.class);

        assertThat(capturedText.get()).isEqualTo("Hello");
        assertThat(capturedContext.get()).isEqualTo("background info");
        assertThat(result).isEqualTo("response from analyze");
    }

    @Test
    void testMemoryIdFromHeader() throws Exception {
        // Track invocation
        AtomicReference<String> capturedText = new AtomicReference<>();
        AtomicReference<String> capturedMemoryId = new AtomicReference<>();

        // Register mock service
        MemoryIdService mockService = MockAiServices.create(MemoryIdService.class, (proxy, method, args) -> {
            capturedText.set((String) args[0]);
            capturedMemoryId.set((String) args[1]);
            return "response from chat";
        });
        context.getRegistry().bind("myAssistant", mockService);

        startWithRouteTo("langchain4j-service:myAssistant");

        String result = template.requestBodyAndHeader("direct:start", "Hello",
                LangChain4jServiceHeaders.MEMORY_ID, "session-123", String.class);

        assertThat(capturedText.get()).isEqualTo("Hello");
        assertThat(capturedMemoryId.get()).isEqualTo("session-123");
        assertThat(result).isEqualTo("response from chat");
    }

    @Test
    void testAnnotationsDeclaredOnTheInterfaceBindOnAPlainImplementation() throws Exception {
        PlainImplementation service = new PlainImplementation();
        context.getRegistry().bind("myAssistant", service);

        startWithRouteTo("langchain4j-service:myAssistant");

        Map<String, Object> headers = Map.of(
                LangChain4jServiceHeaders.VARIABLES, Map.of("tone", "formal"),
                LangChain4jServiceHeaders.MEMORY_ID, "session-123");
        String result = template.requestBodyAndHeaders("direct:start", "Hello", headers, String.class);

        assertThat(service)
                .as("the implementation declares no annotation of its own, so every parameter was bound from the "
                    + "annotations Camel's bean binding collected on the interface it implements, which is the shape "
                    + "a framework-generated AI Service has")
                .extracting("text", "tone", "memoryId")
                .containsExactly("Hello", "formal", "session-123");
        assertThat(result)
                .as("the return value of the invoked method becomes the new body")
                .isEqualTo("response from chat");
    }

    @Test
    void testMissingRequiredHeaderFails() throws Exception {
        // Register mock service
        TemplateVariableService mockService = MockAiServices.create(TemplateVariableService.class, (proxy, method, args) -> {
            return "should not be called";
        });
        context.getRegistry().bind("myAssistant", mockService);

        startWithRouteTo("langchain4j-service:myAssistant");

        assertThatThrownBy(() -> template.requestBody("direct:start", "Hello", String.class))
                .as("a missing variables header must surface as NoSuchHeaderException so users can match it in onException")
                .isInstanceOf(CamelExecutionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .isInstanceOf(NoSuchHeaderException.class)
                .hasMessageContaining("No 'CamelLangChain4jServiceVariables' header available");
    }

    @Test
    void testMissingVariableKeyFails() throws Exception {
        // Register mock service
        TemplateVariableService mockService = MockAiServices.create(TemplateVariableService.class, (proxy, method, args) -> {
            return "should not be called";
        });
        context.getRegistry().bind("myAssistant", mockService);

        startWithRouteTo("langchain4j-service:myAssistant");

        assertThatThrownBy(() -> template.requestBodyAndHeader("direct:start", "Hello",
                LangChain4jServiceHeaders.VARIABLES, Map.of("other", "value"), String.class))
                .as("a variables map that lacks the @V key must fail with a message naming the variable, "
                    + "not pass null to the AI Service")
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template variable 'context' not found in the 'CamelLangChain4jServiceVariables'");
    }

    @Test
    void testMissingMemoryIdHeaderFails() throws Exception {
        // Register mock service
        MemoryIdService mockService = MockAiServices.create(MemoryIdService.class, (proxy, method, args) -> {
            return "should not be called";
        });
        context.getRegistry().bind("myAssistant", mockService);

        startWithRouteTo("langchain4j-service:myAssistant");

        assertThatThrownBy(() -> template.requestBody("direct:start", "Hello", String.class))
                .as("a missing @MemoryId header must surface as NoSuchHeaderException, like a missing @V header")
                .isInstanceOf(CamelExecutionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .isInstanceOf(NoSuchHeaderException.class)
                .hasMessageContaining("No 'CamelLangChain4jServiceMemoryId' header available");
    }

    @Test
    void testVoidReturnLeavesBodyUnchanged() throws Exception {
        // Track invocation
        AtomicReference<String> capturedText = new AtomicReference<>();

        // Register mock service
        VoidReturnService mockService = MockAiServices.create(VoidReturnService.class, (proxy, method, args) -> {
            capturedText.set((String) args[0]);
            return null; // void method
        });
        context.getRegistry().bind("myAssistant", mockService);

        startWithRouteTo("langchain4j-service:myAssistant");

        String result = template.requestBody("direct:start", "Hello", String.class);

        assertThat(capturedText.get()).isEqualTo("Hello");
        assertThat(result).isEqualTo("Hello");
    }

    @Test
    void testZeroParameterMethod() throws Exception {
        // Track invocation. The sentinel is a non-null array because JDK proxies pass null,
        // not an empty array, for zero-arg methods - isNull() below relies on that
        AtomicReference<Boolean> methodCalled = new AtomicReference<>(false);
        AtomicReference<Object[]> capturedArgs = new AtomicReference<>(new Object[0]);

        // Register mock service
        ZeroParameterService mockService = MockAiServices.create(ZeroParameterService.class, (proxy, method, args) -> {
            methodCalled.set(true);
            capturedArgs.set(args);
            return "Here's a tip";
        });
        context.getRegistry().bind("myAssistant", mockService);

        startWithRouteTo("langchain4j-service:myAssistant");

        String result = template.requestBody("direct:start", "ignored", String.class);

        assertThat(methodCalled.get())
                .as("the zero-parameter method must still be invoked, even though the message body is ignored")
                .isTrue();
        assertThat(capturedArgs.get())
                .as("a zero-parameter method must be invoked without any argument")
                .isNull();
        assertThat(result).isEqualTo("Here's a tip");
    }

    @Test
    void testTypeConversion() throws Exception {
        // Track invocation
        AtomicReference<Integer> capturedNumber = new AtomicReference<>();

        // Register mock service
        TypeConversionService mockService = MockAiServices.create(TypeConversionService.class, (proxy, method, args) -> {
            capturedNumber.set((Integer) args[0]);
            return "processed: " + args[0];
        });
        context.getRegistry().bind("myAssistant", mockService);

        startWithRouteTo("langchain4j-service:myAssistant");

        String result = template.requestBody("direct:start", "42", String.class);

        assertThat(capturedNumber.get()).isEqualTo(42);
        assertThat(result).isEqualTo("processed: 42");
    }

    @Test
    void testNullBodyFails() throws Exception {
        // Register mock service
        UserMessageService mockService = MockAiServices.create(UserMessageService.class, (proxy, method, args) -> {
            return "should not be called";
        });
        context.getRegistry().bind("myAssistant", mockService);

        startWithRouteTo("langchain4j-service:myAssistant");

        assertThatThrownBy(() -> template.requestBody("direct:start", null, String.class))
                .as("a null body cannot feed the @UserMessage parameter, so the invocation must fail with a "
                    + "message naming the parameter by its role instead of a NullPointerException downstream")
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "Message body is null but the @UserMessage parameter expects a java.lang.String user message");
    }

    @Test
    void testTypeConversionFailureFails() throws Exception {
        // Register mock service
        TypeConversionService mockService = MockAiServices.create(TypeConversionService.class, (proxy, method, args) -> {
            return "should not be called";
        });
        context.getRegistry().bind("myAssistant", mockService);

        startWithRouteTo("langchain4j-service:myAssistant");

        assertThatThrownBy(() -> template.requestBody("direct:start", "not-a-number", String.class))
                .as("a body that cannot be converted to the Integer parameter must fail the invocation with a "
                    + "message naming the parameter by its role, not pass a wrong value to the AI Service")
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to resolve the @UserMessage parameter for method 'process'");
    }

    @Test
    void testNullReturnSetsTheBodyToNull() throws Exception {
        NullReturnService mockService = MockAiServices.create(NullReturnService.class, (proxy, method, args) -> null);
        context.getRegistry().bind("myAssistant", mockService);

        startWithRouteTo("langchain4j-service:myAssistant");

        Exchange result = template.request("direct:start", exchange -> exchange.getMessage().setBody("Hello"));

        assertThat(result.getMessage().getBody())
                .as("the method is not void, so its return value replaces the body even when it is null; only a "
                    + "void method leaves the body alone")
                .isNull();
    }

    @Test
    void testTemplateVariableIsConverted() throws Exception {
        AtomicReference<Integer> capturedCount = new AtomicReference<>();

        ConvertedVariableService mockService
                = MockAiServices.create(ConvertedVariableService.class, (proxy, method, args) -> {
                    capturedCount.set((Integer) args[1]);
                    return "response from chat";
                });
        context.getRegistry().bind("myAssistant", mockService);

        startWithRouteTo("langchain4j-service:myAssistant");

        template.requestBodyAndHeader("direct:start", "Hello",
                LangChain4jServiceHeaders.VARIABLES, Map.of("count", "7"), String.class);

        assertThat(capturedCount.get())
                .as("a @V value goes through Camel type conversion like the body does, so a String map value "
                    + "reaches an Integer parameter")
                .isEqualTo(7);
    }

    @Test
    void testMemoryIdIsConverted() throws Exception {
        AtomicReference<Integer> capturedMemoryId = new AtomicReference<>();

        ConvertedMemoryIdService mockService
                = MockAiServices.create(ConvertedMemoryIdService.class, (proxy, method, args) -> {
                    capturedMemoryId.set((Integer) args[1]);
                    return "response from chat";
                });
        context.getRegistry().bind("myAssistant", mockService);

        startWithRouteTo("langchain4j-service:myAssistant");

        template.requestBodyAndHeader("direct:start", "Hello",
                LangChain4jServiceHeaders.MEMORY_ID, "42", String.class);

        assertThat(capturedMemoryId.get())
                .as("a @MemoryId header goes through Camel type conversion too, so a String header reaches an "
                    + "Integer parameter")
                .isEqualTo(42);
    }
}
