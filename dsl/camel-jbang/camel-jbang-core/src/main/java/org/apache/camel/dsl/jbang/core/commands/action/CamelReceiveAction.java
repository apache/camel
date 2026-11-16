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

import java.io.IOException;
import java.io.LineNumberReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Mixin;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.CommandHelper;
import org.apache.camel.dsl.jbang.core.commands.DurationMixin;
import org.apache.camel.dsl.jbang.core.commands.MavenResolverMixin;
import org.apache.camel.dsl.jbang.core.commands.Run;
import org.apache.camel.dsl.jbang.core.common.ActionClient;
import org.apache.camel.dsl.jbang.core.common.ActionClient.ActionResult;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.CopyableRow;
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
import org.apache.camel.dsl.jbang.core.common.ListingFormat;
import org.apache.camel.dsl.jbang.core.common.PidNameAgeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.dsl.jbang.core.common.RowSort;
import org.apache.camel.dsl.jbang.core.common.TerminalWidthHelper;
import org.apache.camel.dsl.jbang.core.common.TimeoutConverter;
import org.apache.camel.dsl.jbang.run.ConfiguredRun;
import org.apache.camel.main.KameletMain;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.jline.jansi.Ansi;

import static org.apache.camel.dsl.jbang.core.common.CamelCommandHelper.valueAsStringPretty;

@CommandDefinition(name = "receive", description = "Receive and dump messages from remote endpoints", generateHelp = true,
                   footer = {
                           "Examples:",
                           "  camel cmd receive --endpoint=seda:foo",
                           "  camel cmd receive --endpoint=seda:foo --timeout=30000" })
public class CamelReceiveAction extends ActionBaseCommand {

    private static final int NAME_MAX_WIDTH = 25;
    private static final int NAME_MIN_WIDTH = 10;

    private static final RowSort<StatusRow> ORDER = RowSort.<StatusRow> integration(r -> r.pid, r -> r.name, r -> r.uptime);

    public static class PrefixCompletionCandidates extends CompletionCandidates {

