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

import java.io.Serializable;
import java.util.Map;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.langchain4j.service.support.MockAiServices;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the fail-fast validations performed when the producer starts: annotation conflicts, interface resolution and
 * the lazyStartProducer deferral of those failures to the first message.
 */
class LangChain4jServiceValidationTest extends LangChain4jServiceTestSupport {

    // Mock AI Service interfaces exercising each validation branch

    public interface ConflictingAnnotationsService {
        String chat(@UserMessage @V("context") String text);
    }

    public interface MultipleUserMessageService {
        String chat(@UserMessage String first, @UserMessage String second);
    }

    public interface UnannotatedMultiParamService {
        String chat(String first, String second);
    }

    public interface DuplicateVariableService {
        String chat(@UserMessage String text, @V("context") String first, @V("context") String second);
    }

    public interface EmptyVariableNameService {
        String chat(@UserMessage String text, @V("") String context);
    }

    public interface StreamingService {
        TokenStream chat(@UserMessage String text);
    }

    public interface DuplicateMemoryIdService {
        String chat(@UserMessage String text, @MemoryId String first, @MemoryId String second);
    }

    public interface GreetingService {
        String greet(String text);
    }

    // Methodless marker: it contributes no method, so it cannot make the resolution ambiguous
    public interface MarkerInterface {
    }

    static class BaseGreetingBean implements GreetingService {
        @Override
        public String greet(String text) {
            return "greetings " + text;
        }
    }

    // The method comes from the superclass; Serializable and the marker contribute none
    static class ConcreteGreetingBean extends BaseGreetingBean implements Serializable, MarkerInterface {
    }

    // Intentionally package-private: Camel's bean binding makes the method accessible before invoking it
    interface NonPublicService {
        String chat(@UserMessage String text);
    }

    public interface FirstBusinessInterface {
        String chat(String text);
    }

    public interface SecondBusinessInterface {
        String analyze(String text);
    }

    static class MultiInterfaceBean implements FirstBusinessInterface, SecondBusinessInterface {
        @Override
        public String chat(String text) {
            return "chat";
        }

        @Override
        public String analyze(String text) {
            return "analyze";
        }
    }

    // An agent interface as AgenticServices builds it: the @Agent method is the business method
    public interface MyAgent {
        @Agent("Writes a story about a topic")
        String writeStory(@V("topic") String topic);
    }

    public interface DefaultAndStaticMethodService {
        String chat(String text);

        default String helper() {
            return "helper";
        }

        static String util() {
            return "util";
        }
    }

    public interface MemoryIdAndUserMessageService {
        String chat(@UserMessage @MemoryId String text);
    }

    public interface MemoryIdAndVariableService {
        String chat(@UserMessage String text, @V("context") @MemoryId String context);
    }

    public interface AllThreeAnnotationsService {
        String chat(@UserMessage @V("context") @MemoryId String text);
    }

    /**
     * A plain class, not a proxy: a proxy's generated method carries no parameter names even when the build passes
     * -parameters, so only a real class can reach the branch that names the parameter instead of its position.
     */
    public static class NamedParameterBean implements EmptyVariableNameService {
        @Override
        public String chat(String text, String context) {
            return "response from chat";
        }
    }

    @Test
    void testConflictingAnnotationsFails() {
        context.getRegistry().bind("myAssistant", MockAiServices.create(ConflictingAnnotationsService.class));

        assertThatThrownBy(() -> {
            addRouteTo("langchain4j-service:myAssistant");
            context.start();
        })
                .as("a parameter carrying both @UserMessage and @V is ambiguous, "
                    + "so route startup must fail and name the conflicting annotations")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("has conflicting annotations")
                .hasMessageContaining("@UserMessage and @V");
    }

    @Test
    void testMultipleUserMessageParametersFails() {
        context.getRegistry().bind("myAssistant", MockAiServices.create(MultipleUserMessageService.class));

        assertThatThrownBy(() -> {
            addRouteTo("langchain4j-service:myAssistant");
            context.start();
        })
                .as("only one parameter can be fed from the message body, "
                    + "so two @UserMessage parameters must fail route startup")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("has multiple @UserMessage parameters");
    }

    @Test
    void testUnannotatedMultiParameterFails() {
        context.getRegistry().bind("myAssistant", MockAiServices.create(UnannotatedMultiParamService.class));

        assertThatThrownBy(() -> {
            addRouteTo("langchain4j-service:myAssistant");
            context.start();
        })
                .as("the body fallback only applies to single-parameter methods, "
                    + "so an un-annotated parameter in a multi-parameter method must fail route startup")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("has no binding annotation")
                .hasMessageContaining("each parameter must be annotated with @UserMessage, @V, or @MemoryId");
    }

