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
package org.apache.camel.dsl.jbang.core.commands.process;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.ai.ReloadOutcome;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.dsl.jbang.run.common.LogFileReader;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.UnitUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * The state of one running integration in one call (CAMEL-26941): what an agent otherwise asks with camel get route,
 * get health, get error and log, each a JVM of its own.
 */
@CommandDefinition(name = "status",
                   description = "The state of a running Camel integration in one call: where it runs and its files,"
                                 + " its routes, health, last errors and last reload, its JVM, and the end of its log",
                   generateHelp = true,
                   footer = {
                           "Examples:",
                           "  camel status",
                           "  camel status myapp --json",
                           "  camel status myapp --log-lines=50" })
public class IntegrationStatus extends ProcessBaseCommand {

    // how many records of the log are read for the last reload, also when fewer are shown
    private static final int RELOAD_RECORDS = 200;

    @Argument(description = "Name or pid of the running Camel integration (default: the only one running)")
    String name = "*";

    @Option(name = "json", hasValue = false,
            description = "Output in JSON Format: one document, with the routes as camel get route has them, and the log"
                          + " records newest first")
    boolean jsonOutput;

    @Option(name = "log-lines", description = "How many of the last log records to show", defaultValue = "20")
    int logLines = 20;

    @Option(name = "errors", description = "How many of the last errors to show", defaultValue = "5")
    int errors = 5;

    public IntegrationStatus(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        if (("*".equals(name) || name == null || name.isBlank()) && ProcessFinder.find("*").isEmpty()) {
            throw CliException.notFound("No running Camel integrations");
        }
        CamelProcess process = ProcessFinder.requireOne(name == null || name.isBlank() ? "*" : name);
        JsonObject log = LogFileReader.read(process.pid(), process.name(), Math.max(logLines, RELOAD_RECORDS), null,
                null, false);
        JsonObject status = status(process, loadErrorFile(process.pid()), log, logLines, errors);
        if (jsonOutput) {
            JsonOutput.print(printer(), status);
        } else {
            printText(status);
        }
        return CliExitCode.OK;
    }

    /**
     * The state of the integration as one document.
     *
     * @param process   the integration, with its status
     * @param errorFile its error file, or <tt>null</tt>
     * @param log       its log as {@link LogFileReader} reads it, newest first
     * @param logLines  how many log records to keep
     * @param errors    how many errors to keep
     */
    static JsonObject status(CamelProcess process, JsonObject errorFile, JsonObject log, int logLines, int errors) {
        JsonObject root = process.status();
        JsonObject context = root.get("context") instanceof JsonObject jo ? jo : new JsonObject();
        JsonObject runtime = root.get("runtime") instanceof JsonObject jo ? jo : new JsonObject();
        JsonObject stats = context.get("statistics") instanceof JsonObject jo ? jo : null;
        JsonArray routes = routes(root);
        JsonObject health = health(root);
        JsonArray lastErrors = errors(errorFile, errors);
        List<JsonObject> records = records(log);
        JsonObject reload = reload(stats, records);
        JsonObject jvm = jvm(root, runtime);
        JsonObject answer = new JsonObject();
        answer.put("pid", process.pid());
        answer.put("name", process.name());
        answer.put("camelVersion", context.getString("version"));
        answer.put("platform", runtime.getString("platform"));
        answer.put("state", context.getString("state"));
        answer.put("phase", context.getInteger("phase"));
        answer.put("uptime", context.getLong("uptime"));
        answer.put("devMode", context.getBoolean("devMode"));
        answer.put("problems", problems(context.getString("state"), routes, health, lastErrors, reload, jvm));
        // where it was started, and the files of its routes, as an agent edits them
        answer.put("directory", runtime.getString("directory"));
        answer.put("sources", sources(routes, root));
        if (stats != null) {
            JsonObject exchanges = new JsonObject();
            exchanges.put("total", number(stats.get("exchangesTotal")));
            exchanges.put("failed", number(stats.get("exchangesFailed")));
            exchanges.put("inflight", number(stats.get("exchangesInflight")));
            JsonObject oldest = oldestInflight(root);
            if (oldest != null) {
                exchanges.put("oldestInflight", oldest);
            }
            answer.put("exchanges", exchanges);
        }
        answer.put("routes", routes);
        answer.put("health", health);
        answer.put("http", http(root));
        answer.put("errors", lastErrors);
        answer.put("reload", reload);
        answer.put("jvm", jvm);
        answer.put("log", new JsonArray(records.subList(0, Math.min(Math.max(logLines, 0), records.size()))));
        return answer;
    }

