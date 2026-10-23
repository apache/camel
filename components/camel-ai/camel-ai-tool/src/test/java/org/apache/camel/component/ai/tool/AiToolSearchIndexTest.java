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
package org.apache.camel.component.ai.tool;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AiToolSearchIndexTest {

    private static final String INVOICE_SCHEMA = "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}}}";

    @Test
    public void keywordRanksNameAboveDescriptionAndKeepsSchema() {
        AiToolSearchCandidate invoice = candidate("getInvoice", "Fetch one invoice", INVOICE_SCHEMA, true, "route");
        AiToolSearchCandidate mention = candidate("auditLog", "Records each invoice lookup", "{}", true, "mcp");
        AiToolSearchCandidate visible = candidate("health", "invoice status", "{}", false, "route");

        AiToolSearchIndex index = AiToolSearchIndex.create(
                AiToolSearchStrategy.KEYWORD, List.of(invoice, mention, visible), 5, null);

        List<AiToolSearchCandidate> hits = index.searchKeyword(List.of("invoice"));

        assertThat(hits).containsExactly(invoice, mention);
        assertThat(hits.get(0).parametersJsonSchema()).isEqualTo(INVOICE_SCHEMA);
        assertThat(hits.get(0).source()).isEqualTo("route");
        assertThat(index.size()).isEqualTo(2);
    }

    @Test
    public void keywordLimitsResultsAndBreaksTiesByCatalogueOrder() {
        AiToolSearchCandidate first = candidate("alphaTool", "shared topic", null, true, "a");
        AiToolSearchCandidate second = candidate("betaTool", "shared topic", null, true, "b");
        AiToolSearchCandidate third = candidate("gammaTool", "shared topic", null, true, "c");

        AiToolSearchIndex index = AiToolSearchIndex.create(
                AiToolSearchStrategy.KEYWORD, List.of(first, second, third), 2, null);

        assertThat(index.searchKeyword(List.of("shared", "topic"))).containsExactly(first, second);
    }

    @Test
    public void keywordScoresNameHigherThanDescriptionWhenBothMatchDifferentTools() {
        AiToolSearchCandidate named = candidate("invoice", "unrelated", null, true, "route");
        AiToolSearchCandidate described = candidate("other", "invoice helper", null, true, "mcp");

        AiToolSearchIndex index = AiToolSearchIndex.create(
                AiToolSearchStrategy.KEYWORD, List.of(described, named), 5, null);

        assertThat(index.searchKeyword(List.of("Invoice"))).containsExactly(named, described);
    }

    @Test
    public void keywordNoMatchAndEmptyCatalogueReturnNothing() {
        AiToolSearchIndex index = AiToolSearchIndex.create(
                AiToolSearchStrategy.KEYWORD,
                List.of(candidate("weather", "Forecast", null, true, "route")),
                5, null);

        assertThat(index.searchKeyword(List.of("invoice"))).isEmpty();
        assertThat(index.searchKeyword(List.of("  "))).isEmpty();
        assertThat(index.searchKeyword(List.of())).isEmpty();

        AiToolSearchIndex empty = AiToolSearchIndex.create(AiToolSearchStrategy.KEYWORD, List.of(), 5, null);
        assertThat(empty.searchKeyword(List.of("invoice"))).isEmpty();
        assertThat(empty.size()).isZero();
    }

    @Test
    public void duplicateNamesKeepTheFirstCandidate() {
        AiToolSearchCandidate route = candidate("lookup", "Route lookup", "{\"type\":\"object\"}", true, "route");
        AiToolSearchCandidate mcp = candidate("lookup", "Remote lookup", "{\"type\":\"string\"}", true, "mcp");

        AiToolSearchIndex index = AiToolSearchIndex.create(
                AiToolSearchStrategy.KEYWORD, List.of(route, mcp), 5, null);

        assertThat(index.searchKeyword(List.of("lookup"))).containsExactly(route);
    }

    @Test
    public void vectorRanksByCosineAndRejectsAMissingEmbedder() {
        AiToolSearchEmbedder embedder = text -> {
            if (text.startsWith("invoice")) {
                return new float[] { 1f, 0f };
            }
            if (text.startsWith("weather")) {
                return new float[] { 0f, 1f };
            }
            if ("billing document".equals(text)) {
                return new float[] { 0.9f, 0.1f };
            }
            return new float[] { 0f, 0f };
        };
        AiToolSearchCandidate invoice = candidate("invoice", "billing document", INVOICE_SCHEMA, true, "route");
        AiToolSearchCandidate weather = candidate("weather", "forecast", "{}", true, "mcp");

        assertThatThrownBy(() -> AiToolSearchIndex.create(AiToolSearchStrategy.VECTOR, List.of(invoice), 5, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("VECTOR")
                .hasMessageContaining("does not fall back");

        AiToolSearchIndex index = AiToolSearchIndex.create(
                AiToolSearchStrategy.VECTOR, List.of(weather, invoice), 1, embedder);

        List<AiToolSearchCandidate> hits = index.searchVector("billing document");
        assertThat(hits).containsExactly(invoice);
        assertThat(hits.get(0).parametersJsonSchema()).isEqualTo(INVOICE_SCHEMA);
    }

    @Test
    public void sessionDropsNamesThatLeaveTheCatalogueAndIsolatesConversations() {
        AiToolSearchSession session = new AiToolSearchSession();
        session.remember(List.of("invoice", "weather"));
        session.retainAvailable(Set.of("invoice"));
        assertThat(session.discoveredNames()).containsExactly("invoice");
        session.remember(List.of("invoice", "ledger"));
        assertThat(session.discoveredNames()).containsExactly("invoice", "ledger");

        AiToolSearchSessions sessions = new AiToolSearchSessions();
        sessions.session("one").remember(List.of("invoice"));
        sessions.session("two").remember(List.of("weather"));
        assertThat(sessions.session("one").discoveredNames()).containsExactly("invoice");
        assertThat(sessions.session("two").discoveredNames()).containsExactly("weather");
        assertThat(AiToolSearch.resultMessage(List.of())).isEqualTo("No matching tools found");
        assertThat(AiToolSearch.resultMessage(List.of("invoice", "weather")))
                .isEqualTo("Tools found: invoice, weather");
    }

    private static AiToolSearchCandidate candidate(
            String name, String description, String schema, boolean searchable, Object source) {
        return new AiToolSearchCandidate(name, description, schema, searchable, source);
    }
}
