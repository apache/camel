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
package org.apache.camel.component.ai.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenAiMessagesTest {

    @Test
    void shouldRenderMessagesInTheSemanticConventionsShape() {
        String json = new GenAiMessages()
                .text(GenAiMessages.SYSTEM, "Be brief")
                .text(GenAiMessages.USER, "Where is order A-1?")
                .text(GenAiMessages.ASSISTANT, null)
                .toolCall("call-1", "getOrderStatus", "{\"orderId\":\"A-1\"}")
                .toolResult("call-1", "SHIPPED")
                .toJson(100);

        assertThat(json).isEqualTo("["
                                   + "{\"role\":\"system\",\"parts\":[{\"type\":\"text\",\"content\":\"Be brief\"}]},"
                                   + "{\"role\":\"user\",\"parts\":[{\"type\":\"text\",\"content\":\"Where is order A-1?\"}]},"
                                   + "{\"role\":\"assistant\",\"parts\":[{\"type\":\"tool_call\",\"id\":\"call-1\","
                                   + "\"name\":\"getOrderStatus\",\"arguments\":{\"orderId\":\"A-1\"}}]},"
                                   + "{\"role\":\"tool\",\"parts\":[{\"type\":\"tool_call_response\",\"id\":\"call-1\","
                                   + "\"response\":\"SHIPPED\"}]}"
                                   + "]");
    }

    @Test
    void shouldSetFinishReasonAndReasoningOnOutput() {
        String json = new GenAiMessages()
                .text(GenAiMessages.ASSISTANT, "Shipped")
                .reasoning("The tool said SHIPPED")
                .finishReason("STOP")
                .toJson(100);

        assertThat(json).isEqualTo("[{\"role\":\"assistant\",\"parts\":["
                                   + "{\"type\":\"reasoning\",\"content\":\"The tool said SHIPPED\"},"
                                   + "{\"type\":\"text\",\"content\":\"Shipped\"}],\"finish_reason\":\"stop\"}]");
    }

    @Test
    void shouldCutEveryTextAndKeepTheJsonValid() {
        String json = new GenAiMessages()
                .text(GenAiMessages.USER, "0123456789")
                .toolCall(null, "lookup", "{\"query\":\"abcdefghij\"}")
                .toJson(4);

        assertThat(json).contains("\"content\":\"0123...[truncated]\"")
                .contains("\"query\":\"abcd...[truncated]\"");
    }

    @Test
    void shouldNotSplitASurrogatePair() {
        // "ab" followed by an emoji, which is two chars (a surrogate pair)
        String text = "ab\uD83D\uDE00cd";

        assertThat(GenAiMessages.truncate(text, 3)).isEqualTo("ab...[truncated]");
        assertThat(GenAiMessages.truncate(text, 4)).isEqualTo("ab\uD83D\uDE00...[truncated]");
    }

    @Test
    void shouldKeepToolArgumentsThatAreNotJsonAsText() {
        String json = new GenAiMessages().toolCall("1", "lookup", "not json").toJson(100);

        assertThat(json).contains("\"arguments\":\"not json\"");
    }

    @Test
    void shouldRenderSystemInstructions() {
        assertThat(GenAiMessages.systemInstructions("Be brief", 100))
                .isEqualTo("[{\"type\":\"text\",\"content\":\"Be brief\"}]");
    }
}