    /**
     * What is wrong, in a few words each, so an agent does not have to work it out from the rest: a route that is not
     * started or has failed exchanges, a health check that is down, a failed reload, a heap that is nearly full. Empty
     * when all is well. The failed exchanges are those of the routes, which a reload starts again from zero.
     */
    private static JsonArray problems(
            String state, JsonArray routes, JsonObject health, JsonArray errors, JsonObject reload, JsonObject jvm) {
        JsonArray answer = new JsonArray();
        if (state != null && !"Started".equals(state)) {
            answer.add("Camel is " + state);
        }
        for (Object o : routes) {
            JsonObject route = (JsonObject) o;
            String id = route.getString("routeId");
            String status = route.getString("status");
            if (status != null && !"Started".equals(status) && !"Error".equals(status)) {
                answer.add("route " + id + " is " + status);
            }
            Long failed = route.getLong("failed");
            JsonObject lastError = route.getMap("lastError");
            if (failed != null && failed > 0) {
                String last = newestError(errors, id);
                if (last == null && lastError != null) {
                    last = lastError.getString("message");
                }
                answer.add("route " + id + ": " + failed + (failed == 1 ? " failed exchange" : " failed exchanges")
                           + (last != null ? ", the last: " + last : ""));
            } else if (lastError != null) {
                answer.add("route " + id + " failed (" + lastError.getString("phase") + "): "
                           + lastError.getString("message"));
            }
        }
        JsonArray down = health.getCollection("down");
        if (down != null) {
            for (Object o : down) {
                JsonObject check = (JsonObject) o;
                answer.add("health check " + check.getString("id") + " is DOWN"
                           + (check.getString("message") != null ? ": " + check.getString("message") : ""));
            }
        }
        if (Boolean.FALSE.equals(health.getBoolean("ready")) && (down == null || down.isEmpty())) {
            answer.add("not ready");
        }
        if (reload.get("last") instanceof JsonObject last && "failed".equals(last.getString("status"))) {
            String message = last.getString("message");
            answer.add("the last reload failed" + (message != null ? ": " + message.lines().findFirst().orElse("") : ""));
        }
        Long used = jvm.getLong("heapUsed");
        Long max = jvm.getLong("heapMax");
        if (used != null && max != null && max > 0 && used * 10 >= max * 9) {
            answer.add("the heap is " + used * 100 / max + "% full");
        }
        return answer;
    }

    /**
     * The newest error of the route, as <tt>type: message</tt>, or <tt>null</tt>.
     */
    private static String newestError(JsonArray errors, String routeId) {
        for (Object o : errors) {
            JsonObject error = (JsonObject) o;
            if (routeId != null && routeId.equals(error.getString("routeId"))) {
                JsonObject exception = error.getMap("exception");
                return exception != null ? exception.getString("type") + ": " + exception.getString("message") : null;
            }
        }
        return null;
    }

