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
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Mixin;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.cli.connector.RepeatedSend;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.MavenResolverMixin;
import org.apache.camel.dsl.jbang.core.commands.Run;
import org.apache.camel.dsl.jbang.core.common.ActionClient;
import org.apache.camel.dsl.jbang.core.common.ActionClient.ActionResult;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.dsl.jbang.core.common.TimeoutConverter;
import org.apache.camel.dsl.jbang.run.ConfiguredRun;
import org.apache.camel.main.KameletMain;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.jline.jansi.Ansi;

@CommandDefinition(name = "send", description = "Send messages to endpoints", generateHelp = true,
                   footer = {
                           "With --repeat the message is sent that many times, with --interval between two",
                           "(a range such as 100..500 draws each pause at random), and a summary is printed",
                           "instead of each reply. A body of file:<folder> sends the",
                           "files of the folder in name order, one message per file; 001.properties has the",
                           "headers of 001.json. With --repeat it goes round the files again.",
                           "",
                           "Examples:",
                           "  camel cmd send --endpoint=kafka:myTopic --body='Hello World'",
                           "  camel cmd send hello --endpoint=direct:foo --body='Hello'",
                           "  camel cmd send --endpoint=kafka:refunds --body=file:refund.json --repeat=50 --interval=200ms",
                           "  camel cmd send --endpoint=kafka:refunds --body=file:refund.json --repeat=50 --interval=100..500",
                           "  camel cmd send --endpoint=kafka:refunds --body=file:test/chaos/refunds-fails/" })
public class CamelSendAction extends ActionBaseCommand {

    @Argument(description = "The running Camel integration to send to (name or pid). Without it, the integration that"
                            + " runs, unless --endpoint is a system outside the integrations, such as kafka:topic,"
                            + " which the CLI sends to itself")
    String name;

    @Option(name = "properties",
            description = "comma separated list of properties file (only applicable when NOT using an existing running Camel)"
                          +
                          " (ex. /path/to/file.properties,/path/to/other.properties")
    String propertiesFiles;

    @OptionList(name = "prop", aliases = { "property" }, valueSeparator = OptionList.NO_SEPARATOR,
                description = "Additional properties; override existing (only applicable when NOT using an existing running Camel)")
    String[] property;

    @Option(name = "endpoint", aliases = { "uri" },
            description = "Endpoint where to send the message (can be uri, pattern, or refer to a route id)")
    String endpoint;

    @Option(name = "poll", hasValue = false,
            description = "Poll instead of sending a message. This can be used to receive latest message from a Kafka topic or JMS queue.")
    boolean poll;

    @Option(name = "reply", hasValue = false,
            description = "Whether to expect a reply message (InOut vs InOut messaging style)")
    boolean reply;

    @Option(name = "reply-file", description = "Saves reply message to the file with the given name (override if exists)")
    String replyFile;

    @Option(name = "body", description = "Message body to send (prefix with file: to refer to loading message body from file)")
    String body;

    @OptionList(name = "header", valueSeparator = OptionList.NO_SEPARATOR, description = "Message header (key=value)")
    List<String> headers;

    @Option(name = "repeat", description = "Send the message this many times (going round the files of a folder body), and"
                                           + " print a summary instead of each reply (at most 10000)")
    int repeat;

    @Option(name = "interval", description = "The pause between two messages with --repeat or a folder body, in millis or as a"
                                             + " duration such as 200ms or 1s, or a range such as 100..500 or 100ms..2s to draw"
                                             + " each pause at random (at most 60s)")
    String interval;

    // the shortest and the longest pause of --interval, in millis
    private long intervalMin;
    private long intervalMax;

    @Option(name = "timeout", description = "Timeout waiting for message to be sent (and reply message if InOut messaging),"
                                            + " in millis or as a duration such as 30s",
            defaultValue = "20000", converter = TimeoutConverter.class)
    long timeout = 20000;

    @Option(name = "show-exchange-properties", hasValue = false,
            description = "Show exchange properties from response message (InOut)", defaultValue = "false")
    boolean showExchangeProperties;

    @Option(name = "show-exchange-variables", hasValue = false,
            description = "Show exchange variables from response message (InOut)", defaultValue = "false")
    boolean showExchangeVariables;

    @Option(name = "show-headers", hasValue = false, description = "Show message headers from response message (InOut)",
            defaultValue = "true")
    boolean showHeaders = true;

