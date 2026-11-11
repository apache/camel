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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;
import org.apache.camel.cli.connector.RepeatedSend;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.process.ListChaos;
import org.apache.camel.dsl.jbang.core.common.ChaosExperimentRun;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.dsl.jbang.core.common.RuntimeHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;

@CommandDefinition(name = "chaos-experiment",
                   description = "Run one chaos experiment in the exploration of a running integration: add its rules,"
                                 + " send its traffic, wait for the verdict, and end it",
                   generateHelp = true,
                   footer = {
                           "An experiment is part of an exploration (camel cmd chaos-explore-start). It tests a",
                           "hypothesis with rules, one of them with an expectation (expect=) that decides it.",
                           "The rules are added together, the messages of --feed are sent (as camel cmd send",
                           "sends them, with --body, --header, --repeat and --interval), and then it waits until",
                           "the expectations are decided, or --wait is up. The experiment is ended then, its",
                           "rules are removed, and what came of it is printed: the verdict, the expectations,",
                           "what the routes did, and the traffic. It stays in the journal of the exploration",
                           "(camel get chaos --report).",
                           "",
                           "The verdict is FAIL when an expectation is unmet (exit code 1), PASS when all are",
                           "met, and INCONCLUSIVE when it cannot tell: it ended before an expectation was decided",
                           "(wait longer than the within= of the expectation), its faults never fired, or the",
                           "route of a met expectation had no messages. --feed can be the id of a route, as the",
                           "feed: line of camel cmd chaos-suggest has it.",
                           "",
                           "Examples:",
                           "  camel cmd chaos-experiment --hypothesis='a failed refund is read again' \\",
                           "      --rule='route=refunds error rate=every:5 expect=refunds:failed<=0 within=30s' \\",
                           "      --feed=refunds --body=file:test/chaos/refunds-fails/ --repeat=20 --interval=200ms",
                           "  camel cmd chaos-experiment myApp --hypothesis='orders survive a payment outage' \\",
                           "      --rule='endpoint=http:payment* error status=503 until=1m expect=orders:failed<=0 within=70s' \\",
                           "      --wait=90s",
                           "  camel cmd chaos-experiment --end --note='stopped by hand'" })
public class CamelChaosExperimentAction extends CamelChaosAction {

    @Argument(description = "Name or pid of the running Camel integration (must match one); without it,"
                            + " the only one running, or the only one with chaos switched on")
    String name;

    @Option(name = "hypothesis",
            description = "What the experiment tests, in a few words, such as 'orders survive a payment outage'")
    String hypothesis;

    @OptionList(name = "rule", valueSeparator = OptionList.NO_SEPARATOR,
                description = "A rule in its one-line text form, as an entry of camel.chaos.rules; can be given more"
                              + " than once, and one of them needs an expectation (expect=)")
    List<String> rules;

    @Option(name = "feed", description = "The endpoint, or the id of the route, to send the messages of the experiment to;"
                                         + " a route that serves HTTP gets HTTP requests, or give the path to call, such as"
                                         + " 'PUT /orders/42'")
    String feed;

    @Option(name = "body", description = "The body of the messages: text, file:<path>, or file:<folder> for a message per file")
    String body;

    @OptionList(name = "header", valueSeparator = OptionList.NO_SEPARATOR,
                description = "A header of the messages as key=value; can be given more than once")
    List<String> headers;

    @Option(name = "repeat", description = "How many messages to send in all (at most " + RepeatedSend.MAX_MESSAGES + ")")
    int repeat;

    @Option(name = "interval",
            description = "The pause between two messages, such as 200ms, or a range such as 100..500 to draw"
                          + " each pause at random (at most 60s)")
    String interval;

    @Option(name = "wait", description = "The longest wait for the verdict after the messages were sent, and at least the"
                                         + " within= of the expectations (at most 10m)",
            defaultValue = "2m")
    String wait;

    @Option(name = "note", description = "What to note with the experiment when it ends")
    String note;

    @Option(name = "end", hasValue = false, description = "End the open experiment (with --note) instead of running a new one")
    boolean end;

