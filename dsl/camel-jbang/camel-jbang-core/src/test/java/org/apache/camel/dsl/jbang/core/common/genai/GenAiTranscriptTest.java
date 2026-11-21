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
package org.apache.camel.dsl.jbang.core.common.genai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.component.ai.observability.GenAiAttributes;
import org.apache.camel.component.ai.observability.GenAiMessages;
import org.apache.camel.dsl.jbang.core.common.genai.GenAiTranscript.Kind;
import org.apache.camel.dsl.jbang.core.common.genai.GenAiTranscript.Part;
import org.apache.camel.dsl.jbang.core.common.genai.GenAiTranscript.Turn;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenAiTranscriptTest {

    /** The input of the second model call of a run: the user question, the tool call and its result. */
    private static final String SECOND_CALL_INPUT = new GenAiMessages()
            .text(GenAiMessages.SYSTEM, "You are a support agent.")
            .text(GenAiMessages.USER, "What is the status of order A-17?")
            .text(GenAiMessages.ASSISTANT, null)
            .toolCall("call-1", "lookupOrder", "{\"orderId\":\"A-17\"}")
            .toolResult("call-1", "{\"orderId\":\"A-17\",\"status\":\"DELIVERED\"}")
            .toJson(8192);
    private static final String SECOND_CALL_OUTPUT = new GenAiMessages()
            .text(GenAiMessages.ASSISTANT, "Order A-17 was delivered.")
            .finishReason("STOP")
            .toJson(8192);

    @Test
    void modelCallShowsItsInputAndOutput() {
        SpanEntry chat = span("chat2", "agent", attrs("chat",
                GenAiAttributes.INPUT_MESSAGES, SECOND_CALL_INPUT,
                GenAiAttributes.OUTPUT_MESSAGES, SECOND_CALL_OUTPUT));

        List<Turn> turns = GenAiTranscript.of(chat, List.of(chat)).turns();

        assertThat(turns).extracting(Turn::role).containsExactly("system", "user", "assistant", "tool", "assistant");
        Part call = turns.get(2).parts().get(0);
        assertThat(call.kind()).isEqualTo(Kind.TOOL_CALL);
        assertThat(call.toolName()).isEqualTo("lookupOrder");
        assertThat(call.text()).isEqualTo("{\"orderId\":\"A-17\"}");
        // the result names its tool by the id of the call
        Part result = turns.get(3).parts().get(0);
        assertThat(result.kind()).isEqualTo(Kind.TOOL_RESULT);
        assertThat(result.toolName()).isEqualTo("lookupOrder");
        assertThat(turns.get(4).finishReason()).isEqualTo("stop");
    }

    @Test
    void agentRunShowsItsLastModelCall() {
        SpanEntry agent = span("agent", "route", attrs("invoke_agent",
                GenAiAttributes.INPUT_MESSAGES,
                new GenAiMessages().text(GenAiMessages.USER, "What is the status of order A-17?").toJson(8192)));
        SpanEntry chat1 = span("chat1", "agent", attrs("chat",
                GenAiAttributes.INPUT_MESSAGES,
                new GenAiMessages().text(GenAiMessages.USER, "What is the status of order A-17?").toJson(8192)));
        SpanEntry tool = span("tool", "agent", attrs("execute_tool",
                GenAiAttributes.TOOL_CALL_ARGUMENTS, "{\"orderId\":\"A-17\"}"));
        // nested one level deeper, under a Camel span, and started last
        SpanEntry step = span("step", "agent", Map.of("camel.uri", "openai:chat-completion"));
        SpanEntry chat2 = new SpanEntry(
                "trace-1", "chat2", "step", "chat", "CLIENT", "OK", 300, 400, 1, "r", null,
                "camel", attrs("chat",
                        GenAiAttributes.INPUT_MESSAGES, SECOND_CALL_INPUT,
                        GenAiAttributes.OUTPUT_MESSAGES, SECOND_CALL_OUTPUT));

        GenAiTranscript transcript = GenAiTranscript.of(agent, List.of(agent, chat1, tool, step, chat2));

        assertThat(transcript.turns()).hasSize(5);
        assertThat(transcript.turns().get(4).parts().get(0).text()).isEqualTo("Order A-17 was delivered.");
    }

    @Test
    void agentRunWithoutModelCallsShowsItsOwnMessages() {
        SpanEntry agent = span("agent", "route", attrs("invoke_agent",
                GenAiAttributes.SYSTEM_INSTRUCTIONS, GenAiMessages.systemInstructions("Be brief.", 8192),
                GenAiAttributes.INPUT_MESSAGES, new GenAiMessages().text(GenAiMessages.USER, "Hi").toJson(8192)));

        List<Turn> turns = GenAiTranscript.of(agent, List.of(agent)).turns();

        assertThat(turns).extracting(Turn::role).containsExactly("system", "user");
        assertThat(turns.get(0).parts().get(0).text()).isEqualTo("Be brief.");
    }

    @Test
    void systemInstructionsAreNotRepeatedWhenTheInputHasASystemMessage() {
        SpanEntry chat = span("chat", "", attrs("chat",
                GenAiAttributes.SYSTEM_INSTRUCTIONS, GenAiMessages.systemInstructions("You are a support agent.", 8192),
                GenAiAttributes.INPUT_MESSAGES, SECOND_CALL_INPUT));

        assertThat(GenAiTranscript.of(chat, List.of(chat)).turns())
                .filteredOn(turn -> "system".equals(turn.role())).hasSize(1);
    }

    @Test
    void toolCallShowsItsArgumentsAndResult() {
        SpanEntry tool = span("tool", "agent", attrs("execute_tool",
                GenAiAttributes.TOOL_NAME, "lookupOrder",
                GenAiAttributes.TOOL_CALL_ARGUMENTS, "{\"orderId\":\"A-17\"}",
                GenAiAttributes.TOOL_CALL_RESULT, "DELIVERED"));

        List<Turn> turns = GenAiTranscript.of(tool, List.of(tool)).turns();

        assertThat(turns).extracting(Turn::role).containsExactly("assistant", "tool");
        assertThat(turns).allSatisfy(turn -> assertThat(turn.parts().get(0).toolName()).isEqualTo("lookupOrder"));
        assertThat(turns.get(1).parts().get(0).text()).isEqualTo("DELIVERED");
    }

    @Test
    void spanWithoutMessagesHasNoContent() {
        SpanEntry basic = span("chat", "", attrs("chat", GenAiAttributes.REQUEST_MODEL, "llama3.2"));
        SpanEntry route = span("route", "", Map.of("camel.uri", "timer:ask"));

        assertThat(GenAiSpans.isGenAiSpan(basic)).isTrue();
        assertThat(GenAiTranscript.hasContent(basic)).isFalse();
        assertThat(GenAiTranscript.of(basic, List.of(basic)).isEmpty()).isTrue();
        assertThat(GenAiSpans.isGenAiSpan(route)).isFalse();
        assertThat(GenAiTranscript.of(route, List.of(route)).isEmpty()).isTrue();
    }

    @Test
    void messageWithoutARoleStillHasAHeading() {
        String input = "[{\"role\":\"\",\"parts\":[{\"type\":\"text\",\"content\":\"hello\"}]},"
                       // a part without a message around it, as in gen_ai.system_instructions
                       + "{\"type\":\"text\",\"content\":\"instructions\"}]";
        SpanEntry chat = span("chat", "", attrs("chat", GenAiAttributes.INPUT_MESSAGES, input));

        assertThat(GenAiTranscript.of(chat, List.of(chat)).toMarkdown())
                .contains("### Message\n\nhello")
                .contains("### System\n\ninstructions");
    }

    @Test
    void textThatIsNotJsonIsShownAsItIs() {
        assertThat(GenAiTranscript.parseMessages("not json")).singleElement()
                .satisfies(turn -> assertThat(turn.parts().get(0).text()).isEqualTo("not json"));
        assertThat(GenAiTranscript.parseMessages(null)).isEmpty();
    }

    @Test
    void markdownHasAHeadingPerMessageAndToolDataAsCode() {
        SpanEntry chat = span("chat2", "agent", attrs("chat",
                GenAiAttributes.INPUT_MESSAGES, SECOND_CALL_INPUT,
                GenAiAttributes.OUTPUT_MESSAGES, SECOND_CALL_OUTPUT));

        String markdown = GenAiTranscript.of(chat, List.of(chat)).toMarkdown();

        assertThat(markdown)
                .contains("### System\n\nYou are a support agent.")
                .contains("### User\n\nWhat is the status of order A-17?")
                .contains("Calls **lookupOrder**\n\n```json\n{\n  \"orderId\": \"A-17\"\n}\n```")
                .contains("### Tool lookupOrder\n\n```json\n")
                .contains("### Assistant · stop\n\nOrder A-17 was delivered.");
    }

    @Test
    void markdownMarksCutTextsAndKeepsFencesClosed() {
        String cut = new GenAiMessages().text(GenAiMessages.USER, "a".repeat(20)).toJson(10);
        SpanEntry chat = span("chat", "", attrs("chat",
                GenAiAttributes.INPUT_MESSAGES, cut,
                GenAiAttributes.OUTPUT_MESSAGES, new GenAiMessages().text(GenAiMessages.ASSISTANT, null)
                        .toolCall(null, "run", "```shell\nls\n```").toJson(8192)));

        String markdown = GenAiTranscript.of(chat, List.of(chat)).toMarkdown();

        assertThat(markdown).contains("aaaaaaaaaa … *(truncated)*").contains("````\n```shell\nls\n```\n````");
    }

    private static Map<String, Object> attrs(String operation, String... keyValues) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(GenAiAttributes.OPERATION_NAME, operation);
        for (int i = 0; i < keyValues.length; i += 2) {
            attrs.put(keyValues[i], keyValues[i + 1]);
        }
        return attrs;
    }

    private static SpanEntry span(String spanId, String parentSpanId, Map<String, Object> attributes) {
        long start = switch (spanId) {
            case "chat1" -> 100;
            case "tool" -> 200;
            default -> 250;
        };
        return new SpanEntry(
                "trace-1", spanId, parentSpanId, spanId, "CLIENT", "OK", start, start + 50, 1, "r", null,
                "camel", attributes);
    }
}
