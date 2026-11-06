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
            @Metadata(description = "The state of the rule: active, waiting (before its start, from=) or ended") String state,
            @Metadata(description = "Where the rule fires: send, reply, dlq, poll, entry or step") List<String> hooks,
            @Metadata(description = "The sends, polls, exchanges or steps the target of the rule matched") long matches,
            @Metadata(description = "The faults the rule fired, per hook") Map<String, Long> fired,
            @Metadata(description = "The faults that did nothing, as the rule fired at a message or an endpoint it cannot change") long skipped,
            @Metadata(description = "Why the rule did nothing when it was skipped: the count per reason (only when it was skipped)") Map<String, Long> skippedReasons,
            @Metadata(description = "The messages lost because the rule failed or dropped the send of the dead letter channel, which then handled them (only for a rule with at=dlq)") Long lostAtDlq,
            @Metadata(description = "The milliseconds until an active rule ends") Long endsIn,
            @Metadata(description = "The milliseconds until a waiting rule starts (from=)") Long startsIn,
            @Metadata(description = "The share of the matches the rule fires at: rate=, 1/N for rate=every:N, the share of each period in a burst for rate=burst:, else 1 (not for a lifecycle or stall rule)") Double rate,
            @Metadata(description = "The share of the matches the rule fired at so far, to compare with rate (once it matched; not for a lifecycle or stall rule)") Double observedRate,
            @Metadata(description = "The matches until a rule with rate=every:N fires again (only for such a rule while it is active)") Long nextFireIn,
            @Metadata(description = "The milliseconds until the burst of an active rate=burst: rule ends (only while it is in a burst)") Long burstEndsIn,
            @Metadata(description = "The milliseconds until the next burst of an active rate=burst: rule (only between bursts)") Long nextBurstIn,
            @Metadata(description = "The milliseconds since the rule last fired (only when it fired)") Long lastFiredAgo,
            @Metadata(description = "The faults the rule fired in the last minute") long faultsLastMinute,
            @Metadata(description = "The faults the rule fired per second over the last minute, 60 counts from the oldest second to the current one (only when it fired in the last minute)") List<Long> faultsPerSecond,
            @Metadata(description = "The effect of the faults on the exchanges they hit (for a lifecycle or stall rule: the exchanges sent to its route while it was down), each counted once when it is done: faulted, completed, recovered (by a redelivery of the error handler), handled (by the error handler, such as the dead letter channel), failed and inFlight (only when the rule faulted an exchange)") Map<String, Long> effect,
            @Metadata(description = "The expectation of the rule (expect= and within=) and its outcome, when it has one") Map<String, Object> expectation,
            @Metadata(description = "What a lifecycle or stall rule did to its route: action, route, duration, state (pending, acting, down, comingBack, back, skipped, left, failed or cancelled), round (the times it took the route down), repeat and every (with repeat=), actsIn (until it acts, again with repeat=), downAt, backAt, backIn, endedEarly and reason (only for such a rule)") Map<String, Object> lifecycle) {
    }

    public record Response(
            @Metadata(description = "Whether chaos is switched on: with profile dev or camel.chaos.enabled=true, never with profile prod") boolean enabled,
            @Metadata(description = "Why chaos is off (only when it is off)") String reason,
            @Metadata(description = "The current rules (not the removed or replaced ones; only when chaos is on)") List<RuleEntry> rules,
            @Metadata(description = "The report: verdict, final, seed, seedGiven, lost, reasons, all rules with the removed and replaced ones, the counts per route, and the recent faults (the last 20, the latest first: timestamp, ago, rule, version, fault, hook, route, endpoint or step, exchangeId) (only when chaos is on)") Map<String, Object> report) {
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