    @Test
    void testDuplicateTemplateVariableFails() {
        context.getRegistry().bind("myAssistant", MockAiServices.create(DuplicateVariableService.class));

        assertThatThrownBy(() -> {
            addRouteTo("langchain4j-service:myAssistant");
            context.start();
        })
                .as("two @V('context') parameters would silently feed from the same variables map key, "
                    + "so route startup must fail instead")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("multiple parameters annotated with @V(\"context\")")
                .hasMessageContaining("Template variable names must be unique");
    }

    @Test
    void testMultipleMemoryIdParametersFails() {
        context.getRegistry().bind("myAssistant", MockAiServices.create(DuplicateMemoryIdService.class));

        assertThatThrownBy(() -> {
            addRouteTo("langchain4j-service:myAssistant");
            context.start();
        })
                .as("two @MemoryId parameters would silently feed from the same header, "
                    + "so route startup must fail like the @UserMessage duplicate")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("has multiple @MemoryId parameters");
    }

    @Test
    void testEmptyTemplateVariableNameFails() {
        context.getRegistry().bind("myAssistant", MockAiServices.create(EmptyVariableNameService.class));

        assertThatThrownBy(() -> {
            addRouteTo("langchain4j-service:myAssistant");
            context.start();
        })
                .as("an empty @V name leaves nothing to name the parameter by, so the error falls back to its "
                    + "position, which must be stated as a position and counted from one")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("Parameter at position 2")
                .hasMessageContaining("has an empty @V template variable name");
    }

    @Test
    void testMissingServiceNameFails() {
        assertThatThrownBy(() -> {
            addRouteTo("langchain4j-service:?method=chat");
            context.start();
        })
                .as("an endpoint URI without a service name must be rejected with a clear message "
                    + "at endpoint creation, not fail later with a NullPointerException")
                .isInstanceOf(RuntimeCamelException.class)
                .hasMessageContaining("serviceName must be specified");
    }

    @Test
    void testPlainBeanMethodResolvedThroughClassHierarchy() throws Exception {
        // A plain bean: the method is declared on an interface of a superclass, and the parameter
        // annotations live on that interface rather than on the registered class
        context.getRegistry().bind("myAssistant", new ConcreteGreetingBean());

        addRouteTo("langchain4j-service:myAssistant");
        context.start();

        String result = template.requestBody("direct:start", "Hello", String.class);

        assertThat(result)
                .as("the method inherited through the superclass must be found and invoked, with "
                    + "Serializable and the methodless marker contributing no candidate")
                .isEqualTo("greetings Hello");
    }

    @Test
    void testNonPublicInterfaceIsInvokable() throws Exception {
        context.getRegistry().bind("myAssistant", MockAiServices.create(NonPublicService.class));

        addRouteTo("langchain4j-service:myAssistant");
        context.start();

        String result = template.requestBody("direct:start", "Hello", String.class);

        assertThat(result)
                .as("a package-private AI Service interface must work: the invocation goes through Camel's "
                    + "bean binding, which grants access before calling, so there is nothing to reject")
                .isEqualTo("response from chat");
    }

    @Test
    void testMultipleMethodsWithoutMethodParameterFails() {
        context.getRegistry().bind("myAssistant", new MultiInterfaceBean());

        assertThatThrownBy(() -> {
            addRouteTo("langchain4j-service:myAssistant");
            context.start();
        })
                .as("a bean exposing the methods of two interfaces is ambiguous, so route startup must fail, "
                    + "ask for the method parameter and list what is available")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("has multiple methods")
                .hasMessageContaining("Specify method parameter")
                .hasMessageContaining("analyze")
                .hasMessageContaining("chat");
    }

    @Test
    void testMultipleMethodsWithMethodParameterSucceeds() throws Exception {
        context.getRegistry().bind("myAssistant", new MultiInterfaceBean());

        addRouteTo("langchain4j-service:myAssistant?method=analyze");
        context.start();

        String result = template.requestBody("direct:start", "Hello", String.class);

        assertThat(result)
                .as("naming the method resolves the ambiguity, whichever interface declares it")
                .isEqualTo("analyze");
    }

    @Test
    void testBeanWithNoInvokableMethodFails() {
        context.getRegistry().bind("myAssistant", new Object());

        assertThatThrownBy(() -> {
            addRouteTo("langchain4j-service:myAssistant");
            context.start();
        })
                .as("a bean exposing nothing but the java.lang.Object methods cannot be an AI Service, "
                    + "so route startup must fail with a clear message")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("has no method to invoke");
    }

    @Test
    void testStaticMethodIsNotACandidateButDefaultMethodIs() {
        context.getRegistry().bind("myAssistant", MockAiServices.create(DefaultAndStaticMethodService.class));

        assertThatThrownBy(() -> {
            addRouteTo("langchain4j-service:myAssistant");
            context.start();
        })
                .as("a default method is callable, so it is a candidate and makes the method parameter required; "
                    + "the static method is never a candidate, so it must not be listed")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("has multiple methods")
                .hasMessageContaining("Available methods: chat, helper")
                // the names are sorted, so 'contains' alone would still pass on "chat, helper, util"
                .hasMessageNotContaining("util");
    }