    /**
     * The files of the routes, without the line (<tt>orders.camel.yaml</tt>), and the properties files that were loaded
     * (<tt>application.properties</tt>), as an agent edits those too.
     */
    private static JsonArray sources(JsonArray routes, JsonObject root) {
        Set<String> files = new LinkedHashSet<>();
        for (Object o : routes) {
            if (o instanceof JsonObject route && route.getString("source") != null) {
                files.add(route.getString("source").replaceFirst(":\\d+$", ""));
            }
        }
        if (root.get("properties") instanceof JsonObject properties
                && properties.get("locations") instanceof JsonArray locations) {
            for (Object o : locations) {
                String location = String.valueOf(o);
                if (location.startsWith("file:")) {
                    // file:./application.properties and file:application.properties are the same file
                    String file = StringHelper.before(location.substring(5), ";", location.substring(5));
                    files.add(file.startsWith("./") ? file.substring(2) : file);
                }
            }
        }
        return new JsonArray(new ArrayList<>(files));
    }

    /**
     * The exchange that has been inflight the longest, and where it is now, when the integration lists its inflight
     * exchanges (as in dev mode): one that stays at the same place is how a hang shows.
     */
    private static JsonObject oldestInflight(JsonObject root) {
        if (!(root.get("inflight") instanceof JsonObject inflight)
                || !(inflight.get("exchanges") instanceof JsonArray exchanges)) {
            return null;
        }
        JsonObject oldest = null;
        for (Object o : exchanges) {
            if (o instanceof JsonObject exchange
                    && (oldest == null
                            || exchange.getLongOrDefault("duration", 0) > oldest.getLongOrDefault("duration", 0))) {
                oldest = exchange;
            }
        }
        if (oldest == null) {
            return null;
        }
        JsonObject answer = new JsonObject();
        // the duration of an inflight exchange is the time since it was created
        answer.put("age", oldest.getLong("duration"));
        answer.put("routeId", oldest.getString("atRouteId"));
        answer.put("nodeId", oldest.getString("nodeId"));
        answer.put("source", oldest.getString("nodeSource"));
        return answer;
    }

    /**
     * The Java version, the heap and the threads: a heap near its maximum, or a garbage collection that takes longer
     * and longer, is trouble on its way.
     */
    private static JsonObject jvm(JsonObject root, JsonObject runtime) {
        JsonObject answer = new JsonObject();
        answer.put("javaVersion", runtime.getString("javaVersion"));
        answer.put("javaVendor", runtime.getString("javaVendor"));
        if (root.get("memory") instanceof JsonObject memory) {
            answer.put("heapUsed", memory.getLong("heapMemoryUsed"));
            answer.put("heapMax", memory.getLong("heapMemoryMax"));
        }
        if (root.get("threads") instanceof JsonObject threads) {
            answer.put("threads", threads.getInteger("threadCount"));
        }
        if (root.get("gc") instanceof JsonObject gc) {
            answer.put("gcCount", gc.getLong("collectionCount"));
            answer.put("gcTime", gc.getLong("collectionTime"));
        }
        return answer;
    }

    private static JsonArray routes(JsonObject root) {
        JsonArray answer = new JsonArray();
        if (!(root.get("routes") instanceof JsonArray routes)) {
            return answer;
        }
        for (Object o : routes) {
            if (!(o instanceof JsonObject route)) {
                continue;
            }
            JsonObject jo = new JsonObject();
            jo.put("routeId", route.getString("routeId"));
            jo.put("from", route.getString("from"));
            // where the route is: orders.camel.yaml:4, relative to the directory it was started from
            String source = route.getString("source");
            jo.put("source", source != null && source.startsWith("file:") ? source.substring(5) : source);
            JsonObject lastError = route.get("lastError") instanceof JsonObject le ? le : null;
            // as camel get route has it
            jo.put("status", lastError != null ? "Error" : route.getString("state"));
            String uptime = route.getString("uptime");
            jo.put("uptime", uptime != null ? TimeUtils.toMilliSeconds(uptime) : null);
            if (route.get("statistics") instanceof JsonObject stats) {
                jo.put("total", number(stats.get("exchangesTotal")));
                jo.put("failed", number(stats.get("exchangesFailed")));
                jo.put("inflight", number(stats.get("exchangesInflight")));
            }
            if (lastError != null) {
                JsonObject error = new JsonObject();
                error.put("phase", lastError.getString("phase"));
                error.put("timestamp", lastError.getLong("timestamp"));
                error.put("message", lastError.getString("message"));
                jo.put("lastError", error);
            }
            answer.add(jo);
        }
        return answer;
    }

