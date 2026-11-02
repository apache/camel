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

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The chaos dev console: the current rules with id, version and state, and the report, as JSON and as text; why chaos
 * is off; and that it only reads, whatever options it gets (the HTTP server passes any query parameter as an option).
 */
class ChaosDevConsoleTest {

    private CamelContext context;

    @AfterEach
    void stop() {
        if (context != null) {
            context.stop();
        }
    }

    private DevConsole start(String profile, Map<String, String> properties) throws Exception {
        context = new DefaultCamelContext();
        context.getCamelContextExtension().setProfile(profile);
        properties.forEach(context.getPropertiesComponent()::addInitialProperty);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("start").to("mock:target");
            }
        });
        context.start();
        // found by its id, as the console registry of the developer console and the CLI connector finds it
        // (META-INF/services/org/apache/camel/dev-console/chaos)
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("chaos");
        assertThat(console).isNotNull();
        return console;
    }

    private static JsonObject json(DevConsole console, Map<String, Object> options) {
        return (JsonObject) console.call(DevConsole.MediaType.JSON, options);
    }

    @Test
    void readOnly() throws Exception {
        DevConsole console = start(null, Map.of("camel.chaos.enabled", "true"));
        assertThat(console).isInstanceOf(ChaosDevConsole.class);
        assertThat(console.isReadOnly()).isTrue();
        assertThat(console.getId()).isEqualTo("chaos");
    }

    @Test
    void rulesWithIdVersionAndStateAndTheReport() throws Exception {
        DevConsole console = start(null, Map.of("camel.chaos.enabled", "true", "camel.chaos.seed", "7"));
        ChaosManager chaos = ChaosManager.get(context);
        String r1 = chaos.addRule("endpoint=mock:target error rate=1 until=1m");
        String r2 = chaos.addRule("step=* delay duration=1ms rate=0.5");
        assertThat(chaos.changeRule(r1, "endpoint=mock:target error rate=1 until=5")).isEqualTo(2);
        chaos.removeRule(r2);
        // what the CLI action asks before it changes a rule
        assertThat(chaos.hasRule(r1)).isTrue();
        assertThat(chaos.hasRule(r2)).isFalse();
        assertThat(chaos.hasRule("r9")).isFalse();
        context.createProducerTemplate().send("direct:start", e -> e.getMessage().setBody("x"));

        JsonObject root = json(console, Map.of());
        assertThat(root.getBoolean("enabled")).isTrue();
        assertThat(root).doesNotContainKey("reason");
        // the current rules: r1 in its second version; not the replaced r1 nor the removed r2
        JsonArray rules = root.getCollection("rules");
        assertThat(rules).hasSize(1);
        JsonObject rule = (JsonObject) rules.get(0);
        assertThat(rule.getString("id")).isEqualTo(r1);
        assertThat(rule.getInteger("version")).isEqualTo(2);
        assertThat(rule.getString("state")).isEqualTo("active");
        assertThat(rule.getString("rule")).isEqualTo("endpoint=mock:target error rate=1 until=5");
        assertThat(rule.getLong("matches")).isEqualTo(1);
        assertThat(((JsonObject) rule.get("fired")).getLong("send")).isEqualTo(1);
        assertThat(rule.getLong("endsIn")).isPositive();
        // the report: the verdict, the seed and every version of every rule
        JsonObject report = (JsonObject) root.get("report");
        assertThat(report.getString("verdict")).isEqualTo("PASS");
        assertThat(report.getBoolean("final")).isTrue();
        assertThat(report.getLong("seed")).isEqualTo(7);
        assertThat(report.getBoolean("seedGiven")).isTrue();
        List<String> entries = ((JsonArray) report.get("rules")).stream().map(JsonObject.class::cast)
                .map(r -> r.getString("id") + " v" + r.getInteger("version") + " " + r.getString("state")).toList();
        assertThat(entries).containsExactly(r1 + " v2 active", r1 + " v1 replaced", r2 + " v1 removed");
        assertThat(((JsonArray) report.get("routes")).stream().map(JsonObject.class::cast)
                .map(r -> r.getString("route") + " failed " + r.getLong("failed"))).containsExactly("start failed 1");
    }

    @Test
    void text() throws Exception {
        DevConsole console = start(null, Map.of("camel.chaos.enabled", "true", "camel.chaos.seed", "7"));
        ChaosManager.get(context).addRule("endpoint=mock:target drop");

        String text = (String) console.call(DevConsole.MediaType.TEXT);

        assertThat(text).startsWith("Chaos report: PASS").contains("seed: 7 (camel.chaos.seed)")
                .contains("r1 active: endpoint=mock:target drop");
    }

    @Test
    void anEndedRuleIsStillCurrent() throws Exception {
        DevConsole console = start(null, Map.of("camel.chaos.enabled", "true"));
        ChaosManager.get(context).addRule("endpoint=mock:target drop until=1");
        context.createProducerTemplate().sendBody("direct:start", "x");

        JsonArray rules = json(console, Map.of()).getCollection("rules");
        JsonObject rule = (JsonObject) rules.get(0);

        assertThat(rule.getString("state")).isEqualTo("ended");
        assertThat(rule).doesNotContainKey("endsIn");
    }

    @Test
    void offWithoutProfile() throws Exception {
        DevConsole console = start(null, Map.of("camel.chaos.rules", "endpoint=mock:target error"));

        JsonObject root = json(console, Map.of());

        assertThat(root.getBoolean("enabled")).isFalse();
        assertThat(root.getString("reason"))
                .contains("profile dev (unless camel.chaos.enabled=false) or with camel.chaos.enabled=true");
        assertThat(root).doesNotContainKeys("rules", "report");
        assertThat((String) console.call(DevConsole.MediaType.TEXT)).contains("Chaos is off");
    }

    @Test
    void offWithProfileProd() throws Exception {
        DevConsole console = start("prod", Map.of("camel.chaos.enabled", "true"));

        JsonObject root = json(console, Map.of());

        assertThat(root.getBoolean("enabled")).isFalse();
        assertThat(root.getString("reason")).isEqualTo("Chaos is off: it stays off with profile prod");
        assertThat(ChaosManager.get(context)).isNull();
    }

    @Test
    void optionsDoNotAddChangeOrRemoveRules() throws Exception {
        DevConsole console = start(null, Map.of("camel.chaos.enabled", "true"));
        ChaosManager chaos = ChaosManager.get(context);
        String id = chaos.addRule("endpoint=mock:target drop");
        // what a request to /q/dev/chaos?... could pass: the console ignores it
        Map<String, Object> options = Map.of("command", "add", "action", "clear", "rule", "endpoint=* error",
                "id", id);

        json(console, options);
        console.call(DevConsole.MediaType.TEXT, options);

        JsonArray rules = (JsonArray) chaos.report().toJson().get("rules");
        assertThat(rules).hasSize(1);
        assertThat(((JsonObject) rules.get(0)).getString("rule")).isEqualTo("endpoint=mock:target drop");
        assertThat(((JsonObject) rules.get(0)).getString("state")).isEqualTo("active");
    }
}
