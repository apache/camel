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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.LlmClient;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPanelHistoryCompactionTest {

    private static final String BIG = "x".repeat(5000);

    /** One question answered with a tool call: user, assistant(tool call), tool result, assistant(answer). */
    private static List<LlmClient.Message> turn(String question, String toolResult) {
        List<LlmClient.Message> turn = new ArrayList<>();
        turn.add(LlmClient.Message.user(question));
        turn.add(LlmClient.Message.assistantWithToolCalls(null,
                List.of(new LlmClient.ToolCall("call-" + question, "tui_get_log", new JsonObject()))));
        turn.add(LlmClient.Message.toolResults(List.of(new LlmClient.ToolResult("call-" + question, toolResult))));
        turn.add(LlmClient.Message.assistantWithToolCalls("answer to " + question, List.of()));
        return turn;
    }

    private static String toolResultContent(LlmClient.Message message) {
        return message.toolResults().get(0).content();
    }

    @Test
    void truncatesOversizedToolResultsButKeepsShortOnes() {
        String small = "ok";
        assertSame(small, AiPanel.truncateToolResult(small));

        String huge = "y".repeat(AiPanel.MAX_TOOL_RESULT_CHARS + 1234);
        String truncated = AiPanel.truncateToolResult(huge);

        assertTrue(truncated.startsWith("y".repeat(AiPanel.MAX_TOOL_RESULT_CHARS)));
        assertTrue(truncated.contains("[truncated, 1234 more characters"));
    }

    @Test
    void compactsToolResultsOfOlderTurnsButKeepsThePreviousTurnIntact() {
        List<LlmClient.Message> history = new ArrayList<>();
        history.addAll(turn("q1", BIG));
        history.addAll(turn("q2", BIG));
        history.addAll(turn("q3", BIG));

        AiPanel.compactHistory(history, 20, 400);

        assertEquals(12, history.size());
        String first = toolResultContent(history.get(2));
        assertTrue(first.startsWith("x".repeat(400)));
        assertTrue(first.contains("[earlier result compacted"));
        // the previous turn (q2) and the current turn (q3) keep their full results
        assertEquals(BIG, toolResultContent(history.get(6)));
        assertEquals(BIG, toolResultContent(history.get(10)));
        // structure is untouched: the tool call ids still match their results
        assertEquals("call-q1", history.get(2).toolResults().get(0).toolCallId());
    }

    @Test
    void forcedCompactionAlsoShrinksThePreviousTurn() {
        List<LlmClient.Message> history = new ArrayList<>();
        history.addAll(turn("q1", BIG));
        history.addAll(turn("q2", BIG));

        AiPanel.compactHistory(history, 20, 400, false);

        assertTrue(toolResultContent(history.get(2)).contains("[earlier result compacted"));
        assertTrue(toolResultContent(history.get(6)).contains("[earlier result compacted"));
        assertEquals(8, history.size());
    }

    @Test
    void dropsWholeOldestTurnsBeyondTheLimit() {
        List<LlmClient.Message> history = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            history.addAll(turn("q" + i, "r" + i));
        }

        AiPanel.compactHistory(history, 3, 400);

        assertEquals(12, history.size());
        assertEquals("q3", history.get(0).content());
        assertEquals("user", history.get(0).role());
        assertEquals("answer to q5", history.get(11).content());
    }

    @Test
    void singleTurnAndEmptyHistoryAreLeftAlone() {
        List<LlmClient.Message> history = new ArrayList<>(turn("q1", BIG));

        AiPanel.compactHistory(history, 20, 400);
        AiPanel.compactHistory(new ArrayList<>(), 20, 400);
        AiPanel.compactHistory(null, 20, 400);

        assertEquals(BIG, toolResultContent(history.get(2)));
    }
}