    public CamelChaosExperimentAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        if (end) {
            CamelProcess process = findChaosTarget(name);
            JsonObject ended = chaos(process, "experiment-end", root -> {
                if (note != null && !note.isBlank()) {
                    root.put("note", note);
                }
            });
            print(ended);
            return exitCode(ended);
        }
        if (rules == null || rules.isEmpty()) {
            throw CliException.usage("--rule is required: the rules of the experiment, one of them with expect=");
        }
        if (feed == null && (body != null || headers != null || repeat > 0 || interval != null)) {
            throw CliException.usage("--body, --header, --repeat and --interval go with --feed: where to send the messages");
        }
        if (repeat < 0 || repeat > RepeatedSend.MAX_MESSAGES) {
            throw CliException.usage("--repeat must be between 1 and " + RepeatedSend.MAX_MESSAGES + ", was: " + repeat);
        }
        long waitMillis;
        try {
            RepeatedSend.interval(interval);
            waitMillis = ChaosExperimentRun.waitMillis(wait);
        } catch (IllegalArgumentException e) {
            throw CliException.usage(e.getMessage().replace("The interval", "--interval").replace("The wait", "--wait"));
        }
        CamelProcess process = findChaosTarget(name);
        ChaosExperimentRun.Feed traffic = feed != null
                ? new ChaosExperimentRun.Feed(
                        feed, absolute(body), headers != null ? String.join("\n", headers) : null,
                        repeat, interval)
                : null;
        String why = traffic != null ? ChaosExperimentRun.checkFeed(RuntimeHelper.readStatus(process.pid()), feed) : null;
        if (why != null) {
            throw CliException.usage(why.replace("The feed", "--feed"));
        }
        JsonObject result = ChaosExperimentRun.run(new ChaosExperimentRun.Integration() {
            @Override
            public JsonObject chaos(String command, Consumer<JsonObject> options) {
                return CamelChaosExperimentAction.this.chaos(process, command, options);
            }

            @Override
            public JsonObject send(ChaosExperimentRun.Feed f) {
                return RuntimeHelper.sendMessage(process.pid(), f.endpoint(), f.body(), f.headers(), f.repeat(),
                        f.interval());
            }

            @Override
            public JsonObject status() {
                return RuntimeHelper.readStatus(process.pid());
            }
        }, hypothesis, String.join(";", rules), traffic, waitMillis, note);
        print(result);
        return exitCode(result);
    }

    // a file or a folder of messages here is sent with its absolute path, as the integration may run elsewhere
    private static String absolute(String body) {
        if (body != null && body.startsWith("file:")) {
            File f = new File(body.substring(5));
            if (f.exists()) {
                return "file:" + f.getAbsolutePath();
            }
        }
        return body;
    }

    private void print(JsonObject experiment) {
        lines(experiment).forEach(printer()::println);
    }

    /** What came of the experiment: its lines as the report has them, the traffic, and the time it waited. */
    static List<String> lines(JsonObject experiment) {
        List<String> lines = new ArrayList<>(ListChaos.experimentLines(experiment, ""));
        if (!lines.isEmpty()) {
            lines.set(0, "Experiment " + lines.get(0));
        }
        List<Object> warnings = experiment.getCollection("warnings");
        if (warnings != null) {
            warnings.forEach(w -> lines.add("  Warning: " + w));
        }
        JsonObject traffic = (JsonObject) experiment.get("traffic");
        if (traffic != null) {
            lines.add("  traffic: " + traffic(traffic));
        }
        StringBuilder sb = new StringBuilder();
        if (experiment.containsKey("waited")) {
            sb.append("waited ").append(time(experiment.getLong("waited"))).append(" for the verdict");
        }
        if (experiment.containsKey("timeLeft")) {
            sb.append(sb.isEmpty() ? "" : "; ").append(TimeUtils.printDuration(experiment.getLong("timeLeft")))
                    .append(" left of the exploration");
        }
        if (!sb.isEmpty()) {
            lines.add("  " + sb);
        }
        // the reason of the verdict says why; waiting longer helps only when it ended before a decision (CAMEL-26790)
        String reason = experiment.getString("reason");
        if ("INCONCLUSIVE".equals(experiment.getString("verdict")) && (reason == null || reason.startsWith("it ended"))) {
            lines.add("  An expectation was not decided when it ended: wait longer than its within= (--wait)");
        }
        return lines;
    }

    private static String traffic(JsonObject traffic) {
        String endpoint = traffic.getString("endpoint");
        String error = traffic.getString("error");
        if (error != null) {
            return "not sent to " + endpoint + ": " + error;
        }
        if (traffic.containsKey("sent")) {
            long sent = traffic.getLongOrDefault("sent", 0);
            long failed = traffic.getLongOrDefault("failed", 0);
            StringBuilder sb = new StringBuilder();
            sb.append(sent).append(sent == 1 ? " message" : " messages").append(" sent to ").append(endpoint)
                    .append(traffic.containsKey("localInfra")
                            ? " (the local " + traffic.getString("localInfra") + " from camel infra)" : "")
                    .append(failed > 0 ? " (" + failed + " failed)" : "").append(" in ")
                    .append(time(traffic.getLongOrDefault("elapsed", 0)));
            // the status codes the clients of an HTTP feed got (CAMEL-26793)
            JsonObject codes = (JsonObject) traffic.get("statusCodes");
            if (codes != null && !codes.isEmpty()) {
                StringJoiner sj = new StringJoiner(", ");
                new TreeMap<>(codes).forEach((code, n) -> sj.add(code + " x" + n));
                sb.append(", answered ").append(sj);
            }
            if (traffic.containsKey("planned")) {
                sb.append(" (of ").append(traffic.getLong("planned")).append(": the experiment ended first)");
            }
            return sb.toString();
        }
        // a single message: the answer of the send
        return "1 message sent to " + endpoint
               + (traffic.containsKey("localInfra")
                       ? " (the local " + traffic.getString("localInfra") + " from camel infra)" : "")
               + ("success".equals(traffic.getString("status")) ? "" : " (failed)");
    }

    // a time under a second in millis, as a send of a few messages takes
    private static String time(long millis) {
        return TimeUtils.printDuration(millis, millis < 1000);
    }

    private static int exitCode(JsonObject experiment) {
        return "FAIL".equals(experiment.getString("verdict")) ? CliExitCode.FAILURE : CliExitCode.OK;
    }

    @Override
    protected String describe(JsonObject answer) {
        return String.join("\n", lines(answer));
    }
}