    @Option(name = "show-body", hasValue = false, description = "Show message body from response message (InOut)",
            defaultValue = "true")
    boolean showBody = true;

    @Option(name = "show-exception", hasValue = false, description = "Show exception and stacktrace for failed messages",
            defaultValue = "true")
    boolean showException = true;

    @Option(name = "pretty", hasValue = false,
            description = "Pretty print response message body (InOut) when using JSon or XML format")
    boolean pretty;

    @Option(name = "logging-color", hasValue = false, description = "Use colored logging", defaultValue = "true")
    boolean loggingColor = true;

    @Option(name = "infra", description = "Send to infrastructure service (e.g., nats, kafka)")
    String infra;

    @Mixin
    MavenResolverMixin mavenResolver;

    private volatile long pid;

    private MessageTableHelper tableHelper;

    // the limit of the integration, so the CLI refuses what the integration would
    private static final int MAX_MESSAGES = RepeatedSend.MAX_MESSAGES;

    private final CamelCatalog catalog = new DefaultCamelCatalog();

    public CamelSendAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        if (headers != null) {
            for (String h : headers) {
                if (!h.contains("=")) {
                    throw CliException.usage("Header must be in key=value format, was: " + h);
                }
            }
        }
        // timeout cannot be too low
        if (timeout < 5000) {
            timeout = 5000;
        }
        if (repeat < 0 || repeat > MAX_MESSAGES) {
            throw CliException.usage("--repeat must be between 1 and " + MAX_MESSAGES + ", was: " + repeat);
        }
        try {
            long[] pause = RepeatedSend.interval(interval);
            intervalMin = pause[0];
            intervalMax = pause[1];
        } catch (IllegalArgumentException e) {
            throw CliException.usage(e.getMessage().replace("The interval", "--interval"));
        }
        if (isRepeated() && poll) {
            throw CliException.usage("A poll does not repeat: use --repeat or a folder body to send, or --poll alone");
        }
        if (isRepeated() && replyFile != null) {
            throw CliException.usage("--reply-file saves one reply: it cannot be used with --repeat or a folder body");
        }

