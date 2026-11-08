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
package org.apache.camel.component.chaos;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * camel.chaos.scenario loads a scenario when the application starts, and camel.chaos.reportFile writes the report when
 * it stops, as camel run --chaos=file and --chaos-report=file do.
 */
class ChaosScenarioMainTest {

    @TempDir
    Path dir;

    private Main main;

    @AfterEach
    void stop() {
        if (main != null) {
            main.stop();
        }
    }

    private Main main() {
        main = new Main();
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("start").to("mock:target");
            }
        });
        main.addInitialProperty("camel.main.profile", "dev");
        return main;
    }

    private Exchange send() throws Exception {
        return main.getCamelTemplate().send("direct:start", e -> e.getMessage().setBody("x"));
    }

    private JsonObject readReport(Path file) throws Exception {
        return (JsonObject) Jsoner.deserialize(Files.readString(file));
    }

    @Test
    void aScenarioFileWithItsSeed() throws Exception {
        Path scenario = Files.writeString(dir.resolve("outage.chaos"), """
                # the target is down
                seed=3
                endpoint=mock:target error message=down
                """);
        main().addInitialProperty("camel.chaos.scenario", scenario.toString());
        main.addInitialProperty("camel.chaos.rules", "endpoint=mock:other drop");
        main.start();

        assertThat(send().getException()).isInstanceOf(ChaosException.class).hasMessage("down");
        JsonObject report = ChaosManager.get(main.getCamelContext()).report().toJson();
        assertThat(report.getLong("seed")).isEqualTo(3);
        assertThat(report.getString("seedFrom")).isEqualTo("scenario outage.chaos");
        // the scenario first, then the rules of camel.chaos.rules
        JsonArray rules = (JsonArray) report.get("rules");
        assertThat(rules).hasSize(2);
        assertThat(((JsonObject) rules.get(0)).getString("scenario")).isEqualTo("outage.chaos");
        assertThat(((JsonObject) rules.get(1)).getString("scenario")).isNull();
    }

    @Test
    void theSeedOptionWinsOverTheSeedOfTheScenario() throws Exception {
        main().addInitialProperty("camel.chaos.scenario", "classpath:chaos/boom.chaos");
        main.addInitialProperty("camel.chaos.seed", "5");
        main.start();

        assertThat(send().getException()).hasMessage("boom");
        JsonObject report = ChaosManager.get(main.getCamelContext()).report().toJson();
        assertThat(report.getLong("seed")).isEqualTo(5);
        assertThat(report.getString("seedFrom")).isEqualTo("camel.chaos.seed");
    }

    @Test
    void aScenarioThatIsMissingOrNotValidStopsTheStart() throws Exception {
        main().addInitialProperty("camel.chaos.scenario", dir.resolve("missing.chaos").toString());
        assertThatThrownBy(main::start).hasStackTraceContaining("camel.chaos.scenario=")
                .hasStackTraceContaining("missing.chaos cannot be read");
        main.stop();

        Path bad = Files.writeString(dir.resolve("bad.chaos"), "endpoint=mock:target error\nendpoint=mock:target bang\n");
        main().addInitialProperty("camel.chaos.scenario", bad.toString());
        assertThatThrownBy(main::start).hasStackTraceContaining("Chaos scenario bad.chaos, line 2: Invalid chaos rule");
    }

    @Test
    void theReportIsWrittenWhenTheApplicationStops() throws Exception {
        Path file = dir.resolve("target/chaos-report.json");
        main().addInitialProperty("camel.chaos.scenario", "classpath:chaos/boom.chaos");
        main.addInitialProperty("camel.chaos.reportFile", file.toString());
        main.start();
        send();
        assertThat(file).doesNotExist();

        main.stop();

        JsonObject report = readReport(file);
        assertThat(report.getString("verdict")).isEqualTo(ChaosReport.PASS);
        JsonObject rule = (JsonObject) ((JsonArray) report.get("rules")).get(0);
        assertThat(rule.getLong("matches")).isEqualTo(1);
    }

    @Test
    void theReportIsWrittenAlsoWithoutRules() throws Exception {
        Path file = dir.resolve("chaos.json");
        main().addInitialProperty("camel.chaos.reportFile", file.toString());
        main.start();
        main.stop();

        assertThat((JsonArray) readReport(file).get("rules")).isEmpty();
    }
}
