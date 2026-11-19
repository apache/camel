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
import java.util.concurrent.atomic.AtomicBoolean;

import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.apache.camel.component.langchain4j.service.support.RecordingChatModel;
import org.apache.camel.component.langchain4j.service.support.ToolCallingChatModel;
import org.apache.camel.component.langchain4j.service.support.WeatherReport;
import org.apache.camel.component.langchain4j.service.support.WeatherTool;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the AI Service ended up asking the model, for every shape {@code @UserMessage} can take.
 * <p>
 * These use their own {@link RecordingChatModel} rather than the OpenAI mock the conformance tests run against, because
 * the assertion here is the prompt itself. {@code ChatRequest.messages()} hands it over as typed objects already
 * rendered, so a failure reads as a diff on the prompt. Going through the mock server would mean naming the expected
 * prompt in the expectation to get a reply at all, which puts the expected value on the wrong side of the test.
 * <p>
 * The component never renders anything: it binds the body and the headers, and LangChain4j substitutes. That is exactly
 * what makes these worth asserting end to end, since a binding mistake shows up as a prompt that is wrong rather than
 * as an error.
 */
class LangChain4jServicePromptRenderingTest extends LangChain4jServiceTestSupport {

    public interface ItTemplateAssistant {
        @UserMessage("Tell me about {{it}}")
        String chat(String topic);
    }

    public interface VariableOnlyTemplateAssistant {
        @UserMessage("Compare {{first}} and {{second}}")
        String compare(@V("first") String first, @V("second") String second);
    }

    public interface FixedPromptAssistant {
        @UserMessage("Give me a tip")
        String tip();
    }

    public interface SystemAndUserTemplateAssistant {
        @SystemMessage("You are terse")
        @UserMessage("Tell me about {{it}}")
        String chat(String topic);
    }

    public interface AnnotatedValueAssistant {
        String chat(@UserMessage("ignored on a parameter") String text);
    }

    public interface ReportAssistant {
        WeatherReport report(@UserMessage String question);
    }

    @Test
    void methodLevelTemplateBindsTheBodyIntoIt() throws Exception {
        RecordingChatModel model = new RecordingChatModel("ok");
        context.getRegistry().bind("assistant", AiServices.builder(ItTemplateAssistant.class)
                .chatModel(model)
                .build());
        startWithRouteTo("langchain4j-service:assistant");

        template.requestBody("direct:start", "camels", String.class);

        assertThat(model.lastUserMessage())
                .as("the body binds to the single un-annotated parameter, and LangChain4j substitutes it for {{it}}")
                .isEqualTo("Tell me about camels");
    }

    @Test
    void methodLevelTemplateSubstitutesEveryTemplateVariable() throws Exception {
        RecordingChatModel model = new RecordingChatModel("ok");
        context.getRegistry().bind("assistant", AiServices.builder(VariableOnlyTemplateAssistant.class)
                .chatModel(model)
                .build());
        startWithRouteTo("langchain4j-service:assistant?method=compare");

        template.requestBodyAndHeader("direct:start", "this body goes nowhere",
                LangChain4jServiceHeaders.VARIABLES, Map.of("first", "camels", "second", "llamas"),
                String.class);

        assertThat(model.lastUserMessage())
                .as("both @V parameters bind from the variables header and LangChain4j substitutes both placeholders")
                .isEqualTo("Compare camels and llamas");
    }

    @Test
    void theBodyIsIgnoredWhenNoParameterBindsIt() throws Exception {
        RecordingChatModel model = new RecordingChatModel("ok");
        context.getRegistry().bind("assistant", AiServices.builder(VariableOnlyTemplateAssistant.class)
                .chatModel(model)
                .build());
        startWithRouteTo("langchain4j-service:assistant?method=compare");

        template.requestBodyAndHeader("direct:start", null,
                LangChain4jServiceHeaders.VARIABLES, Map.of("first", "camels", "second", "llamas"),
                String.class);

        assertThat(model.lastUserMessage())
                .as("when every parameter binds from a header the body never reaches the model, and a null body is "
                    + "therefore fine: nothing expects it")
                .isEqualTo("Compare camels and llamas");
    }