        if (infra != null) {
            return doCallInfra(infra);
        } else if (name != null) {
            return doCall(name);
        } else if (endpoint == null || isInsideAnIntegration(endpoint)) {
            // the Camel of this command has no routes: camel cmd send --body=... is for the integration that runs
            return doCall(String.valueOf(theRunningIntegration().pid()));
        } else {
            // a system outside the integrations, such as kafka:topic, from the Camel of this command
            return doCallLocal();
        }
    }

    /**
     * The schemes of the endpoints that only an integration has: a send from the Camel of this command reaches nobody.
     */
    private static final Set<String> INTERNAL_SCHEMES
            = Set.of("direct", "seda", "stub", "disruptor", "disruptor-vm", "mock", "dataset", "vm");

    /** Whether the endpoint is a route id, a pattern or an endpoint in the memory of an integration. */
    static boolean isInsideAnIntegration(String endpoint) {
        int colon = endpoint.indexOf(':');
        return colon < 0 || endpoint.endsWith("*")
                || INTERNAL_SCHEMES.contains(endpoint.substring(0, colon).toLowerCase(Locale.ROOT));
    }

    /**
     * The one integration that runs, for a send without a name.
     *
     * @throws CliException (exit code 3) when none runs, or several
     */
    private CamelProcess theRunningIntegration() {
        List<CamelProcess> running = ProcessFinder.find(null);
        if (running.size() == 1) {
            return running.get(0);
        }
        if (running.isEmpty()) {
            throw CliException.notFound(endpoint == null
                    ? "No running Camel integration to send to: start one with camel run, or give --endpoint to send"
                      + " to a system such as kafka:topic"
                    : "No running Camel integration to send to: " + endpoint + " is in an integration, so start it"
                      + " with camel run first");
        }
        String names = running.stream()
                .map(p -> p.name() != null && !p.name().isEmpty() ? p.name() + " (pid " + p.pid() + ")" : "pid " + p.pid())
                .collect(Collectors.joining(", "));
        throw CliException.notFound(running.size() + " Camel integrations are running: " + names
                                    + ". Give the name or pid of the one to send to.");
    }

    /**
     * The options of the send action.
     */
    protected void sendOptions(JsonObject root) {
        root.put("endpoint", endpoint);
        root.put("poll", poll);
        root.put("pollTimeout", timeout);
        String mep = (reply || replyFile != null) ? "InOut" : "InOnly";
        root.put("exchangePattern", mep);
        if (body != null) {
            // favour using absolute path to file (or folder of messages) as the send command can be called from another
            // folder than where camel is running
            if (body.startsWith("file:")) {
                File f = new File(body.substring(5));
                if (f.exists()) {
                    body = "file:" + f.getAbsolutePath();
                }
            }
            root.put("body", body);
        }
        if (repeat > 0) {
            root.put("repeat", repeat);
        }
        if (intervalMax > 0) {
            root.put("interval", intervalMin);
            if (intervalMax > intervalMin) {
                root.put("intervalMax", intervalMax);
            }
        }
        if (headers != null) {
            JsonArray arr = new JsonArray();
            for (String h : headers) {
                JsonObject jo = new JsonObject();
                jo.put("key", StringHelper.before(h, "="));
                jo.put("value", StringHelper.after(h, "="));
                arr.add(jo);
            }
            root.put("headers", arr);
        }
    }

    public Integer doCall(String name) throws Exception {
        CamelProcess process = findOne(name);
        this.pid = process.pid();

        // wait longer than the timeout of sending
        return showStatus(ActionClient.execute(process, "send", this::sendOptions, waitMillis()));
    }

    /** Whether more than one message is sent: a repeat, or the files of a folder. */
    private boolean isRepeated() {
        return repeat > 1 || folder() != null;
    }

    /** The folder of messages the body names (file:<folder>), when it is here. */
    private File folder() {
        if (body == null || !body.startsWith("file:")) {
            return null;
        }
        File f = new File(body.substring(5));
        return f.isDirectory() ? f : null;
    }

    /**
     * How long to wait for the answer: longer than the timeout of sending, and for more messages the pauses between
     * them and a second for each.
     */
    long waitMillis() {
        long wait = timeout + 10000;
        if (isRepeated()) {
            long count = repeat;
            File folder = folder();
            if (count <= 1 && folder != null) {
                String[] files = folder.list((d, n) -> !n.startsWith(".") && !n.endsWith(".properties"));
                count = files != null ? files.length : 1;
            }
            wait += count * (intervalMax + 1000);
        }
        return wait;
    }

    /**
     * Prints the outcome of sending the message.
     *
     * @return the exit code: 1 when the exchange failed, 124 when the endpoint or the integration did not answer in
     *         time
     */
    protected int showStatus(ActionResult result) throws Exception {
        JsonObject jo = result.reply();
        if (jo == null) {
            // no answer from the integration at all
            // an integration that stopped before it answered is not a timeout
            String error = result.status() == ActionClient.Status.TIMEOUT ? "Send timeout: " + result.error() : result.error();
            throw new CliException(result.exitCode(), error);
        }
        printStatusLine(jo);
        if (jo.containsKey("sent")) {
            return showSummary(jo);
        }
        String exchangeId = jo.getString("exchangeId");
        JsonObject message = jo.getMap("message");
        JsonObject cause = jo.getMap("exception");
        if (!result.isOk() && message == null && cause == null) {
            // the integration failed the action itself, so there is no exchange to show
            printer().printErr(result.error());
        }
        if (message != null || cause != null) {
            if (replyFile != null) {
                Path target = Path.of(replyFile);
                String json = jo.toJson();
                if (pretty) {
                    json = Jsoner.prettyPrint(json, 2);
                }
                try {
                    Files.writeString(target, json);
                } catch (IOException e) {
                    throw CliException.failure("Cannot write the reply to " + replyFile + ": " + e.getMessage());
                }
            }
            if (!showExchangeProperties && message != null) {
                message.remove("exchangeProperties");
            }
            if (!showExchangeVariables && message != null) {
                message.remove("exchangeVariables");
            }
            if (!showHeaders && message != null) {
                message.remove("headers");
            }
            if (!showBody && message != null) {
                message.remove("body");
            }
            if (!showException && cause != null) {
                cause = null;
            }
            if (replyFile == null) {
                tableHelper = new MessageTableHelper();
                tableHelper.setPretty(pretty);
                tableHelper.setLoggingColor(loggingColor);
                tableHelper.setShowExchangeProperties(showExchangeProperties);
                tableHelper.setShowExchangeVariables(showExchangeVariables);
                String mep = (reply || replyFile != null) ? "InOut" : "InOnly";
                String table = tableHelper.getDataAsTable(exchangeId, mep, null, jo, null, message, cause);
                printer().println(table);
            }
        }
        String status = jo.getString("status");
        if ("timeout".equals(status)) {
            return CliExitCode.TIMEOUT;
        } else if ("failed".equals(status) || "error".equals(status)) {
            return CliExitCode.FAILURE;
        }
        return CliExitCode.OK;
    }

    /** The summary of a repeat or a folder of messages: the errors, as the status line has the counts. */
    private int showSummary(JsonObject jo) {
        Collection<JsonObject> errors = jo.getCollection("errors");
        if (errors != null) {
            for (JsonObject error : errors) {
                printer().println("  " + error.getLong("count") + " x " + error.getString("message"));
            }
        }
        return "success".equals(jo.getString("status")) ? CliExitCode.OK : CliExitCode.FAILURE;
    }

    private Integer doCallLocal() throws Exception {
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

        // spawn thread that sends the message to the Camel of this process, once it has started
        final CountDownLatch latch = new CountDownLatch(1);
        this.pid = ProcessHandle.current().pid();
        JsonObject request = new JsonObject();
        request.put("action", "send");
        sendOptions(request);
        AtomicInteger status = new AtomicInteger(CliExitCode.TIMEOUT);
        AtomicReference<CliException> failure = new AtomicReference<>();
        Thread t = new Thread("CamelJBangSendStatus") {
            @Override
            public void run() {
                try {
                    status.set(showStatus(ActionClient.execute(pid, request, waitMillis())));
                } catch (CliException e) {
                    failure.set(e);
                } catch (Exception e) {
                    failure.set(CliException.failure("Cannot send the message: " + e.getMessage()));
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

        Integer exit = run.call();
        if (!latch.await(waitMillis(), TimeUnit.MILLISECONDS)) {
            throw CliException.timeout("Send timeout: no answer within " + waitMillis() + " millis");
        }
        if (exit != null && exit != 0) {
            return exit;
        }
        if (failure.get() != null) {
            throw failure.get();
        }
        return status.get();
    }

    private void printStatusLine(JsonObject jo) {
        // timestamp
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        String ts = sdf.format(new Date(jo.getLongOrDefault("timestamp", System.currentTimeMillis())));
        if (loggingColor) {
            printColored(Ansi.ansi().fgBrightDefault().a(Ansi.Attribute.INTENSITY_FAINT).a(ts).reset());
        } else {
            printer().print(ts);
        }
        // pid
        printer().print("  ");
        String p = String.format("%5.5s", this.pid);
        if (loggingColor) {
            printColored(Ansi.ansi().fgMagenta().a(p).reset());
            printColored(Ansi.ansi().fgBrightDefault().a(Ansi.Attribute.INTENSITY_FAINT).a(" --- ").reset());
        } else {
            printer().print(p);
            printer().print(" --- ");
        }
        // endpoint
        String ids = jo.getString("endpoint");
        if (ids != null) {
            if (ids.length() > 40) {
                ids = ids.substring(0, 40);
            }
            ids = String.format("%40.40s", ids);
            if (loggingColor) {
                printColored(Ansi.ansi().fgCyan().a(ids).reset());
            } else {
                printer().print(ids);
            }
        }
        printer().print(" : ");
        // status
        printer().print(getStatus(jo));
        // elapsed
        // an action the integration failed has no elapsed time
        String e = TimeUtils.printDuration(jo.getLongOrDefault("elapsed", 0), true);
        if (loggingColor) {
            printColored(Ansi.ansi().fgBrightDefault().a(" (" + e + ")").reset());
        } else {
            printer().print(" (" + e + ")");
        }
        printer().println();
    }

    private String getStatus(JsonObject r) {
        boolean failed = "failed".equals(r.getString("status")) || "error".equals(r.getString("status"));
        boolean timeout = "timeout".equals(r.getString("status"));
        boolean reply = r.containsKey("message");
        String status;
        Ansi.Color c = Ansi.Color.GREEN;
        if (r.containsKey("sent")) {
            long sent = r.getLongOrDefault("sent", 0);
            long count = r.getLongOrDefault("failed", 0);
            status = "Sent " + sent + (sent == 1 ? " message" : " messages")
                     + (count > 0 ? " (" + (sent - count) + " ok, " + count + " failed)" : " (success)");
            if (count > 0) {
                c = Ansi.Color.RED;
            }
        } else if (failed) {
            status = "Failed (exception)";
            c = Ansi.Color.RED;
        } else if (replyFile != null) {
            if (poll) {
                status = "Poll save to file (success)";
            } else {
                status = "Reply save to file (success)";
            }
        } else if (reply) {
            if (poll) {
                status = "Poll received (success)";
            } else {
                status = "Reply received (success)";
            }
        } else if (timeout) {
            status = "Timeout";
            c = Ansi.Color.YELLOW;
        } else if (poll) {
            status = "Poll (success)";
        } else {
            status = "Sent (success)";
        }
        if (loggingColor) {
            return Ansi.ansi().fg(c).a(status).reset().toString();
        } else {
            return status;
        }
    }

    private Integer doCallInfra(String infraService) throws Exception {
        Map<Long, Path> infraPids = findInfraPids(infraService);

        if (infraPids.isEmpty()) {
            throw CliException.notFound("No running infrastructure service found for: " + infraService);
        }

        if (infraPids.size() > 1) {
            throw CliException.notFound("Multiple running infrastructure services found for: " + infraService
                                        + ". Found " + infraPids.size() + " services.");
        }

        Map.Entry<Long, Path> entry = infraPids.entrySet().iterator().next();
        Path jsonFile = entry.getValue();

        JsonObject connectionDetails = readConnectionDetails(jsonFile);

        if (connectionDetails == null) {
            throw CliException.failure("Could not read connection details from: " + jsonFile);
        }

        String scheme = extractScheme(endpoint, infraService);

        this.endpoint = updateEndpointWithConnectionDetails(endpoint, infraService, connectionDetails);

        Map<String, String> componentProps = buildComponentProperties(scheme, connectionDetails);

        Map<String, String> beanProps = buildBeanProperties(connectionDetails);

        int additionalCount = componentProps.size() + beanProps.size();
        if (additionalCount > 0) {
            int existingCount = (property != null) ? property.length : 0;
            String[] merged = new String[existingCount + additionalCount];
            if (property != null) {
                System.arraycopy(property, 0, merged, 0, existingCount);
            }
            int i = existingCount;
            for (Map.Entry<String, String> compEntry : componentProps.entrySet()) {
                merged[i++] = "camel.component." + scheme + "." + compEntry.getKey() + "=" + compEntry.getValue();
            }
            for (Map.Entry<String, String> beanEntry : beanProps.entrySet()) {
                merged[i++] = beanEntry.getKey() + "=" + beanEntry.getValue();
            }
            property = merged;
        }

        return doCallLocal();
    }

    private String extractScheme(String endpoint, String infraService) {
        if (endpoint != null && endpoint.contains(":")) {
            return StringHelper.before(endpoint, ":");
        }
        return infraService;
    }

    private Map<Long, Path> findInfraPids(String serviceName) throws Exception {
        Map<Long, Path> pids = new HashMap<>();

        Path camelDir = CommandLineHelper.getCamelDir();

        try (Stream<Path> files = Files.list(camelDir)) {
            List<Path> pidFiles = files
                    .filter(p -> {
                        String fileName = p.getFileName().toString();
                        return fileName.startsWith("infra-") && fileName.endsWith(".json");
                    })
                    .collect(Collectors.toList());

            for (Path pidFile : pidFiles) {
                String fileName = pidFile.getFileName().toString();
                // Format: infra-{service}-{pid}.json
                String withoutPrefix = fileName.substring("infra-".length());
                String withoutExtension = withoutPrefix.substring(0, withoutPrefix.lastIndexOf(".json"));

                int lastDashIndex = withoutExtension.lastIndexOf('-');
                if (lastDashIndex > 0) {
                    String service = withoutExtension.substring(0, lastDashIndex);
                    String pidStr = withoutExtension.substring(lastDashIndex + 1);

                    if (service.equals(serviceName)) {
                        try {
                            long pid = Long.parseLong(pidStr);
                            pids.put(pid, pidFile);
                        } catch (NumberFormatException e) {
                            // Skip invalid PID
                        }
                    }
                }
            }
        }

        return pids;
    }

    private JsonObject readConnectionDetails(Path jsonFile) throws Exception {
        String content = Files.readString(jsonFile);
        return (JsonObject) Jsoner.deserialize(content);
    }

    String updateEndpointWithConnectionDetails(
            String originalEndpoint, String infraService, JsonObject connectionDetails) {

        if (originalEndpoint == null) {
            originalEndpoint = infraService + ":default";
        }

        if (originalEndpoint.contains("?")) {
            return originalEndpoint;
        }

        String scheme;
        String path;
        if (originalEndpoint.contains(":")) {
            scheme = StringHelper.before(originalEndpoint, ":");
            path = StringHelper.after(originalEndpoint, ":");
        } else {
            scheme = originalEndpoint;
            path = null;
        }

        String pathBasedEndpoint = buildPathBasedEndpoint(path, connectionDetails);
        if (pathBasedEndpoint != null) {
            return pathBasedEndpoint;
        }

        Map<String, String> properties = buildPropertiesMap(scheme, connectionDetails);

        try {
            String uri = catalog.asEndpointUri(scheme, properties, true);
            if (uri != null && !uri.isEmpty()) {
                String queryPart = "";
                if (uri.contains("?")) {
                    queryPart = "?" + StringHelper.after(uri, "?");
                }

                if (path != null && !path.isEmpty()) {
                    return scheme + ":" + path + queryPart;
                } else {
                    return uri;
                }
            }
        } catch (URISyntaxException e) {
            // Fall back to manual construction if catalog fails
        }

        return buildEndpointManually(originalEndpoint, properties);
    }

    private Map<String, String> buildBeanProperties(JsonObject connectionDetails) {
        Map<String, String> properties = new LinkedHashMap<>();

        Object beanProps = connectionDetails.get("beanProperties");
        if (beanProps instanceof Map<?, ?> beanMap) {
            for (Map.Entry<?, ?> entry : beanMap.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    properties.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
        }

        return properties;
    }

    private Map<String, String> buildComponentProperties(String scheme, JsonObject connectionDetails) {
        Map<String, String> properties = new LinkedHashMap<>();

        var componentModel = catalog.componentModel(scheme);
        if (componentModel != null) {
            componentModel.getComponentOptions().stream()
                    .map(opt -> opt.getName())
                    .filter(name -> isValidConnectionProperty(name, connectionDetails))
                    .forEach(name -> properties.put(name, String.valueOf(connectionDetails.get(name))));
        }

        return properties;
    }

    private Map<String, String> buildPropertiesMap(String scheme, JsonObject connectionDetails) {
        Map<String, String> properties = new LinkedHashMap<>();

        var componentModel = catalog.componentModel(scheme);
        if (componentModel != null) {
            componentModel.getEndpointOptions().stream()
                    .map(opt -> opt.getName())
                    .filter(name -> isValidConnectionProperty(name, connectionDetails))
                    .forEach(name -> properties.put(name, String.valueOf(connectionDetails.get(name))));
        }

        return properties;
    }

    private boolean isValidConnectionProperty(String name, JsonObject connectionDetails) {
        if (!connectionDetails.containsKey(name)) {
            return false;
        }
        Object value = connectionDetails.get(name);
        return value != null && !(value instanceof Map);
    }

    private String buildPathBasedEndpoint(String path, JsonObject connectionDetails) {
        String uri = connectionDetails.getString("endpointUri");
        if (uri == null) {
            return null;
        }

        if (path != null && !path.isEmpty()) {
            String connectionBase = connectionDetails.getString("connectionBase");
            if (connectionBase != null) {
                String queryPart = StringHelper.after(uri, "?");
                if (queryPart != null) {
                    queryPart = "?" + queryPart;
                } else {
                    queryPart = "";
                }

                String newPath = path.startsWith("/") ? path : "/" + path;
                return connectionBase + newPath + queryPart;
            }
        }

        // fallback to original URI
        return uri;
    }

    private String buildEndpointManually(String endpoint, Map<String, String> properties) {
        if (properties.isEmpty()) {
            return endpoint;
        }

        StringBuilder queryParams = new StringBuilder();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (queryParams.length() > 0) {
                queryParams.append("&");
            }
            queryParams.append(entry.getKey()).append("=").append(entry.getValue());
        }

        return endpoint + "?" + queryParams;
    }
}
