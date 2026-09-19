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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.util.Comparator;
import java.util.List;

import org.apache.camel.dsl.jbang.core.common.ExampleHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExampleToolsTest {

    @Test
    void listsTheWholeLadderInReadingOrderWithoutArguments() {
        ExampleTools.ExampleListResult all = new ExampleTools().camel_catalog_examples(null, null, null);

        assertThat(all.count()).isEqualTo(all.total());
        assertThat(all.groups()).isNotEmpty();
        List<String> levels = all.groups().stream().map(ExampleTools.GroupInfo::level).toList();
        assertThat(levels).isSubsetOf(ExampleHelper.getGroupOrder());
        assertThat(levels).isSortedAccordingTo((a, b) -> Integer.compare(
                ExampleHelper.getGroupOrder().indexOf(a), ExampleHelper.getGroupOrder().indexOf(b)));
        assertThat(levels.get(0)).isEqualTo("quick-start");
        ExampleTools.GroupInfo first = all.groups().get(0);
        assertThat(first.title()).isEqualTo(ExampleHelper.getGroupTitle("quick-start"));
        assertThat(first.intro()).isNotBlank();

        // the examples follow the groups in order
        List<String> exampleLevels = all.examples().stream().map(ExampleTools.ExampleInfo::level).distinct().toList();
        assertThat(exampleLevels).isEqualTo(levels);
        assertThat(all.examples().stream().map(ExampleTools.ExampleInfo::name)).contains("quick-start/timer-log");
    }

    @Test
    void filtersOneGroupAndReturnsWhatTheExamplesTeach() {
        ExampleTools.ExampleListResult run = new ExampleTools().camel_catalog_examples(null, "run", null);

        assertThat(run.groups()).hasSize(1);
        assertThat(run.groups().get(0).level()).isEqualTo("run");
        assertThat(run.groups().get(0).count()).isEqualTo(run.total());
        assertThat(run.examples()).allMatch(e -> "run".equals(e.level()));
        assertThat(run.examples()).isSortedAccordingTo(
                Comparator.comparingInt(e -> e.order() != null ? e.order() : Integer.MAX_VALUE));
        assertThat(run.examples()).anyMatch(e -> !e.teaches().isEmpty());
    }

    @Test
    void searchesByNameAndHonoursTheLimit() {
        ExampleTools.ExampleListResult kafka = new ExampleTools().camel_catalog_examples("kafka", null, 1);

        assertThat(kafka.total()).isGreaterThanOrEqualTo(1);
        assertThat(kafka.count()).isEqualTo(1);
        assertThat(kafka.examples().get(0).name()).contains("kafka");
        assertThat(kafka.examples().get(0).infraServices()).contains("kafka");
    }
}