    @Test
    void zeroParameterMethodSendsItsFixedPrompt() throws Exception {
        RecordingChatModel model = new RecordingChatModel("drink water");
        context.getRegistry().bind("assistant", AiServices.builder(FixedPromptAssistant.class)
                .chatModel(model)
                .build());
        startWithRouteTo("langchain4j-service:assistant");

        template.requestBody("direct:start", "ignored", String.class);

        assertThat(model.lastUserMessage())
                .as("a zero-parameter method sends its own prompt and the body is discarded")
                .isEqualTo("Give me a tip");
    }

    @Test
    void systemMessageIsSentAlongsideTheRenderedUserMessage() throws Exception {
        RecordingChatModel model = new RecordingChatModel("ok");
        context.getRegistry().bind("assistant", AiServices.builder(SystemAndUserTemplateAssistant.class)
                .chatModel(model)
                .build());
        startWithRouteTo("langchain4j-service:assistant");

        template.requestBody("direct:start", "camels", String.class);

        assertThat(model.lastSystemMessage())
                .as("a method-level @SystemMessage is LangChain4j's to emit; the component must not swallow it")
                .isEqualTo("You are terse");
        assertThat(model.lastUserMessage())
                .as("the user message is still rendered from the body, alongside the system message")
                .isEqualTo("Tell me about camels");
    }

    @Test
    void userMessageValueOnAParameterChangesNothing() throws Exception {
        RecordingChatModel model = new RecordingChatModel("ok");
        context.getRegistry().bind("assistant", AiServices.builder(AnnotatedValueAssistant.class)
                .chatModel(model)
                .build());
        startWithRouteTo("langchain4j-service:assistant");

        template.requestBody("direct:start", "the real message", String.class);

        assertThat(model.lastUserMessage())
                .as("on a parameter @UserMessage is only a marker: its value is not a template and the producer never "
                    + "reads it, so the body is still what the model sees")
                .isEqualTo("the real message");
    }

    @Test
    void toolRoundTripFeedsTheToolResultBackToTheModel() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean();
        ToolCallingChatModel model
                = new ToolCallingChatModel("getWeather", "{\"location\": \"Paris\"}", "It is sunny in Paris");
        context.getRegistry().bind("assistant", AiServices.builder(ItTemplateAssistant.class)
                .chatModel(model)
                .tools(new WeatherTool(invoked))
                .build());
        startWithRouteTo("langchain4j-service:assistant");

        template.requestBody("direct:start", "Paris", String.class);

        assertThat(model.advertisedToolNames())
                .as("the tool the AI Service was built with must be advertised to the model on the first turn")
                .contains("getWeather");
        assertThat(model.rounds())
                .as("a completed tool round-trip is exactly two turns: ask for the tool, then answer")
                .isEqualTo(2);
        assertThat(model.toolResult())
                .as("the tool's return value must come back to the model as the tool result of the second turn")
                .contains("Sunny");
    }

    @Test
    void structuredOutputBecomesATypedBody() throws Exception {
        RecordingChatModel model = new RecordingChatModel(
                "{\"location\": \"Paris\", \"temperatureCelsius\": 22, \"summary\": \"Sunny\"}");
        context.getRegistry().bind("assistant", AiServices.builder(ReportAssistant.class)
                .chatModel(model)
                .build());
        startWithRouteTo("langchain4j-service:assistant?method=report");

        WeatherReport report = template.requestBody("direct:start", "Weather in Paris?", WeatherReport.class);

        assertThat(model.lastUserMessage())
                .as("LangChain4j appends its own format instruction for a POJO return type; if this fails, the "
                    + "instruction shows the exact JSON shape the parser wants")
                .containsIgnoringCase("json");
        assertThat(report)
                .as("a non-String return type must reach the body as the parsed object, not as text")
                .isNotNull()
                .extracting(WeatherReport::getLocation, WeatherReport::getSummary,
                        WeatherReport::getTemperatureCelsius)
                .containsExactly("Paris", "Sunny", 22);
    }
}
