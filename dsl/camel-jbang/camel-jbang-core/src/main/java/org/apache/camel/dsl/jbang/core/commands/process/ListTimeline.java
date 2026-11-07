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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.ActionClient;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.dsl.jbang.core.common.RowSort;
import org.apache.camel.dsl.jbang.core.common.TerminalWidthHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "timeline",
         description = "Get what the routes did per second, the errors, and the failed and slowest exchanges",
         sortOptions = false, showDefaultValues = true,
         footer = {
                 "%nThe integration records the last 5 minutes (the timeline dev console, on in profile dev).",
                 "%nExamples:",
                 "  camel get timeline",
                 "  camel get timeline --seconds=300",
                 "  camel get timeline --exchanges --sort=elapsed",
                 "  camel get timeline --errors",
                 "  camel get timeline --filter=orders --watch" })
public class ListTimeline extends ProcessWatchCommand {

    private static final String SPARK = " ▁▂▃▄▅▆▇█";
    private static final int SPARK_WIDTH = 30;

    private static final RowSort<ExchangeRow> EXCHANGE_ORDER
            = RowSort.<ExchangeRow> integration(r -> r.pid, r -> r.name, r -> r.uptime)
                    // the slowest first, as the newest first for since
                    .by("elapsed", Comparator.comparingLong((ExchangeRow r) -> r.elapsed).reversed())
                    .by("since", Comparator.comparingLong((ExchangeRow r) -> r.timestamp).reversed());

    public static class SortCandidates implements Iterable<String> {

