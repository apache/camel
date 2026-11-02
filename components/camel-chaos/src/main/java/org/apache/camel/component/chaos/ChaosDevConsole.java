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

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonObject;

/**
 * The chaos rules and their report.
 * <p/>
 * The console only reads: it lists the rules and shows the report, and has no option that adds, changes or removes a
 * rule. The developer console of the HTTP server takes any console's options from the query string, so a rule can only
 * be added through {@code camel.chaos.rules}, Java ({@link ChaosManager}) and the {@code chaos} action of the CLI
 * connector, never over HTTP.
 *
 * @since 4.24
 */
@DevConsole(name = "chaos", displayName = "Chaos", description = "The chaos rules and their report")
public class ChaosDevConsole extends AbstractDevConsole {

    public record RuleEntry(
            @Metadata(description = "The id of the rule, such as r1") String id,
            @Metadata(description = "The version of the rule: 1 when added, one more each time it is changed") int version,
            @Metadata(description = "The rule in its one-line text form") String rule,
            @Metadata(description = "The state of the rule: active or ended") String state,
            @Metadata(description = "Where the rule fires: send, poll, entry or step") List<String> hooks,
            @Metadata(description = "The sends, polls, exchanges or steps the target of the rule matched") long matches,
            @Metadata(description = "The faults the rule fired, per hook") Map<String, Long> fired,
            @Metadata(description = "The faults that did nothing, as the rule fired at a message or an endpoint it cannot change") long skipped,
            @Metadata(description = "The milliseconds until an active rule ends") Long endsIn,
            @Metadata(description = "The expectation of the rule (expect= and within=) and its outcome, when it has one") Map<String, Object> expectation) {
    }

    public record Response(
            @Metadata(description = "Whether chaos is switched on: with profile dev or camel.chaos.enabled=true, never with profile prod") boolean enabled,
            @Metadata(description = "Why chaos is off (only when it is off)") String reason,
            @Metadata(description = "The current rules (not the removed or replaced ones; only when chaos is on)") List<RuleEntry> rules,
            @Metadata(description = "The report: verdict, final, seed, seedGiven, lost, reasons, all rules with the removed and replaced ones, and the counts per route (only when chaos is on)") Map<String, Object> report) {
    }

    public ChaosDevConsole() {
        super("camel", "chaos", "Chaos", "The chaos rules and their report");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        ChaosManager chaos = ChaosManager.get(getCamelContext());
        if (chaos == null) {
            return offReason() + "\n";
        }
        return chaos.report().toText() + "\n";
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();
        ChaosManager chaos = ChaosManager.get(getCamelContext());
        root.put("enabled", chaos != null);
        if (chaos == null) {
            root.put("reason", offReason());
            return root;
        }
        // one report, so the rules and the report are of the same moment
        ChaosReport report = chaos.report();
        root.put("rules", report.rulesJson(true));
        root.put("report", report.toJson());
        return root;
    }

    private String offReason() {
        String profile = getCamelContext().getCamelContextExtension().getProfile();
        if ("prod".equalsIgnoreCase(profile)) {
            return "Chaos is off: it stays off with profile prod";
        }
        return "Chaos is off: it is switched on with profile dev (unless camel.chaos.enabled=false) or with"
               + " camel.chaos.enabled=true, and never with profile prod";
    }
}