    private static JsonObject health(JsonObject root) {
        JsonObject answer = new JsonObject();
        if (!(root.get("healthChecks") instanceof JsonObject hc)) {
            return answer;
        }
        answer.put("up", hc.getBoolean("up"));
        answer.put("ready", hc.getBoolean("ready"));
        answer.put("live", hc.getBoolean("live"));
        JsonArray down = new JsonArray();
        if (hc.get("checks") instanceof JsonArray checks) {
            for (Object o : checks) {
                if (o instanceof JsonObject check && "DOWN".equals(check.getString("state"))) {
                    JsonObject jo = new JsonObject();
                    jo.put("id", check.getString("id"));
                    jo.put("message", check.getString("message"));
                    down.add(jo);
                }
            }
        }
        answer.put("down", down);
        return answer;
    }

    /**
     * How to reach the integration over HTTP: the server, the endpoints of the routes, and the management endpoints,
     * such as the health check, so an agent does not have to guess the port.
     */
    private static JsonObject http(JsonObject root) {
        JsonObject answer = new JsonObject();
        JsonArray endpoints = new JsonArray();
        JsonArray management = new JsonArray();
        if (root.get("platform-http") instanceof JsonObject ph) {
            if (ph.getString("server") != null) {
                answer.put("server", ph.getString("server"));
            }
            endpoints(ph.get("endpoints"), endpoints);
            endpoints(ph.get("managementEndpoints"), management);
        }
        answer.put("endpoints", endpoints);
        answer.put("management", management);
        return answer;
    }

    private static void endpoints(Object list, JsonArray answer) {
        if (list instanceof JsonArray endpoints) {
            for (Object o : endpoints) {
                if (o instanceof JsonObject endpoint) {
                    JsonObject jo = new JsonObject();
                    jo.put("url", endpoint.getString("url"));
                    if (endpoint.getString("verbs") != null) {
                        jo.put("verbs", endpoint.getString("verbs"));
                    }
                    answer.add(jo);
                }
            }
        }
    }

    /**
     * The last errors, newest first, without the messages and the message history the error file also has.
     */
    private static JsonArray errors(JsonObject errorFile, int limit) {
        List<JsonObject> all = new ArrayList<>();
        if (errorFile != null && errorFile.get("errors") instanceof JsonArray arr) {
            for (Object o : arr) {
                if (o instanceof JsonObject error) {
                    all.add(error);
                }
            }
        }
        all.sort(Comparator.comparingLong((JsonObject e) -> e.getLongOrDefault("timestamp", 0)).reversed());
        JsonArray answer = new JsonArray();
        for (JsonObject error : all.subList(0, Math.min(Math.max(limit, 0), all.size()))) {
            JsonObject jo = new JsonObject();
            jo.put("timestamp", error.getLong("timestamp"));
            jo.put("routeId", error.getString("routeId"));
            jo.put("nodeId", error.getString("nodeId"));
            jo.put("exchangeId", error.getString("exchangeId"));
            jo.put("handled", error.getBoolean("handled"));
            if (error.get("exception") instanceof JsonObject exception) {
                JsonObject ex = new JsonObject();
                ex.put("type", exception.getString("type"));
                ex.put("message", exception.getString("message"));
                jo.put("exception", ex);
            }
            answer.add(jo);
        }
        return answer;
    }