        public SortCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("pid", "name", "age", "elapsed", "since").iterator();
        }
    }

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--seconds" }, defaultValue = "60",
                        description = "The number of seconds to show, up to the seconds the integration records")
    int seconds = 60;

    @CommandLine.Option(names = { "--filter" },
                        description = "Filter by route ID (pattern)")
    String filter;

    @CommandLine.Option(names = { "--exchanges" },
                        description = "List the exchanges kept per second (the failed, handled and recovered ones and the 3 slowest) instead of the routes")
    boolean exchanges;

    @CommandLine.Option(names = { "--errors" },
                        description = "List the errors by route and exception instead of the routes")
    boolean errors;

    @CommandLine.Option(names = { "--sort" }, completionCandidates = SortCandidates.class,
                        description = "Sort the exchanges by pid, name, age, elapsed (the slowest first), or since (the newest first)",
                        defaultValue = "since")
    String sort;

    @CommandLine.Option(names = { "--limit" },
                        description = "Filter the exchanges by limiting to the given number of rows")
    int limit;

    @CommandLine.Option(names = { "--short-uri" },
                        description = "List endpoint URI without query parameters (short)")
    boolean shortUri;

    @CommandLine.Option(names = { "--wide-uri" },
                        description = "List endpoint URI in full details")
    boolean wideUri;

    public ListTimeline(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doProcessWatchCall() throws Exception {
        List<RouteRow> routeRows = new ArrayList<>();
        List<ExchangeRow> exchangeRows = new ArrayList<>();
        List<ErrorRow> errorRows = new ArrayList<>();
        String pattern = filter != null && !filter.endsWith("*") ? filter + "*" : filter;

        forEachIntegration(name, (ph, root) -> {
            JsonObject context = (JsonObject) root.get("context");
            if (context == null) {
                return;
            }
            String integrationName = context.getString("name");
            if ("CamelJBang".equals(integrationName)) {
                integrationName = ProcessHelper.extractName(root, ph);
            }
            JsonObject timeline = loadTimeline(ph.pid(), pattern);
            if (timeline == null) {
                return;
            }
            Row base = new Row();
            base.pid = Long.toString(ph.pid());
            base.name = integrationName;
            base.uptime = extractSince(ph);
            base.age = TimeUtils.printSince(base.uptime);
            addRoutes(base, timeline, routeRows);
            addExchanges(base, timeline, exchangeRows);
            addErrors(base, timeline, errorRows);
        });

        if (exchanges) {
            exchangeRows.sort(EXCHANGE_ORDER.comparator(sort));
            if (limit > 0 && exchangeRows.size() > limit) {
                exchangeRows.subList(limit, exchangeRows.size()).clear();
            }
            if (!exchangeRows.isEmpty() || jsonOutput) {
                printExchanges(exchangeRows);
            }
        } else if (errors) {
            if (!errorRows.isEmpty() || jsonOutput) {
                printErrors(errorRows);
            }
        } else if (!routeRows.isEmpty() || jsonOutput) {
            printRoutes(routeRows);
        }
        return 0;
    }

    /**
     * Asks the integration for its timeline.
     */
    JsonObject loadTimeline(long pid, String routeId) {
        JsonObject request = new JsonObject();
        request.put("action", "timeline");
        request.put("seconds", Integer.toString(seconds));
        request.put("exchanges", Boolean.toString(exchanges));
        if (routeId != null) {
            request.put("routeId", routeId);
        }
        try {
            ActionClient.ActionResult result = ActionClient.execute(pid, request, ActionClient.DEFAULT_TIMEOUT);
            return result.isOk() && result.reply() != null && result.reply().containsKey("routes") ? result.reply() : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static void addRoutes(Row base, JsonObject timeline, List<RouteRow> rows) {
        JsonArray routes = (JsonArray) timeline.get("routes");
        if (routes == null) {
            return;
        }
        for (Object o : routes) {
            JsonObject r = (JsonObject) o;
            RouteRow row = new RouteRow();
            row.copy(base);
            row.routeId = r.getString("routeId");
            row.from = r.getString("from");
            long[] completed = values(r, "completed");
            long[] recovered = values(r, "recovered");
            long[] handled = values(r, "handled");
            long[] failed = values(r, "failed");
            long[] mean = values(r, "meanMillis");
            long[] max = values(r, "maxMillis");
            long[] p95 = values(r, "p95Millis");
            row.completed = sum(completed);
            row.recovered = sum(recovered);
            row.handled = sum(handled);
            row.failed = sum(failed);
            row.attempts = sum(values(r, "redeliveryAttempts"));
            row.total = row.completed + row.recovered + row.handled + row.failed;
            long[] done = new long[completed.length];
            long weighted = 0;
            long timed = 0;
            for (int i = 0; i < done.length; i++) {
                done[i] = completed[i] + at(recovered, i) + at(handled, i) + at(failed, i);
                long ok = done[i] - at(failed, i);
                weighted += at(mean, i) * ok;
                timed += ok;
                row.max = Math.max(row.max, at(max, i));
                row.p95 = Math.max(row.p95, at(p95, i));
            }
            row.mean = timed > 0 ? weighted / timed : 0;
            row.spark = spark(done, SPARK_WIDTH);
            rows.add(row);
        }
    }

    private static void addExchanges(Row base, JsonObject timeline, List<ExchangeRow> rows) {
        JsonArray kept = (JsonArray) timeline.get("exchanges");
        if (kept == null) {
            return;
        }
        for (Object o : kept) {
            JsonObject e = (JsonObject) o;
            ExchangeRow row = new ExchangeRow();
            row.copy(base);
            row.exchangeId = e.getString("exchangeId");
            row.routeId = e.getString("routeId");
            row.status = e.getBooleanOrDefault("failed", false)
                    ? "FAILED"
                    : e.getBooleanOrDefault("handled", false) ? "HANDLED"
                    : e.getBooleanOrDefault("redelivered", false) ? "RECOVERED" : "OK";
            row.elapsed = e.getLongOrDefault("elapsed", 0);
            row.timestamp = e.getLongOrDefault("timestamp", 0);
            row.since = row.timestamp > 0 ? TimeUtils.printSince(row.timestamp) : "";
            JsonArray sends = (JsonArray) e.get("endpointSends");
            row.sends = sends != null ? sends.size() : 0;
            row.chaosRule = e.getString("chaosRule");
            row.endpointUri = e.getString("fromEndpointUri");
            rows.add(row);
        }
    }

    private static void addErrors(Row base, JsonObject timeline, List<ErrorRow> rows) {
        JsonArray list = (JsonArray) timeline.get("errors");
        if (list == null) {
            return;
        }
        long end = timeline.getLongOrDefault("end", 0);
        for (Object o : list) {
            JsonObject e = (JsonObject) o;
            ErrorRow row = new ErrorRow();
            row.copy(base);
            row.routeId = e.getString("routeId");
            row.exception = e.getString("exception");
            Object status = e.get("statusCode");
            row.statusCode = status != null ? status.toString() : "";
            row.chaosRule = e.getString("chaosRule");
            long[] counts = values(e, "counts");
            row.count = sum(counts);
            for (int i = counts.length - 1; i >= 0; i--) {
                if (counts[i] > 0) {
                    long second = end - (counts.length - 1 - i);
                    row.last = TimeUtils.printSince(second * 1000);
                    break;
                }
            }
            row.spark = spark(counts, SPARK_WIDTH);
            rows.add(row);
        }
    }

    private static long[] values(JsonObject o, String key) {
        JsonArray arr = (JsonArray) o.get(key);
        if (arr == null) {
            return new long[0];
        }
        long[] answer = new long[arr.size()];
        for (int i = 0; i < answer.length; i++) {
            Object v = arr.get(i);
            answer[i] = v instanceof Number n ? n.longValue() : 0;
        }
        return answer;
    }

    private static long at(long[] values, int i) {
        return i < values.length ? values[i] : 0;
    }

    private static long sum(long[] values) {
        long sum = 0;
        for (long v : values) {
            sum += v;
        }
        return sum;
    }

    /** The values as a sparkline of at most the given width, a character taking the max of its seconds. */
    static String spark(long[] values, int width) {
        if (values.length == 0) {
            return "";
        }
        int columns = Math.min(width, values.length);
        long[] col = new long[columns];
        long max = 0;
        for (int i = 0; i < values.length; i++) {
            int c = (int) ((long) i * columns / values.length);
            col[c] = Math.max(col[c], values[i]);
            max = Math.max(max, values[i]);
        }
        StringBuilder sb = new StringBuilder(columns);
        for (long v : col) {
            int level = max == 0 || v == 0 ? 0 : (int) Math.max(1, Math.round(v * 8.0 / max));
            sb.append(SPARK.charAt(level));
        }
        return sb.toString();
    }

    private void printRoutes(List<RouteRow> rows) {
        if (jsonOutput) {
            printer().println(Jsoner.serialize(rows.stream().map(r -> {
                JsonObject jo = r.json();
                jo.put("routeId", r.routeId);
                if (r.from != null) {
                    jo.put("from", r.from);
                }
                jo.put("total", r.total);
                jo.put("completed", r.completed);
                jo.put("recovered", r.recovered);
                jo.put("handled", r.handled);
                jo.put("failed", r.failed);
                jo.put("redeliveryAttempts", r.attempts);
                jo.put("meanMillis", r.mean);
                jo.put("p95Millis", r.p95);
                jo.put("maxMillis", r.max);
                return jo;
            }).collect(Collectors.toList())));
            return;
        }
        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.name),
                new Column().header("ROUTE").dataAlign(HorizontalAlign.LEFT).maxWidth(25, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.routeId),
                new Column().header("TOTAL").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> Long.toString(r.total)),
                new Column().header("RECOVERED").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> count(r.recovered)),
                new Column().header("HANDLED").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> count(r.handled)),
                new Column().header("FAILED").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> count(r.failed)),
                new Column().header("ATTEMPTS").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> count(r.attempts)),
                new Column().header("MEAN").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> r.mean + "ms"),
                new Column().header("MAX").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> r.max > 0 ? r.max + "ms" : ""),
                new Column().header("EXCHANGES/S").dataAlign(HorizontalAlign.LEFT).with(r -> r.spark))));
    }

    private void printExchanges(List<ExchangeRow> rows) {
        if (jsonOutput) {
            printer().println(Jsoner.serialize(rows.stream().map(r -> {
                JsonObject jo = r.json();
                jo.put("exchangeId", r.exchangeId);
                jo.put("routeId", r.routeId);
                jo.put("status", r.status);
                jo.put("elapsed", r.elapsed);
                jo.put("sends", r.sends);
                jo.put("since", r.since);
                if (r.chaosRule != null) {
                    jo.put("chaosRule", r.chaosRule);
                }
                if (r.endpointUri != null) {
                    jo.put("endpointUri", r.endpointUri);
                }
                return jo;
            }).collect(Collectors.toList())));
            return;
        }
        // Flexible column: ENDPOINT (40/140)
        // Fixed columns: PID(8)+NAME(30)+AGE(8)+EXCHANGE(40)+ROUTE(25)+STATUS(9)+ELAPSED(8)+SENDS(7)+SINCE(8) ~= 143
        int fixedW = 143;
        int numCols = 11;
        int tw = terminalWidth();
        int uriW = TerminalWidthHelper.flexWidth(tw, fixedW, TerminalWidthHelper.noBorderOverhead(numCols), 20, 40);
        int uriWideW = TerminalWidthHelper.flexWidth(tw, fixedW, TerminalWidthHelper.noBorderOverhead(numCols), 20, 140);
        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.name),
                new Column().header("AGE").headerAlign(HorizontalAlign.CENTER).with(r -> r.age),
                new Column().header("EXCHANGE").dataAlign(HorizontalAlign.LEFT).maxWidth(40, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.exchangeId),
                new Column().header("ROUTE").dataAlign(HorizontalAlign.LEFT).maxWidth(25, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.routeId),
                new Column().header("STATUS").dataAlign(HorizontalAlign.CENTER)
                        .with(r -> r.status),
                new Column().header("ELAPSED").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> r.elapsed + "ms"),
                new Column().header("SENDS").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> r.sends > 0 ? String.valueOf(r.sends) : ""),
                new Column().header("SINCE").headerAlign(HorizontalAlign.CENTER)
                        .with(r -> r.since),
                new Column().header("CHAOS").dataAlign(HorizontalAlign.LEFT)
                        .with(r -> r.chaosRule != null ? r.chaosRule : ""),
                new Column().header("ENDPOINT").visible(!wideUri).dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(uriW, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(this::getUri),
                new Column().header("ENDPOINT").visible(wideUri).dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(uriWideW, OverflowBehaviour.NEWLINE)
                        .with(this::getUri))));
    }

    private void printErrors(List<ErrorRow> rows) {
        if (jsonOutput) {
            printer().println(Jsoner.serialize(rows.stream().map(r -> {
                JsonObject jo = r.json();
                jo.put("routeId", r.routeId);
                jo.put("exception", r.exception);
                if (!r.statusCode.isEmpty()) {
                    jo.put("statusCode", Integer.valueOf(r.statusCode));
                }
                if (r.chaosRule != null) {
                    jo.put("chaosRule", r.chaosRule);
                }
                jo.put("count", r.count);
                jo.put("last", r.last);
                return jo;
            }).collect(Collectors.toList())));
            return;
        }
        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.name),
                new Column().header("ROUTE").dataAlign(HorizontalAlign.LEFT).maxWidth(25, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.routeId != null ? r.routeId : ""),
                new Column().header("EXCEPTION").dataAlign(HorizontalAlign.LEFT).maxWidth(50, OverflowBehaviour.ELLIPSIS_LEFT)
                        .with(r -> r.exception),
                new Column().header("STATUS").headerAlign(HorizontalAlign.CENTER).dataAlign(HorizontalAlign.CENTER)
                        .with(r -> r.statusCode),
                new Column().header("CHAOS").dataAlign(HorizontalAlign.LEFT)
                        .with(r -> r.chaosRule != null ? r.chaosRule : ""),
                new Column().header("COUNT").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> Long.toString(r.count)),
                new Column().header("LAST").headerAlign(HorizontalAlign.CENTER).with(r -> r.last),
                new Column().header("ERRORS/S").dataAlign(HorizontalAlign.LEFT).with(r -> r.spark))));
    }

    private static String count(long n) {
        return n > 0 ? Long.toString(n) : "";
    }

    private String getUri(ExchangeRow r) {
        String u = r.endpointUri;
        if (u == null) {
            return "";
        }
        if (shortUri) {
            int pos = u.indexOf('?');
            if (pos > 0) {
                u = u.substring(0, pos);
            }
        }
        return u;
    }

    static class Row {
        String pid;
        String name;
        long uptime;
        String age;

        void copy(Row base) {
            pid = base.pid;
            name = base.name;
            uptime = base.uptime;
            age = base.age;
        }

        JsonObject json() {
            JsonObject jo = new JsonObject();
            jo.put("pid", JsonValues.pid(pid));
            jo.put("name", name);
            jo.put("age", age);
            jo.put("uptime", uptime);
            return jo;
        }
    }

    static class RouteRow extends Row {
        String routeId;
        String from;
        long total;
        long completed;
        long recovered;
        long handled;
        long failed;
        long attempts;
        long mean;
        long p95;
        long max;
        String spark;
    }

    static class ExchangeRow extends Row {
        String exchangeId;
        String routeId;
        String status;
        long elapsed;
        long timestamp;
        String since;
        int sends;
        String chaosRule;
        String endpointUri;
    }

    static class ErrorRow extends Row {
        String routeId;
        String exception;
        String statusCode;
        String chaosRule;
        long count;
        String last = "";
        String spark;
    }
}