        public PrefixCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("auto", "true", "false").iterator();
        }
    }

    public static class ActionCompletionCandidates extends CompletionCandidates {

        public ActionCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("dump", "start", "stop", "status", "clear").iterator();
        }
    }

    public static class OutputFormatCompletionCandidates extends CompletionCandidates {

        public OutputFormatCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("text", "json").iterator();
        }
    }

    @Argument(description = "To use an existing running Camel integration for receiving the message (name or pid)")
    String name;

    @Option(name = "properties",
            description = "comma separated list of properties file (only applicable when NOT using an existing running Camel)"
                          +
                          " (ex. /path/to/file.properties,/path/to/other.properties")
    String propertiesFiles;

    @OptionList(name = "prop", aliases = { "property" }, valueSeparator = OptionList.NO_SEPARATOR,
                description = "Additional properties; override existing (only applicable when NOT using an existing running Camel)")
    String[] property;

    @Option(name = "action", description = "Action to start, stop, clear, status, or dump messages", defaultValue = "status",
            completer = ActionCompletionCandidates.class)
    String action;

    @Option(name = "endpoint", aliases = { "uri" },
            description = "Endpoint to receive messages from (can be uri or pattern to refer to existing endpoint)")
    String endpoint;

    @Option(name = "sort", description = "Sort by pid, name or age for showing status of messages", defaultValue = "pid",
            completer = PidNameAgeCompletionCandidates.class)
    String sort;

    @Option(name = "follow", hasValue = false, description = "Keep following and outputting new messages (press enter to exit)."
                                                             + " Without an interactive terminal the default is false, unless --duration is given.",
            defaultValue = "true")
    boolean follow = true;

    @Mixin
    DurationMixin durationOption = new DurationMixin();

    @Option(name = "prefix",
            description = "Print prefix with running Camel integration name. auto=only prefix when running multiple integrations. true=always prefix. false=prefix off.",
            defaultValue = "auto", completer = PrefixCompletionCandidates.class)
    String prefix = "auto";

    @Option(name = "tail",
            description = "The number of messages from the end to show. Use -1 to read from the beginning. Use 0 to read only new lines. Defaults to showing all messages from beginning.",
            defaultValue = "-1")
    int tail = -1;

    @Option(name = "since",
            description = "Return messages newer than a relative duration like 5s, 2m, or 1h. The value is in seconds if no unit specified.")
    String since;

    @OptionList(name = "find", valueSeparator = OptionList.NO_SEPARATOR, optionalValue = true,
                description = "Find and highlight matching text (ignore case).")
    String[] find;

    @OptionList(name = "grep", valueSeparator = OptionList.NO_SEPARATOR, optionalValue = true,
                description = "Filter messages to only output matching text (ignore case).")
    String[] grep;

    @Option(name = "timeout",
            description = "Timeout waiting for message to be received, in millis or as a duration such as 30s",
            defaultValue = "20000", converter = TimeoutConverter.class)
    long timeout = 20000;

    @Option(name = "show-exchange-properties", hasValue = false, description = "Show exchange properties in received messages",
            defaultValue = "false")
    boolean showExchangeProperties;

    @Option(name = "show-exchange-variables", hasValue = false, description = "Show exchange variables in received messages",
            defaultValue = "false")
    boolean showExchangeVariables;

    @Option(name = "show-headers", hasValue = false, description = "Show message headers in received messages",
            defaultValue = "true")
    boolean showHeaders = true;

    @Option(name = "show-body", hasValue = false, description = "Show message body in received messages", defaultValue = "true")
    boolean showBody = true;

    @Option(name = "only-body", hasValue = false, description = "Show only message body in received messages",
            defaultValue = "false")
    boolean onlyBody;

    @Option(name = "logging-color", hasValue = false, description = "Use colored logging", defaultValue = "true")
    boolean loggingColor = true;

    @Option(name = "compact", hasValue = false, description = "Compact output (no empty line separating messages)",
            defaultValue = "true")
    boolean compact = true;

    @Option(name = "short-uri", hasValue = false, description = "List endpoint URI without query parameters (short)")
    boolean shortUri;

    @Option(name = "wide-uri", hasValue = false, description = "List endpoint URI in full details")
    boolean wideUri;

    @Option(name = "mask", hasValue = false,
            description = "Whether to mask endpoint URIs to avoid printing sensitive information such as password or access keys")
    boolean mask;

    @Option(name = "pretty", hasValue = false, description = "Pretty print message body when using JSon or XML format")
    boolean pretty;

    @Option(name = "output", description = "Output format (${COMPLETION-CANDIDATES})",
            completer = OutputFormatCompletionCandidates.class)
    private String output;

    @Mixin
    MavenResolverMixin mavenResolver;

    private volatile long pid;

    String findAnsi;
    Pattern[] grepPatterns;
    Pattern[] findPatterns;
    private int nameMaxWidth;
    private boolean prefixShown;
    private MessageTableHelper tableHelper;
    private final Map<String, Ansi.Color> nameColors = new HashMap<>();

    public CamelReceiveAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        // no ANSI codes of colors in a file or a pipe, unless --logging-color is given
        loggingColor = colorOutput(loggingColor);
        boolean autoDump = false;
        if (endpoint != null) {
            // if using --endpoint then action should be start and auto-dump
            action = "start";
            autoDump = true;
        }

        if ("dump".equals(action)) {
            return doDumpCall();
        } else if ("status".equals(action)) {
            return doStatusCall();
        } else if (!"start".equals(action) && !"stop".equals(action) && !"clear".equals(action)) {
            throw CliException.usage("Unknown action: " + action + " (use dump, start, stop, status or clear)");
        }

        if (name != null) {
            return doCall(findProcesses(name), autoDump);
        } else {
            // are there only 1 pid running then use that
            List<CamelProcess> processes = ProcessFinder.find("*");
            if (processes.size() == 1) {
                this.pid = processes.get(0).pid();
                return doCall(processes, autoDump);
            }
        }
        // okay fallback and run camel locally to connect to external system
        return doCallLocal(autoDump);
    }

    private Integer doCall(List<CamelProcess> processes, boolean autoDump) throws Exception {
        int exit = 0;
        for (CamelProcess process : processes) {
            if ("clear".equals(action)) {
                Path f = getReceiveFile(Long.toString(process.pid()));
                if (Files.exists(f)) {
                    // empty: the connector appends a line of JSON per batch, which an empty JSON object without
                    // a new line would join, and that batch would be lost (CAMEL-26911)
                    Files.writeString(f, "");
                }
            } else {
                info(("stop".equals(action) ? "Stopping" : "Starting") + " to receive messages from existing Camel: "
                     + process.name() + " (pid: " + process.pid() + ")");
                int code = showStatus(ActionClient.execute(process, "receive", this::receiveOptions, timeout + 10000));
                if (exit == 0) {
                    exit = code;
                }
            }
        }

        if (exit == 0 && autoDump) {
            return doDumpCall();
        }

        return exit;
    }

    private Integer doCallLocal(boolean autoDump) throws Exception {
        AtomicReference<KameletMain> ref = new AtomicReference<>();
        Run run = new Run(this.getMain()) {
            @Override
            protected int runKameletMain(ConfiguredRun run) throws Exception {
                ref.set(run.getMain());
                return super.runKameletMain(run);
            }
        };
        run.empty = true;
        run.propertiesFiles = propertiesFiles;
        run.property = property;
        run.mavenResolver = mavenResolver;

        // spawn thread that starts receiving, once the Camel of this process has started
        final CountDownLatch latch = new CountDownLatch(1);
        this.pid = ProcessHandle.current().pid();
        JsonObject request = new JsonObject();
        request.put("action", "receive");
        receiveOptions(request);
        AtomicInteger status = new AtomicInteger();
        Thread t = new Thread("CamelJBangSendStatus") {
            @Override
            public void run() {
                try {
                    status.set(showStatus(ActionClient.execute(pid, request, timeout + 10000)));
                    if (status.get() != 0) {
                        // some kind of error so exit
                        return;
                    }
                    if (autoDump) {
                        doDumpCall();
                    }
                } catch (Exception e) {
                    printer().printErr("Cannot receive messages: " + e.getMessage());
                    status.set(CliExitCode.FAILURE);
                } finally {
                    latch.countDown();
                    // signal to main we are complete
                    KameletMain main = ref.get();
                    if (main != null) {
                        main.completed();
                    }
                }
            }
        };
        // keep thread running as we need it to show the status before terminating
        t.start();
        info("Starting to receive messages by connecting to external system using local process (pid: " + pid + ")");

        Integer exit = run.call();
        latch.await();

        if (exit != null && exit != 0) {
            return exit;
        }
        return status.get();
    }

    /**
     * The options of the receive action.
     */
    protected void receiveOptions(JsonObject root) {
        if ("start".equals(action)) {
            root.put("enabled", "true");
            if (endpoint != null) {
                root.put("endpoint", endpoint);
            } else {
                root.put("endpoint", "*");
            }
        } else if ("stop".equals(action)) {
            root.put("enabled", "false");
        }
    }

    /**
     * Reports how starting (or stopping) to receive went.
     *
     * @return the exit code: 1 when the integration failed to receive, 124 when it did not answer in time
     */
    protected int showStatus(ActionResult result) {
        if (!result.isOk()) {
            printer().printErr("Error starting to receive messages due to: " + result.error());
            return result.exitCode();
        }
        JsonObject jo = result.reply();
        String error = jo != null ? jo.getString("error") : null;
        if (error != null) {
            error = Jsoner.unescape(error);
            String url = jo.getString("url");
            List<String> stackTrace = jo.getCollection("stackTrace");
            if (url != null) {
                printer().printErr("Error starting to receive messages from: " + url + " due to: " + error);
            } else {
                printer().printErr("Error starting to receive messages due to: " + error);
            }
            if (stackTrace != null && !stackTrace.isEmpty()) {
                printer().printDiagnostic(StringHelper.fillChars('-', 120));
                printer().printDiagnostic(StringHelper.padString(1, 55) + "STACK-TRACE");
                printer().printDiagnostic(StringHelper.fillChars('-', 120));
                StringBuilder sb = new StringBuilder();
                for (String s : stackTrace) {
                    sb.append(String.format("\t%s%n", s));
                }
                printer().printDiagnostic(String.valueOf(sb));
            }
            return CliExitCode.FAILURE;
        }
        if ("start".equals(action) && endpoint != null && !"*".equals(endpoint) && jo != null
                && jo.getString("url") == null) {
            // the integration says which endpoint it receives from: it started to receive from none (CAMEL-26929)
            printer().printErr("No endpoint matching: " + endpoint);
            return CliExitCode.NOT_FOUND;
        }
        return CliExitCode.OK;
    }

    /**
     * A line that says what the command does: not on stdout when the output is JSON, which then has only the JSON.
     */
    private void info(String line) {
        if ("json".equalsIgnoreCase(output)) {
            printer().printDiagnostic(line);
        } else {
            printer().println(line);
        }
    }

    protected Integer doStatusCall() {
        List<StatusRow> rows = new ArrayList<>();

        List<Long> pids;
        if (pid != 0) {
            pids = List.of(pid);
        } else {
            // a name that matches no running integration is not found
            pids = findProcesses(name).stream().map(CamelProcess::pid).toList();
        }
        ProcessHandle.allProcesses()
                .filter(ph -> pids.contains(ph.pid()))
                .forEach(ph -> {
                    JsonObject root = loadStatus(ph.pid());
                    if (root != null) {
                        StatusRow row = new StatusRow();
                        JsonObject context = (JsonObject) root.get("context");
                        if (context == null) {
                            return;
                        }
                        row.name = context.getString("name");
                        if ("CamelJBang".equals(row.name)) {
                            row.name = ProcessHelper.extractName(root, ph);
                        }
                        row.pid = Long.toString(ph.pid());
                        row.uptime = extractSince(ph);
                        row.age = TimeUtils.printSince(row.uptime);
                        JsonObject jo = root.getMap("receive");
                        if (jo != null) {
                            row.enabled = jo.getBoolean("enabled");
                            row.counter = jo.getLong("total");
                            row.firstTimestamp = jo.getLongOrDefault("firstTimestamp", 0);
                            row.lastTimestamp = jo.getLongOrDefault("lastTimestamp", 0);
                            JsonArray arr = jo.getCollection("endpoints");
                            if (arr != null) {
                                for (Object e : arr) {
                                    jo = (JsonObject) e;
                                    row.uri = jo.getString("uri");
                                    if (mask) {
                                        row.uri = URISupport.sanitizeUri(row.uri);
                                    }
                                    rows.add(row);
                                    row = row.copy();
                                }
                            } else {
                                rows.add(row);
                            }
                        }
                    }
                });

        rows.sort(ORDER.comparator(sort));

        if ("json".equalsIgnoreCase(output)) {
            // --output=json printed the table (CAMEL-26929)
            JsonOutput.printList(printer(), rows.stream().map(CamelReceiveAction::statusAsJson).toList());
        } else if (!rows.isEmpty()) {
            int tw = terminalWidth();
            int fixedWidth = 10 + 30 + 10 + 10 + 8 + 10; // PID + NAME + AGE + STATUS + TOTAL + SINCE (approx)
            int borderOverhead = TerminalWidthHelper.noBorderOverhead(7);
            int endpointWidth = TerminalWidthHelper.flexWidth(tw, fixedWidth, borderOverhead, 20, wideUri ? 140 : 90);

            printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                    new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                    new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(r -> r.name),
                    new Column().header("AGE").headerAlign(HorizontalAlign.CENTER).with(r -> r.age),
                    new Column().header("STATUS").with(this::getStatus),
                    new Column().header("TOTAL").with(r -> r.enabled ? "" + r.counter : ""),
                    new Column().header("SINCE").headerAlign(HorizontalAlign.CENTER)
                            .with(this::getMessageAgo),
                    new Column().header("ENDPOINT").visible(!wideUri).dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(endpointWidth, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(this::getEndpointUri),
                    new Column().header("ENDPOINT").visible(wideUri).dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(endpointWidth, OverflowBehaviour.NEWLINE)
                            .with(r -> r.uri))));
        }

        return 0;
    }

    private static JsonObject statusAsJson(StatusRow r) {
        JsonObject jo = new JsonObject();
        jo.put("pid", Long.parseLong(r.pid));
        jo.put("name", r.name);
        jo.put("uptime", r.uptime);
        jo.put("enabled", r.enabled);
        jo.put("total", r.counter);
        if (r.firstTimestamp > 0) {
            jo.put("firstTimestamp", r.firstTimestamp);
        }
        if (r.lastTimestamp > 0) {
            jo.put("lastTimestamp", r.lastTimestamp);
        }
        if (r.uri != null) {
            jo.put("endpoint", r.uri);
        }
        return jo;
    }

    private String getStatus(StatusRow r) {
        if (r.enabled) {
            return "Enabled";
        }
        return "Disabled";
    }

    protected Integer doDumpCall() throws Exception {
        follow = isFollowing(follow, durationOption.getDuration());
        // setup table helper
        tableHelper = new MessageTableHelper();
        tableHelper.setPretty(pretty);
        tableHelper.setLoggingColor(loggingColor);

        Map<Long, Pid> pids = new LinkedHashMap<>();

        // before reading, as an integration that starts later is read with them too (once, as they quote in place)
        if (find != null && findPatterns == null) {
            findAnsi = Ansi.ansi().fg(Ansi.Color.BLACK).bg(Ansi.Color.YELLOW).a("$0").reset().toString();
            findPatterns = quoteAndCompilePatterns(find);
        }
        if (grep != null && grepPatterns == null) {
            findAnsi = Ansi.ansi().fg(Ansi.Color.BLACK).bg(Ansi.Color.YELLOW).a("$0").reset().toString();
            grepPatterns = quoteAndCompilePatterns(grep);
        }

        // find new pids
        updatePids(pids);
        if (pids.isEmpty() && !follow) {
            // shown once: a name that matches no running integration is not found
            findProcesses(name);
        }
        if (!pids.isEmpty()) {
            // read existing received files (skip by tail/since)
            Date limit = null;
            if (since != null) {
                long millis;
                if (StringHelper.isDigit(since)) {
                    // is in seconds by default
                    millis = TimeUtils.toMilliSeconds(since) * 1000;
                } else {
                    millis = TimeUtils.toMilliSeconds(since);
                }
                limit = new Date(System.currentTimeMillis() - millis);
            }
            // dump existing messages
            if (tail != 0) {
                tailReceiveFiles(pids, tail);
                dumpReceiveFiles(pids, tail, limit);
            }
        }

        if (follow) {
            boolean waitMessage = true;
            // until Enter is pressed, or the --duration has passed
            CommandHelper.RunUntil running = CommandHelper.RunUntil.start(durationOption.getDuration());
            boolean more = true;
            boolean init = true;
            StopWatch watch = new StopWatch();
            do {
                if (pids.isEmpty()) {
                    if (waitMessage) {
                        if (!"json".equals(output)) {
                            printer().println("Waiting for messages ...");
                        }
                        waitMessage = false;
                    }
                    Thread.sleep(500);
                    updatePids(pids);
                } else {
                    waitMessage = true;
                    if (watch.taken() > 500) {
                        // check for new messages
                        updatePids(pids);
                        watch.restart();
                    }
                    int lines = readReceiveFiles(pids);
                    if (lines > 0) {
                        more = dumpReceiveFiles(pids, 0, null);
                        init = false;
                    } else if (lines == 0) {
                        if (init) {
                            if (!"json".equals(output)) {
                                printer().println("Waiting for messages ...");
                            }
                            init = false;
                        }
                        Thread.sleep(100);
                    } else {
                        break;
                    }
                }
            } while (more && running.isRunning());
        }

        return 0;
    }

    private void tailReceiveFiles(Map<Long, Pid> pids, int tail) throws Exception {
        for (Pid pid : pids.values()) {
            Path file = getReceiveFile(pid.pid);
            if (Files.exists(file) && Files.size(file) > 0) {
                pid.reader = new LineNumberReader(Files.newBufferedReader(file));
                String line;
                if (tail <= 0) {
                    pid.fifo = new ArrayDeque<>();
                } else {
                    pid.fifo = new ArrayBlockingQueue<>(tail);
                }
                do {
                    line = pid.reader.readLine();
                    if (line != null) {
                        while (!pid.fifo.offer(line)) {
                            pid.fifo.poll();
                        }
                    }
                } while (line != null);
            }
        }
    }

    private void updatePids(Map<Long, Pid> rows) {
        List<Long> pids;
        if (name != null) {
            pids = findPids(name);
        } else if (pid != 0) {
            // the integration this command started to receive in
            pids = List.of(pid);
        } else {
            pids = findPids("*");
        }
        ProcessHandle.allProcesses()
                .filter(ph -> pids.contains(ph.pid()))
                .forEach(ph -> {
                    JsonObject root = loadStatus(ph.pid());
                    if (root != null) {
                        Pid row = new Pid();
                        row.pid = Long.toString(ph.pid());
                        JsonObject context = (JsonObject) root.get("context");
                        if (context == null) {
                            return;
                        }
                        row.name = context.getString("name");
                        if ("CamelJBang".equals(row.name)) {
                            row.name = ProcessHelper.extractName(root, ph);
                        }
                        int len = row.name.length();
                        if (len < NAME_MIN_WIDTH) {
                            len = NAME_MIN_WIDTH;
                        }
                        if (len > NAME_MAX_WIDTH) {
                            len = NAME_MAX_WIDTH;
                        }
                        if (len > nameMaxWidth) {
                            nameMaxWidth = len;
                        }
                        if (!rows.containsKey(ph.pid())) {
                            rows.put(ph.pid(), row);
                        }
                    }
                });

        // remove pids that are no long active from the rows
        Set<Long> remove = new HashSet<>();
        for (long pid : rows.keySet()) {
            if (!pids.contains(pid)) {
                remove.add(pid);
            }
        }
        for (long pid : remove) {
            rows.remove(pid);
        }
    }

    private int readReceiveFiles(Map<Long, Pid> pids) throws Exception {
        int lines = 0;

        for (Pid pid : pids.values()) {
            if (pid.reader == null) {
                Path file = getReceiveFile(pid.pid);
                if (Files.exists(file)) {
                    pid.reader = new LineNumberReader(Files.newBufferedReader(file));
                    if (tail == 0) {
                        // only read new lines so forward to end of reader
                        long size = Files.size(file);
                        pid.reader.skip(size);
                    }
                }
            }
            if (pid.reader != null) {
                String line;
                do {
                    try {
                        line = pid.reader.readLine();
                        if (line != null) {
                            lines++;
                            // switch fifo to be unlimited as we use it for new messages
                            if (pid.fifo == null || pid.fifo instanceof ArrayBlockingQueue) {
                                pid.fifo = new ArrayDeque<>();
                            }
                            pid.fifo.offer(line);
                        }
                    } catch (IOException e) {
                        // ignore
                        line = null;
                    }
                } while (line != null);
            }
        }

        return lines;
    }

    private List<Row> parseReceiveLine(Pid pid, String line) {
        JsonObject root = null;
        try {
            root = (JsonObject) Jsoner.deserialize(line);
        } catch (Exception e) {
            // ignore
        }
        if (root != null) {
            List<Row> rows = new ArrayList<>();
            JsonArray arr = root.getCollection("messages");
            if (arr != null) {
                for (Object o : arr) {
                    Row row = new Row(pid);
                    row.pid = pid.pid;
                    row.name = pid.name;
                    JsonObject jo = (JsonObject) o;
                    row.json = jo;
                    row.uid = jo.getLong("uid");
                    String uri = jo.getString("endpointUri");
                    if (uri != null) {
                        row.endpoint = new JsonObject();
                        if (mask) {
                            uri = URISupport.sanitizeUri(uri);
                            jo.put("endpointUri", uri);
                        }
                        row.endpoint.put("endpoint", uri);
                        row.endpoint.put("remote", jo.getBooleanOrDefault("remoteEndpoint", true));
                    }
                    JsonObject es = jo.getMap("endpointService");
                    if (es != null) {
                        row.endpointService = es;
                    }
                    Long ts = jo.getLong("timestamp");
                    if (ts != null) {
                        row.timestamp = ts;
                    }
                    row.message = jo.getMap("message");
                    if (!"json".equalsIgnoreCase(output)) {
                        // the table of the message does not show them
                        row.message.remove("exchangeId");
                        row.message.remove("exchangePattern");
                    }
                    if (onlyBody) {
                        row.message.remove("exchangeProperties");
                        row.message.remove("exchangeVariables");
                        row.message.remove("headers");
                        row.message.remove("messageType");
                    } else {
                        if (!showExchangeProperties) {
                            row.message.remove("exchangeProperties");
                        }
                        if (!showExchangeVariables) {
                            row.message.remove("exchangeVariables");
                        }
                        if (!showHeaders) {
                            row.message.remove("headers");
                        }
                        if (!showBody) {
                            row.message.remove("body");
                        }
                    }
                    rows.add(row);
                }
            }
            return rows;
        }
        return null;
    }

    private boolean dumpReceiveFiles(Map<Long, Pid> pids, int tail, Date limit) {
        Set<String> names = new HashSet<>();
        List<Row> rows = new ArrayList<>();
        for (Pid pid : pids.values()) {
            Queue<String> queue = pid.fifo;
            if (queue != null) {
                for (String l : queue) {
                    names.add(pid.name);
                    List<Row> parsed = parseReceiveLine(pid, l);
                    if (parsed != null && !parsed.isEmpty()) {
                        rows.addAll(parsed);
                    }
                }
                pid.fifo.clear();
            }
        }

        // only sort if there are multiple Camels running
        if (names.size() > 1) {
            // sort lines
            final Map<String, Long> lastTimestamp = new HashMap<>();
            rows.sort((r1, r2) -> {
                long t1 = r1.timestamp;
                long t2 = r2.timestamp;
                if (t1 == 0) {
                    t1 = lastTimestamp.getOrDefault(r1.name, 0L);
                }
                if (t2 == 0) {
                    t2 = lastTimestamp.getOrDefault(r2.name, 0L);
                }
                if (t1 == 0 && t2 == 0) {
                    return 0;
                } else if (t1 == 0) {
                    return -1;
                } else if (t2 == 0) {
                    return 1;
                }
                lastTimestamp.put(r1.name, t1);
                lastTimestamp.put(r2.name, t2);
                return Long.compare(t1, t2);
            });
        }
        if (tail > 0) {
            // cut according to tail
            int pos = rows.size() - tail;
            if (pos > 0) {
                rows = rows.subList(pos, rows.size());
            }
        }

        for (Row r : rows) {
            printDump(r.name, pids.size(), r, limit);
        }
        return true;
    }

    private boolean isValidGrep(String line) {
        if (grep == null) {
            return true;
        }
        for (Pattern p : grepPatterns) {
            if (p.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidSince(Date limit, long timestamp) {
        if (limit == null || timestamp == 0) {
            return true;
        }
        Date row = new Date(timestamp);
        return row.compareTo(limit) >= 0;
    }

    protected void printDump(String name, int pids, Row row, Date limit) {
        if ("json".equalsIgnoreCase(output)) {
            // a message per line (a line of the file holds the messages the integration received since the line before)
            String dump = row.json.toJson();
            if (!isValidSince(limit, row.timestamp) || !isValidGrep(dump)) {
                return;
            }
            if (pretty) {
                dump = valueAsStringPretty(dump, loggingColor);
            }
            printer().println(dump);
            if (!compact) {
                printer().println();
            }
            return;
        }

        if (!prefixShown) {
            // compute whether to show prefix or not
            if ("false".equals(prefix) || "auto".equals(prefix) && pids <= 1) {
                name = null;
            }
        }
        prefixShown = name != null;

        String data = getDataAsTable(row);
        boolean valid = isValidSince(limit, row.timestamp) && isValidGrep(data);
        if (!valid) {
            return;
        }

        String nameWithPrefix = null;
        if (name != null) {
            if (loggingColor) {
                Ansi.Color color = nameColors.get(name);
                if (color == null) {
                    // grab a new color
                    int idx = (nameColors.size() % 6) + 1;
                    color = Ansi.Color.values()[idx];
                    nameColors.put(name, color);
                }
                String n = String.format("%-" + nameMaxWidth + "s", name);
                nameWithPrefix = Ansi.ansi().fg(color).a(n).a("| ").reset().toString();
            } else {
                nameWithPrefix = String.format("%-" + nameMaxWidth + "s", name) + "| ";
            }
            printer().print(nameWithPrefix);
        }
        // header
        String header = String.format("Received Message: (%s)", row.uid);
        if (loggingColor) {
            printer().println(Ansi.ansi().fgGreen().a(header).reset().toString());
        } else {
            printer().println(header);
        }
        String[] lines = data.split(System.lineSeparator());
        if (lines.length > 0) {
            for (String line : lines) {
                if (findPatterns != null) {
                    for (Pattern p : findPatterns) {
                        line = p.matcher(line).replaceAll(findAnsi);
                    }
                }
                if (grepPatterns != null) {
                    for (Pattern p : grepPatterns) {
                        line = p.matcher(line).replaceAll(findAnsi);
                    }
                }
                if (nameWithPrefix != null) {
                    printer().print(nameWithPrefix);
                }
                printer().print(" ");
                printer().println(line);
            }
            if (!compact) {
                if (nameWithPrefix != null) {
                    printer().println(nameWithPrefix);
                } else {
                    // empty line
                    printer().println();
                }
            }
        }
    }

    private String getDataAsTable(Row r) {
        return tableHelper.getDataAsTable(null, null, null, r.endpoint, r.endpointService, r.message, null);
    }

    protected String getEndpointUri(StatusRow r) {
        return ListingFormat.uri(r.uri, shortUri);
    }

    protected String getMessageAgo(StatusRow r) {
        if (r.lastTimestamp > 0) {
            return TimeUtils.printSince(r.lastTimestamp);
        }
        return "";
    }

    private static class Pid {
        String pid;
        String name;
        Queue<String> fifo;
        LineNumberReader reader;
    }

    private static class Row {
        JsonObject json;
        Pid parent;
        String pid;
        String name;
        long uid;
        long timestamp;
        JsonObject endpoint;
        JsonObject endpointService;
        JsonObject message;

        Row(Pid parent) {
            this.parent = parent;
        }

    }

    private static class StatusRow extends CopyableRow<StatusRow> {
        String pid;
        String name;
        String age;
        long uptime;
        boolean enabled;
        long counter;
        long firstTimestamp;
        long lastTimestamp;
        String uri;
    }

}