    /**
     * The reloads of dev mode as Camel counts them, and the outcome of the last one from the log.
     */
    private static JsonObject reload(JsonObject stats, List<JsonObject> records) {
        JsonObject answer = new JsonObject();
        if (stats != null && stats.get("reload") instanceof JsonObject counts) {
            answer.put("reloaded", number(counts.get("reloaded")));
            answer.put("failed", number(counts.get("failed")));
        }
        JsonObject last = ReloadOutcome.classify(records, null);
        if (last != null) {
            answer.put("last", last);
        }
        return answer;
    }

    private static List<JsonObject> records(JsonObject log) {
        List<JsonObject> answer = new ArrayList<>();
        if (log != null && log.get("lines") instanceof JsonArray lines) {
            for (Object o : lines) {
                if (o instanceof JsonObject record) {
                    answer.add(record);
                }
            }
        }
        return answer;
    }

    private static Long number(Object value) {
        return value != null ? JsonValues.number(value.toString()) : null;
    }

    private void printText(JsonObject status) {
        StringBuilder sb = new StringBuilder();
        sb.append(status.getString("name")).append(" (pid ").append(status.getLong("pid")).append(")");
        if (status.getString("camelVersion") != null) {
            sb.append(", Camel ").append(status.getString("camelVersion"));
        }
        if (status.getString("platform") != null) {
            sb.append(" (").append(status.getString("platform")).append(")");
        }
        sb.append(": ").append(status.getString("state"));
        if (status.getLong("uptime") != null) {
            sb.append(", up ").append(TimeUtils.printDuration(status.getLong("uptime")));
        }
        if (Boolean.TRUE.equals(status.getBoolean("devMode"))) {
            sb.append(", dev mode");
        }
        printer().println(sb.toString());
        JsonArray problems = status.getCollection("problems");
        if (problems.isEmpty()) {
            printer().println("Problems: none");
        } else {
            printer().println("Problems:");
            for (Object o : problems) {
                printer().println("  - " + o);
            }
        }
        if (status.getString("directory") != null) {
            printer().println("Directory: " + status.getString("directory"));
        }
        JsonArray sources = status.getCollection("sources");
        if (!sources.isEmpty()) {
            StringJoiner files = new StringJoiner(", ");
            sources.forEach(f -> files.add(f.toString()));
            printer().println("Sources: " + files);
        }
        if (status.get("exchanges") instanceof JsonObject ex) {
            String oldest = "";
            if (ex.get("oldestInflight") instanceof JsonObject o) {
                oldest = " (the oldest " + TimeUtils.printDuration(o.getLongOrDefault("age", 0)) + ", now at "
                         + o.getString("routeId") + "/" + o.getString("nodeId")
                         + (o.getString("source") != null ? " " + o.getString("source") : "") + ")";
            }
            printer().println("Exchanges: " + ex.getLong("total") + " total, " + ex.getLong("failed") + " failed, "
                              + ex.getLong("inflight") + " inflight" + oldest);
        }
        JsonArray routes = status.getCollection("routes");
        printer().println("Routes:" + (routes.isEmpty() ? " none" : ""));
        for (Object o : routes) {
            JsonObject r = (JsonObject) o;
            printer().println("  " + r.getString("routeId") + "  " + r.getString("status") + "  " + r.getString("from")
                              + (r.getString("source") != null ? "  " + r.getString("source") : "")
                              + "  (" + r.getLong("total") + " total, " + r.getLong("failed") + " failed, "
                              + r.getLong("inflight") + " inflight)");
            if (r.get("lastError") instanceof JsonObject le) {
                printer().println("    last error (" + le.getString("phase") + "): " + le.getString("message"));
            }
        }
        JsonObject health = status.getMap("health");
        if (health.getBoolean("up") != null) {
            JsonArray down = health.getCollection("down");
            printer().println("Health: " + (Boolean.TRUE.equals(health.getBoolean("up")) ? "UP" : "DOWN")
                              + (Boolean.TRUE.equals(health.getBoolean("ready")) ? ", ready" : ", not ready")
                              + (Boolean.TRUE.equals(health.getBoolean("live")) ? ", live" : ", not live"));
            for (Object o : down) {
                JsonObject d = (JsonObject) o;
                printer().println("  DOWN " + d.getString("id") + (d.getString("message") != null
                        ? ": " + d.getString("message") : ""));
            }
        }
        JsonObject http = status.getMap("http");
        JsonArray endpoints = http.getCollection("endpoints");
        JsonArray management = http.getCollection("management");
        if (http.getString("server") != null || !endpoints.isEmpty() || !management.isEmpty()) {
            printer().println("HTTP:" + (http.getString("server") != null ? " " + http.getString("server") : ""));
            for (Object o : endpoints) {
                JsonObject h = (JsonObject) o;
                printer().println("  " + (h.getString("verbs") != null ? h.getString("verbs") + " " : "")
                                  + h.getString("url"));
            }
            for (Object o : management) {
                JsonObject h = (JsonObject) o;
                printer().println("  management: " + h.getString("url"));
            }
        }
        JsonArray errors = status.getCollection("errors");
        if (!errors.isEmpty()) {
            printer().println("Errors (newest first):");
            for (Object o : errors) {
                JsonObject e = (JsonObject) o;
                Long ts = e.getLong("timestamp");
                JsonObject ex = e.getMap("exception");
                printer().println("  " + (ts != null ? TimeUtils.printSince(ts) + " ago  " : "") + e.getString("routeId")
                                  + (ex != null ? "  " + ex.getString("type") + ": " + ex.getString("message") : ""));
            }
        }
        JsonObject reload = status.getMap("reload");
        if (reload.getLong("reloaded") != null || reload.get("last") != null) {
            StringBuilder rl = new StringBuilder("Reload: ");
            if (reload.getLong("reloaded") != null) {
                rl.append(reload.getLong("reloaded")).append(" reloaded, ").append(reload.getLong("failed"))
                        .append(" failed");
            }
            if (reload.get("last") instanceof JsonObject last) {
                String message = last.getString("message");
                rl.append(reload.getLong("reloaded") != null ? "; last: " : "last: ").append(last.getString("status"));
                if (message != null) {
                    // the first line: a failure has the cause and the validator's report below it
                    rl.append(": ").append(message.lines().findFirst().orElse(""));
                }
            }
            printer().println(rl.toString());
        }
        JsonObject jvm = status.getMap("jvm");
        StringJoiner vm = new StringJoiner(", ");
        if (jvm.getString("javaVersion") != null) {
            vm.add("Java " + jvm.getString("javaVersion")
                   + (jvm.getString("javaVendor") != null ? " (" + jvm.getString("javaVendor") + ")" : ""));
        }
        Long heapUsed = jvm.getLong("heapUsed");
        if (heapUsed != null) {
            Long heapMax = jvm.getLong("heapMax");
            vm.add("heap " + UnitUtils.printUnitFromBytesDot(heapUsed)
                   + (heapMax != null && heapMax > 0 ? " of " + UnitUtils.printUnitFromBytesDot(heapMax) : ""));
        }
        if (jvm.getInteger("threads") != null) {
            vm.add(jvm.getInteger("threads") + " threads");
        }
        if (jvm.getLong("gcCount") != null) {
            vm.add(jvm.getLong("gcCount") + " GCs (" + jvm.getLong("gcTime") + " ms)");
        }
        if (vm.length() > 0) {
            printer().println("JVM: " + vm);
        }
        JsonArray log = status.getCollection("log");
        if (!log.isEmpty()) {
            printer().println("Log (last " + log.size() + " records):");
            // oldest first, as a log reads
            for (int i = log.size() - 1; i >= 0; i--) {
                JsonObject r = (JsonObject) log.get(i);
                printer().println("  " + r.getString("time") + " " + r.getString("level") + " " + r.getString("message"));
            }
        }
    }

}
