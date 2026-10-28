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
package org.apache.camel.dsl.jbang.core.common;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The order of the rows of a listing by its --sort option.
 */
class RowSortTest {

    private record Row(String pid, String name, long uptime, String level) {
    }

    private static final RowSort<Row> ORDER = RowSort.<Row> integration(Row::pid, Row::name, Row::uptime)
            .text("level", Row::level)
            .alias("support-level", "level");

    private static List<String> names(String sort) {
        List<Row> rows = new ArrayList<>(
                List.of(
                        new Row("123", "orders", 3000, "Stable"),
                        new Row("45", "Billing", 1000, null),
                        new Row("6789", "audit", 2000, "preview")));
        rows.sort(ORDER.comparator(sort));
        return rows.stream().map(Row::name).toList();
    }

    @Test
    void byPidAsNumber() {
        assertThat(names("pid")).containsExactly("Billing", "orders", "audit");
    }

    @Test
    void byNameIgnoringCase() {
        assertThat(names("name")).containsExactly("audit", "Billing", "orders");
    }

    @Test
    void byAge() {
        assertThat(names("age")).containsExactly("Billing", "audit", "orders");
    }

    @Test
    void reverseOrder() {
        assertThat(names("-name")).containsExactly("orders", "Billing", "audit");
        assertThat(names("-pid")).containsExactly("audit", "orders", "Billing");
    }

    @Test
    void missingValueFirst() {
        assertThat(names("level")).containsExactly("Billing", "audit", "orders");
        // the same order by its other name
        assertThat(names("support-level")).containsExactly("Billing", "audit", "orders");
    }

    @Test
    void withoutSortTheRowsKeepTheirOrder() {
        assertThat(names(null)).containsExactly("orders", "Billing", "audit");
    }

    @Test
    void unknownKeyIsAUsageError() {
        assertThatThrownBy(() -> ORDER.comparator("nmae"))
                .isInstanceOf(CliException.class)
                .hasMessage("--sort must be one of pid, name, age, level, support-level"
                            + " (with - in front for the reverse order), was: nmae")
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.USAGE);
        assertThatThrownBy(() -> ORDER.comparator("-nmae")).isInstanceOf(CliException.class);
    }

    @Test
    void numberThatIsNotANumberFirst() {
        RowSort<Row> order = new RowSort<Row>().number("pid", Row::pid);
        List<Row> rows = new ArrayList<>(List.of(new Row("12", "a", 0, null), new Row("n/a", "b", 0, null)));
        rows.sort(order.comparator("pid"));
        assertThat(rows).extracting(Row::name).containsExactly("b", "a");
    }
}
