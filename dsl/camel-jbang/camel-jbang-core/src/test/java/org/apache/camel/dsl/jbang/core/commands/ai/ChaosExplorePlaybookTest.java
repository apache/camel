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
package org.apache.camel.dsl.jbang.core.commands.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The playbook of "let there be chaos" (CAMEL-26787): the same steps for camel ask and the MCP server, each with the
 * names of its own tools, which exist.
 */
class ChaosExplorePlaybookTest {

    @Test
    void camelAskGetsItsOwnToolsAndARunningExploration() {
        String text = ChaosExplorePlaybook.text(ChaosExplorePlaybook.ASK, "shop", "30m", "refunds", true);

        assertThat(text).contains("explore how the integration shop copes with faults, by yourself, for 30m",
                "The exploration is running (30m): do not start another one.", "Focus on refunds",
                "chaos_report", "chaos_suggest", "chaos_experiment", "chaos_explore_finish", "get_errors")
                .doesNotContain("camel_runtime_", "chaos_explore_start");
    }

    @Test
    void theToolsItNamesExist() {
        ChaosExplorePlaybook.Tools t = ChaosExplorePlaybook.ASK;
        for (String name : new String[] { t.start(), t.report(), t.suggest(), t.experiment(), t.finish(), t.errors() }) {
            assertThat(ToolRegistry.findTool(name)).as(name).isNotNull();
        }
    }

    @Test
    void theExpectationsItTeachesAreRulesCamelChaosTakes() {
        String text = ChaosExplorePlaybook.text(ChaosExplorePlaybook.MCP, null, null, null, false);

        // the forms of expect= that ChaosRuleParser reads
        assertThat(text).contains("expect=<route>:failed<=0", "expect=<route>-><next route>:lost<=0",
                "expect=<route>:20", "expect=<route>:maxTime<=5s", "within=");
    }
}