    @Test
    void testStreamingReturnTypeIsRejected() {
        context.getRegistry().bind("myAssistant", MockAiServices.create(StreamingService.class));

        assertThatThrownBy(() -> {
            addRouteTo("langchain4j-service:myAssistant");
            context.start();
        })
                .as("nothing subscribes to a TokenStream the producer would set as the body, so the route would carry "
                    + "an object that never emits; the return type is known at startup and must fail there")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("Streaming responses are not supported")
                .hasMessageContaining(TokenStream.class.getName());
    }

    @Test
    void testDefaultMethodServiceResolvedWithMethodParameter() throws Exception {
        context.getRegistry().bind("myAssistant", MockAiServices.create(DefaultAndStaticMethodService.class));

        addRouteTo("langchain4j-service:myAssistant?method=chat");
        context.start();

        String result = template.requestBody("direct:start", "Hello", String.class);

        assertThat(result)
                .as("naming the method selects it next to the default one")
                .isEqualTo("response from chat");
    }

    @Test
    void testAgentServiceMethodSelectedAmongFrameworkMethods() throws Exception {
        // AgenticServices builds the agent over the user's agent interface plus its own framework
        // interfaces, so the framework methods sit next to the agent method and must not get in the way
        context.getRegistry().bind("myAgent", MockAiServices.create(
                MyAgent.class, InternalAgent.class, AgenticScopeOwner.class, AgenticScopeAccess.class));

        addRouteTo("langchain4j-service:myAgent?method=writeStory");
        context.start();

        String result = template.requestBodyAndHeader("direct:start", "ignored",
                LangChain4jServiceHeaders.VARIABLES, Map.of("topic", "a camel"), String.class);

        assertThat(result)
                .as("naming the agent method must select it among the methods the LangChain4j framework "
                    + "interfaces add, and its @V parameter annotation must still be found")
                .isEqualTo("response from writeStory");
    }

    @Test
    void testLazyStartProducerDefersResolutionFailureToFirstMessage() throws Exception {
        // Do NOT register any service as 'missing'

        addRouteTo("langchain4j-service:missing?lazyStartProducer=true");
        context.start();

        assertThat(context.getRouteController().getRouteStatus(context.getRoutes().get(0).getRouteId()).isStarted())
                .as("with lazyStartProducer=true the route must start even though the AI Service "
                    + "cannot be resolved yet")
                .isTrue();

        assertThatThrownBy(() -> template.requestBody("direct:start", "Hello", String.class))
                .as("the deferred resolution failure must then surface on the first message, "
                    + "where routing error handlers can deal with it")
                .isInstanceOf(CamelExecutionException.class)
                .rootCause()
                .isInstanceOf(NoSuchBeanException.class)
                .hasMessageContaining("No bean could be found in the registry for: missing");
    }

    @Test
    void testConflictingUserMessageAndMemoryIdFails() {
        context.getRegistry().bind("myAssistant", MockAiServices.create(MemoryIdAndUserMessageService.class));

        assertThatThrownBy(() -> {
            addRouteTo("langchain4j-service:myAssistant");
            context.start();
        })
                .as("every pair of binding annotations must be rejected, not only @UserMessage with @V")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("has conflicting annotations")
                .hasMessageContaining("@UserMessage and @MemoryId");
    }

    @Test
    void testConflictingVariableAndMemoryIdFails() {
        context.getRegistry().bind("myAssistant", MockAiServices.create(MemoryIdAndVariableService.class));

        assertThatThrownBy(() -> {
            addRouteTo("langchain4j-service:myAssistant");
            context.start();
        })
                .as("@V and @MemoryId bind from different places, so carrying both is ambiguous")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("has conflicting annotations")
                .hasMessageContaining("@V and @MemoryId");
    }

    @Test
    void testAllThreeConflictingAnnotationsAreAllNamed() {
        context.getRegistry().bind("myAssistant", MockAiServices.create(AllThreeAnnotationsService.class));

        assertThatThrownBy(() -> {
            addRouteTo("langchain4j-service:myAssistant");
            context.start();
        })
                .as("the error must list every annotation it found, so the author can see all of them at once")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("@UserMessage and @V and @MemoryId");
    }

    @Test
    void testParameterIsNamedWhenTheClassCarriesParameterNames() {
        context.getRegistry().bind("myAssistant", new NamedParameterBean());

        assertThatThrownBy(() -> {
            addRouteTo("langchain4j-service:myAssistant");
            context.start();
        })
                .as("a compiled class keeps its parameter names, so the error must name the parameter rather than "
                    + "fall back to its position as it does for a proxy")
                .isInstanceOf(FailedToStartRouteException.class)
                .hasMessageContaining("Parameter 'context'")
                .hasMessageContaining("has an empty @V template variable name");
    }
}
