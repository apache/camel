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
package org.apache.camel.dsl.jbang.core.commands.catalog;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTestSupport;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the row sorting logic of {@link CatalogKamelet}.
 *
 * The {@code doCall()} method downloads the Kamelet catalog from Maven and is therefore not unit tested here; the
 * deterministic, user-visible ordering produced by {@link CatalogKamelet#sortRow} is what these tests pin down.
 */
class CatalogKameletTest extends CamelCommandBaseTestSupport {

    private static KameletModel model(String name, String type, String supportLevel, String description) {
        KameletModel m = new KameletModel();
        m.name = name;
        m.type = type;
        m.supportLevel = supportLevel;
        m.description = description;
        return m;
    }

    private List<KameletModel> sampleRows() {
        List<KameletModel> rows = new ArrayList<>();
        // intentionally unordered, mixed case, so a no-op comparator would be detectable
        rows.add(model("kafka-source", "source", "Stable", "Reads from Kafka"));
        rows.add(model("Aws-S3-sink", "sink", "Preview", "Writes to S3"));
        rows.add(model("timer-source", "action", "Stable", "A timer based source"));
        return rows;
    }

    private CatalogKamelet command() {
        return new CatalogKamelet(new CamelJBangMain().withPrinter(printer));
    }

    private static List<String> names(List<KameletModel> rows) {
        return rows.stream().map(r -> r.name).toList();
    }

    @Test
    void shouldSortByNameCaseInsensitiveByDefault() {
        CatalogKamelet cmd = command();
        cmd.sort = "name";

        List<KameletModel> rows = sampleRows();
        rows.sort(cmd::sortRow);

        // case-insensitive: "Aws-S3-sink" sorts before "kafka-source"
        assertEquals(List.of("Aws-S3-sink", "kafka-source", "timer-source"), names(rows));
    }

    @Test
    void shouldReverseOrderWhenSortKeyHasLeadingMinus() {
        CatalogKamelet cmd = command();
        cmd.sort = "-name";

        List<KameletModel> rows = sampleRows();
        rows.sort(cmd::sortRow);

        assertEquals(List.of("timer-source", "kafka-source", "Aws-S3-sink"), names(rows));
    }

    @Test
    void shouldSortByType() {
        CatalogKamelet cmd = command();
        cmd.sort = "type";

        List<KameletModel> rows = sampleRows();
        rows.sort(cmd::sortRow);

        // ordered by type: action, sink, source
        assertEquals(List.of("timer-source", "Aws-S3-sink", "kafka-source"), names(rows));
    }

    @Test
    void shouldSortBySupportLevelViaBothAliases() {
        List<KameletModel> rowsLevel = sampleRows();
        CatalogKamelet level = command();
        level.sort = "level";
        rowsLevel.sort(level::sortRow);

        List<KameletModel> rowsSupportLevel = sampleRows();
        CatalogKamelet supportLevel = command();
        supportLevel.sort = "support-level";
        rowsSupportLevel.sort(supportLevel::sortRow);

        // "level" and "support-level" are documented aliases and must produce identical ordering
        assertEquals(names(rowsLevel), names(rowsSupportLevel));
        // "Preview" sorts before "Stable"; the two Stable rows keep stable relative order
        assertEquals(List.of("Aws-S3-sink", "kafka-source", "timer-source"), names(rowsLevel));
    }

    @Test
    void shouldSortByDescription() {
        CatalogKamelet cmd = command();
        cmd.sort = "description";

        List<KameletModel> rows = sampleRows();
        rows.sort(cmd::sortRow);

        assertEquals(List.of("timer-source", "kafka-source", "Aws-S3-sink"), names(rows));
    }

    @Test
    void shouldLeaveOrderUnchangedForUnknownSortKey() {
        CatalogKamelet cmd = command();
        cmd.sort = "bogus";

        List<KameletModel> original = sampleRows();
        List<KameletModel> rows = sampleRows();
        rows.sort(cmd::sortRow);

        // an unrecognized key yields a comparator that returns 0, so the input order is preserved
        assertEquals(names(original), names(rows));
        assertEquals(0, cmd.sortRow(original.get(0), original.get(1)),
                "unknown sort key must produce a no-op comparator");
    }
}
