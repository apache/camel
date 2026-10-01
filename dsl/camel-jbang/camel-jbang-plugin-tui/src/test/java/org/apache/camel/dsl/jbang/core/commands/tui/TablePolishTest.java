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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Small things that make the tables read right: one row per health check, and tabs found by the label they show.
 */
class TablePolishTest {

    @Test
    void aCheckReportedForReadinessAndLivenessIsOneRow() {
        List<HealthCheckInfo> checks = new ArrayList<>();
        StatusParser.addHealthCheck(checks, check("context", "UP", true, false));
        StatusParser.addHealthCheck(checks, check("route-controller", "UP", true, false));
        StatusParser.addHealthCheck(checks, check("context", "DOWN", false, true));

        assertThat(checks).hasSize(2);
        HealthCheckInfo context = checks.get(0);
        assertThat(context.readiness).isTrue();
        assertThat(context.liveness).isTrue();
        assertThat(context.state).as("the worse state").isEqualTo("DOWN");
    }

    @Test
    void tabsAreFoundByTheLabelTheBarShows() {
        assertThat(McpFacade.tabIndex("Route")).isEqualTo(McpFacade.tabIndex("Routes")).isEqualTo(5);
        assertThat(McpFacade.tabIndex("endpoint")).isEqualTo(6);
        assertThat(McpFacade.tabIndex("Errors")).isEqualTo(8);
        assertThat(McpFacade.tabIndex("1")).isZero();
        assertThat(McpFacade.tabIndex("0")).isEqualTo(9);
        assertThat(McpFacade.tabIndex("Health")).as("a More tab").isEqualTo(-1);
    }

    private static HealthCheckInfo check(String name, String state, boolean readiness, boolean liveness) {
        HealthCheckInfo hc = new HealthCheckInfo();
        hc.group = "camel";
        hc.name = name;
        hc.state = state;
        hc.readiness = readiness;
        hc.liveness = liveness;
        return hc;
    }
}
