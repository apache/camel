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
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.text.Line;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The marks of the exchanges a chaos rule faulted ({@link ChaosMarks}): from the {@code CamelChaosRule} property of an
 * error, or from the recent faults of the chaos report, on the Activity and Errors tabs.
 */
class ChaosMarksTest {

    private IntegrationInfo info;
    private MonitorContext ctx;

    @BeforeEach
    void setUp() throws Exception {
        Theme.resetForTesting();
        info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "chaosdemo";
        info.chaos = ChaosInfo.parse((JsonObject) Jsoner.deserialize("""
                {"enabled": true,
                 "rules": [{"id": "r1", "version": 1, "state": "active", "rule": "endpoint=http:stock* error status=503"}],
                 "report": {"verdict": "PASS", "final": true, "seed": 42,
                            "recentFaults": [{"ago": 2000, "rule": "r1", "fault": "error status 503", "hook": "send",
                                              "route": "orders", "exchangeId": "3F2A85F3D4213-0000000000000001E"}]}}
                """));
        ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        ctx.selectedPid = "1234";
        ctx.chaosMarks.record(info);
    }

    @Test
    void theRuleOfAnExchange() {
        assertThat(ChaosMarks.ruleOf(ctx, info, "3F2A85F3D4213-0000000000000001E", null)).isEqualTo("r1");
        assertThat(ChaosMarks.ruleOf(ctx, info, "OTHER", null)).isNull();
        // the property of the exchange, when there are properties
        assertThat(ChaosMarks.ruleOf(ctx, info, "OTHER", Map.of(ChaosMarks.PROPERTY, "r7"))).isEqualTo("r7");
        // no marks without chaos
        info.chaos = null;
        assertThat(ChaosMarks.ruleOf(ctx, info, "3F2A85F3D4213-0000000000000001E", null)).isNull();
    }

    @Test
    void theMarksAreKeptAsTheStatusComesInAndBounded() throws Exception {
        ChaosMarks marks = new ChaosMarks();
        for (int i = 0; i < ChaosMarks.MAX + 10; i++) {
            IntegrationInfo next = new IntegrationInfo();
            next.pid = "1234";
            next.chaos = ChaosInfo.parse((JsonObject) Jsoner.deserialize(
                    "{\"enabled\": true, \"report\": {\"recentFaults\": [{\"rule\": \"r1\", \"exchangeId\": \"E" + i
                                                                         + "\"}]}}"));
            marks.record(next);
        }
        // the oldest went, the latest are there
        assertThat(marks.ruleOf("1234", "E0")).isNull();
        assertThat(marks.ruleOf("1234", "E" + (ChaosMarks.MAX + 9))).isEqualTo("r1");
        assertThat(marks.ruleOf("9999", "E5")).isNull();
    }

    @Test
    void theActivityTabMarksTheExchange() {
        ActivityEntry faulted = new ActivityEntry();
        faulted.exchangeId = "3F2A85F3D4213-0000000000000001E";
        faulted.routeId = "orders";
        faulted.failed = true;
        faulted.timestamp = System.currentTimeMillis();
        ActivityEntry fine = new ActivityEntry();
        fine.exchangeId = "3F2A85F3D4213-0000000000000001F";
        fine.routeId = "orders";
        fine.timestamp = System.currentTimeMillis() - 1000;
        info.activity = List.of(faulted, fine);

        ActivityTab tab = new ActivityTab(ctx);
        // the detail of the selected (latest) exchange
        tab.navigateDown();
        // the dice is two columns wide: the screen has a blank cell after it
        String screen = TuiTestHelper.renderToString(tab, 160, 40).replace(TuiIcons.TAB_CHAOS + " ", TuiIcons.TAB_CHAOS);
        assertThat(screen).contains("3F2A85…-1E " + TuiIcons.TAB_CHAOS + "r1");
        // the other exchange is not faulted
        assertThat(screen).doesNotContain("3F2A85…-1F " + TuiIcons.TAB_CHAOS);
        assertThat(screen).contains("injected by chaos rule r1").contains("endpoint=http:stock* error status=503");
    }

    @Test
    void theErrorsTabMarksTheErrorFromItsProperty() {
        ErrorInfo ei = new ErrorInfo();
        ei.exchangeId = "ID-9";
        ei.routeId = "orders";
        ei.exceptionType = "org.apache.camel.http.base.HttpOperationFailedException";
        ei.exceptionMessage = "HTTP operation failed with statusCode: 503";
        ei.timestamp = System.currentTimeMillis();
        ei.properties.put(ChaosMarks.PROPERTY, "r1");
        info.errors = new ArrayList<>(List.of(ei));

        ErrorsTab tab = new ErrorsTab(ctx);
        String screen = TuiTestHelper.renderToString(tab, 160, 40).replace(TuiIcons.TAB_CHAOS + " ", TuiIcons.TAB_CHAOS);
        assertThat(screen).contains(TuiIcons.TAB_CHAOS + "r1 HttpOperation")
                .contains("injected by chaos rule r1");
    }

    @Test
    void theDetailLineIsAddedOnlyForAFaultedExchange() {
        List<Line> lines = new ArrayList<>();
        ChaosMarks.addDetailLine(lines, ctx, info, "OTHER", null);
        assertThat(lines).isEmpty();
        ChaosMarks.addDetailLine(lines, ctx, info, "3F2A85F3D4213-0000000000000001E", null);
        assertThat(lines).hasSize(1);
    }
}
