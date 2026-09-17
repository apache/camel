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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPanelHistoryCompactionTest {

    private static final String BIG = "x".repeat(5000);

    /** One question answered with a tool call: user, assistant(tool call), tool result, assistant(answer). */
    private static List<LlmClient.Message> turn(String question, String toolResult) {
        List<LlmClient.Message> turn = new ArrayList<>();
        turn.add(LlmClient.Message.user(question));
        turn.add(LlmClient.Message.assistantWithToolCalls(null,
                List.of(new LlmClient.ToolCall("call-" + question, "camel_get_log", new JsonObject()))));
        turn.add(LlmClient.Message.toolResults(List.of(new LlmClient.ToolResult("call-" + question, toolResult))));
        turn.add(LlmClient.Message.assistantWithToolCalls("answer to " + question, List.of()));
        return turn;
    }

    private static String toolResultContent(LlmClient.Message message) {
        return message.toolResults().get(0).content();
    }

    @Test
    void hostedEndpointsCompactAfterEveryTurn() {
        assertTrue(AiPanel.shouldCompactAfterTurn(false, 0));
        assertTrue(AiPanel.shouldCompactAfterTurn(false, 1_000));
    }

    @Test
    void localEndpointsDeferCompactionUntilTheHistoryBudgetIsExceeded() {
        long budgetChars = (long) AiPanel.LOCAL_HISTORY_BUDGET_TOKENS * 4;
        // an untouched history keeps the local server's KV cache valid, so nothing is rewritten while it fits
        assertFalse(AiPanel.shouldCompactAfterTurn(true, 0));
        assertFalse(AiPanel.shouldCompactAfterTurn(true, budgetChars));
        // once the history would crowd the context window, compaction resumes as for hosted endpoints
        assertTrue(AiPanel.shouldCompactAfterTurn(true, budgetChars + 4_000));
    }

    @Test
    void measuredPromptDecidesForLocalEndpointsWhenAvailable() {
        int budget = AiPanel.compactionBudgetTokens(true, 32_768);
        assertEquals(16_384, budget);
        // the estimate says the history is small, Ollama measured a prompt over the budget: compact
        assertTrue(AiPanel.shouldCompactAfterTurn(true, 1_000, 24_800, budget));
        // the estimate says big, the measurement says fine: the measurement wins
        assertFalse(AiPanel.shouldCompactAfterTurn(true, 100_000, 12_000, budget));
        // nothing measured yet: the estimate decides
        assertTrue(AiPanel.shouldCompactAfterTurn(true, 100_000, 0, budget));
        assertFalse(AiPanel.shouldCompactAfterTurn(true, 1_000, 0, budget));
        // hosted endpoints compact regardless
        assertTrue(AiPanel.shouldCompactAfterTurn(false, 0, 0, budget));
    }

    @Test
    void budgetIsHalfTheWindowCappedAtTheMaximum() {
        assertEquals(AiPanel.LOCAL_HISTORY_BUDGET_TOKENS, AiPanel.compactionBudgetTokens(true, 0));
        assertEquals(AiPanel.LOCAL_HISTORY_BUDGET_TOKENS, AiPanel.compactionBudgetTokens(false, 262_144));
        assertEquals(16_384, AiPanel.compactionBudgetTokens(true, 32_768));
        assertEquals(32_768, AiPanel.compactionBudgetTokens(true, 65_536));
        // a 256k window adopted from another client is used, but the panel still budgets as if it were 64k
        assertEquals(32_768, AiPanel.compactionBudgetTokens(true, 262_144));
    }

    @Test
    void localCompactionShrinksThePreviousTurnAndDropsOldTurnsUntilHalfTheBudget() {
        List<LlmClient.Message> history = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            history.addAll(turn("q" + i, BIG));
        }
        // six turns of ~5,000 chars of tool results each; budget 4,000 tokens -> target ~2,000 tokens (8,000 chars)
        AiPanel.compactLocalHistory(history, 4_000);

        // the previous turn's tool result was cut too, unlike the per-turn compaction of hosted endpoints
        for (LlmClient.Message m : history) {
            if (m.toolResults() != null) {
                assertTrue(m.toolResults().get(0).content().length() < 500, "tool results are cut");
            }
        }
        assertTrue(AiPanel.estimateTokens(AiPanel.historyChars(history)) <= 2_000);
        // the newest turn is always kept
        assertEquals("answer to q6", history.get(history.size() - 1).content());
    }

    @Test
    void dropOldestTurnRemovesTheFirstQuestionWithItsAnswer() {
        List<LlmClient.Message> history = new ArrayList<>();
        history.addAll(turn("q1", "r1"));
        history.addAll(turn("q2", "r2"));
        AiPanel.dropOldestTurn(history);
        assertEquals(4, history.size());
        assertEquals("q2", history.get(0).content());
        // a single turn is never dropped by the loop in compactLocalHistory; the helper alone leaves it too
        AiPanel.dropOldestTurn(history);
        assertEquals(4, history.size());
    }

    @Test
    void compactionNoticeExplainsTheRePrefillCostLocally() {
        String local = AiPanel.describeCompaction(true, 24_800, 8_100, true, 650);
        assertTrue(local.startsWith("History compacted automatically: ~24.8k -> ~8.1k tokens."), local);
        assertTrue(local.contains("about 12s at 650 tok/s, measured"), local);
        String manual = AiPanel.describeCompaction(false, 24_800, 8_100, true, 0);
        assertTrue(manual.startsWith("Compacting history:"), manual);
        assertTrue(manual.contains("at 600 tok/s, assumed"), manual);
        String hosted = AiPanel.describeCompaction(true, 24_800, 8_100, false, 0);
        assertEquals("History compacted automatically: ~24.8k -> ~8.1k tokens, saving ~16.7k tokens per request.",
                hosted);
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
