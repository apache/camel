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
package org.apache.camel.dsl.jbang.core.commands;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.ai.CatalogDocs;

/**
 * Keeps the history of a chat with a model bounded, as the whole history is sent with every request: camel ask and the
 * AI panel of the TUI. A history larger than the context window of the model makes every later question fail.
 */
public final class ChatHistory {

    /**
     * A tool result is cut to this many characters before it enters the history, because a single log or table dump can
     * otherwise be larger than the whole system prompt. It is the longest answer of camel_catalog_doc, so no catalog
     * answer is cut: a long one names at its end the options and headers it leaves out, which the model needs to ask
     * for one of them (16_000 cut that end off, once the catalog answers grew to 18,000 characters).
     */
    public static final int MAX_TOOL_RESULT_CHARS = CatalogDocs.MAX_ANSWER_LENGTH;

    /**
     * Tool results from turns before the previous one are shrunk to this many characters once the turn is answered. The
     * model's own answer already summarises them, and the whole history is re-sent (and re-processed by a local model)
     * on every request.
     */
    public static final int COMPACT_TOOL_RESULT_CHARS = 400;

    /**
     * Oldest turns are dropped beyond this many user questions in one conversation.
     */
    public static final int MAX_HISTORY_TURNS = 20;

    private ChatHistory() {
    }

    /**
     * Caps a tool result before it enters the model history.
     */
    public static String truncateToolResult(String result) {
        if (result == null || result.length() <= MAX_TOOL_RESULT_CHARS) {
            return result;
        }
        return result.substring(0, MAX_TOOL_RESULT_CHARS)
               + "\n... [truncated, " + (result.length() - MAX_TOOL_RESULT_CHARS)
               + " more characters; narrow the request (filter, limit, section) to see the rest]";
    }

    /**
     * Keeps the model history bounded after a turn is answered: tool results from turns before the previous one are
     * shrunk to their head, and the oldest turns are dropped beyond {@code maxTurns} user questions. The previous turn
     * is kept intact so an immediate follow-up can still refer to what was just fetched. Whole turns are removed (user
     * message through the final answer) so assistant tool calls never lose their matching results.
     */
    public static void compact(List<LlmClient.Message> history, int maxTurns, int compactChars) {
        compact(history, maxTurns, compactChars, true);
    }

    /**
     * As {@link #compact(List, int, int)}; with {@code keepPreviousTurn} false the most recent answered turn is
     * compacted as well (used by {@code /compact} of the TUI).
     */
    public static void compact(
            List<LlmClient.Message> history, int maxTurns, int compactChars,
            boolean keepPreviousTurn) {
        if (history == null || history.isEmpty()) {
            return;
        }
        List<Integer> userIndexes = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            LlmClient.Message m = history.get(i);
            if ("user".equals(m.role()) && m.toolCalls() == null && m.toolResults() == null) {
                userIndexes.add(i);
            }
        }
        if (userIndexes.size() > maxTurns) {
            int keepFrom = userIndexes.get(userIndexes.size() - maxTurns);
            history.subList(0, keepFrom).clear();
            int dropped = userIndexes.size() - maxTurns;
            userIndexes = userIndexes.subList(dropped, userIndexes.size()).stream()
                    .map(index -> index - keepFrom).toList();
        }
        // everything before the previous turn (i.e. before the second-last user message) is compacted; /compact
        // also compacts the previous turn itself
        int keep = keepPreviousTurn ? 2 : 1;
        if (userIndexes.size() < keep) {
            return;
        }
        int compactBefore = keepPreviousTurn ? userIndexes.get(userIndexes.size() - 2) : history.size();
        for (int i = 0; i < compactBefore; i++) {
            LlmClient.Message m = history.get(i);
            if (m.toolResults() == null || m.toolResults().isEmpty()) {
                continue;
            }
            boolean changed = false;
            List<LlmClient.ToolResult> compacted = new ArrayList<>(m.toolResults().size());
            for (LlmClient.ToolResult tr : m.toolResults()) {
                String content = tr.content();
                if (content != null && content.length() > compactChars) {
                    content = content.substring(0, compactChars)
                              + "\n... [earlier result compacted; call the tool again for the full data]";
                    changed = true;
                }
                compacted.add(new LlmClient.ToolResult(tr.toolCallId(), content));
            }
            if (changed) {
                history.set(i, LlmClient.Message.toolResults(compacted));
            }
        }
    }
}
