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
package org.apache.camel.runtime.jfr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonRecordSupport;

/**
 * Reports whether the camel-jfr runtime instrumentation is installed, and allows its events to be turned on and off
 * while the application is running.
 *
 * @since 4.22
 */
@DevConsole(name = "jfr", displayName = "JFR Runtime Instrumentation",
            description = "Status and live control of camel-jfr runtime instrumentation", readOnly = false)
public class CamelJfrDevConsole extends AbstractDevConsole {

    @Metadata(label = "query", description = "Command to perform", javaType = "java.lang.String",
              defaultValue = "status", enums = "status,enable,disable,jfc,snapshot")
    public static final String COMMAND = "command";

    @Metadata(label = "query", description = "The runtime event to enable or disable, or all for every event",
              javaType = "java.lang.String", defaultValue = "all",
              enums = "all,route,processor,exchange,send,failed,redelivery")
    public static final String EVENT = "event";

    @Metadata(label = "query",
              description = "Comma separated list of events to disable in the generated jfc settings overlay",
              javaType = "java.lang.String")
    public static final String DISABLE = "disable";

    @Metadata(label = "query", description = "Filter snapshot results to the given route id",
              javaType = "java.lang.String")
    public static final String ROUTE_ID = "routeId";

    @Metadata(label = "query",
              description = "Maximum number of failure and redelivery entries to return in a snapshot",
              javaType = "int", defaultValue = "50")
    public static final String LIMIT = "limit";

    private static final String ALL = "all";
    private static final int DEFAULT_LIMIT = 50;

    public record RecordingEntry(
            @Metadata(description = "The recording name") String name,
            @Metadata(description = "The recording state") String state,
            @Metadata(description = "The recording destination file (only present when configured)") String destination) {
    }

    public record RouteStatEntry(
            @Metadata(description = "The number of spans") long total,
            @Metadata(description = "The number of failed spans") long failed,
            @Metadata(description = "The minimum duration in milliseconds") double minMs,
            @Metadata(description = "The mean duration in milliseconds") double meanMs,
            @Metadata(description = "The maximum duration in milliseconds") double maxMs,
            @Metadata(description = "The route id") String routeId) {
    }

    public record ProcessorStatEntry(
            @Metadata(description = "The number of spans") long total,
            @Metadata(description = "The number of failed spans") long failed,
            @Metadata(description = "The minimum duration in milliseconds") double minMs,
            @Metadata(description = "The mean duration in milliseconds") double meanMs,
            @Metadata(description = "The maximum duration in milliseconds") double maxMs,
            @Metadata(description = "The processor id") String processorId,
            @Metadata(description = "The processor type") String processorType,
            @Metadata(description = "The route id") String routeId) {
    }

    public record EndpointStatEntry(
            @Metadata(description = "The number of spans") long total,
            @Metadata(description = "The number of failed spans") long failed,
            @Metadata(description = "The minimum duration in milliseconds") double minMs,
            @Metadata(description = "The mean duration in milliseconds") double meanMs,
            @Metadata(description = "The maximum duration in milliseconds") double maxMs,
            @Metadata(description = "The endpoint URI") String endpointUri) {
    }

    public record FailureEntry(
            @Metadata(description = "The failure timestamp") String timestamp,
            @Metadata(description = "The exchange id") String exchangeId,
            @Metadata(description = "The route id") String routeId,
            @Metadata(description = "The exception type") String exceptionType,
            @Metadata(description = "The exception message") String exceptionMessage) {
    }

    public record RedeliveryEntry(
            @Metadata(description = "The redelivery timestamp") String timestamp,
            @Metadata(description = "The exchange id") String exchangeId,
            @Metadata(description = "The route id") String routeId,
            @Metadata(description = "The redelivery attempt number") int attempt,
            @Metadata(description = "The maximum number of redelivery attempts") int maxAttempts) {
    }

    public record Response(
            @Metadata(description = "Whether camel-jfr runtime instrumentation is registered (status command)") Boolean runtimeEvents,
            @Metadata(description = "The active JFR recordings (status command)") List<RecordingEntry> recordings,
            @Metadata(description = "Whether each runtime event is enabled, keyed by short name (status command)") Map<String, Boolean> events,
            @Metadata(description = "Whether the toggle succeeded (enable/disable command)") Boolean success,
            @Metadata(description = "The toggle result message (enable/disable command)") String result,
            @Metadata(description = "The generated jfc settings overlay (jfc command)") String jfc,
            @Metadata(description = "An error message (jfc, snapshot, or unknown command)") String error,
            @Metadata(description = "Whether this is a snapshot response (snapshot command)") Boolean snapshot,
            @Metadata(description = "Epoch time in milliseconds the snapshot was taken (snapshot command)") Long snapshotTimestamp,
            @Metadata(description = "The number of matched events in the snapshot (snapshot command)") Integer eventCount,
            @Metadata(description = "Per-route duration statistics (snapshot command)") List<RouteStatEntry> routes,
            @Metadata(description = "Per-processor duration statistics (snapshot command)") List<ProcessorStatEntry> processors,
            @Metadata(description = "Per-endpoint duration statistics (snapshot command)") List<EndpointStatEntry> endpoints,
            @Metadata(description = "The most recent failures, newest first (snapshot command)") List<FailureEntry> failures,
            @Metadata(description = "The most recent redeliveries, newest first (snapshot command)") List<RedeliveryEntry> redeliveries) {
    }

    public CamelJfrDevConsole() {
        super("camel", "jfr", "JFR Runtime Instrumentation",
              "Status and live control of camel-jfr runtime instrumentation");
    }

    private boolean isInstrumentationRegistered() {
        CamelContext ctx = getCamelContext();
        if (ctx == null) {
            return false;
        }
        for (LifecycleStrategy strategy : ctx.getLifecycleStrategies()) {
            if (strategy instanceof CamelJfrRuntimeInstrumentation instrumentation) {
                return instrumentation.isRegistered();
            }
        }
        return false;
    }

    /**
     * Only a running recording can have its events toggled, so a stopped or closed one is not reported as changed.
     */
    private static List<Recording> runningRecordings() {
        List<Recording> answer = new ArrayList<>();
        for (Recording recording : FlightRecorder.getFlightRecorder().getRecordings()) {
            if (recording.getState() == RecordingState.RUNNING) {
                answer.add(recording);
            }
        }
        return answer;
    }

    private String doStatus() {
        List<Recording> recordings = FlightRecorder.getFlightRecorder().getRecordings();

        StringBuilder sb = new StringBuilder();
        sb.append("runtimeEvents: ").append(isInstrumentationRegistered()).append('\n');
        if (recordings.isEmpty()) {
            sb.append("recordings: none active\n");
        } else {
            for (Recording recording : recordings) {
                sb.append("recording: ").append(recording.getName())
                        .append(" (state=").append(recording.getState())
                        .append(", destination=").append(recording.getDestination())
                        .append(", duration=").append(recording.getDuration())
                        .append(")\n");
            }
        }
        for (CamelJfrEvents event : CamelJfrEvents.values()) {
            sb.append("event ").append(event.getShortName()).append(": ")
                    .append(event.isEnabled() ? "enabled" : "disabled").append('\n');
        }
        return sb.toString();
    }

    /**
     * Resolves the {@code event} option to the events it addresses.
     *
     * @throws IllegalArgumentException if the option names an unknown event
     */
    private static Set<CamelJfrEvents> resolveEvents(String event) {
        if (event == null || ALL.equals(event)) {
            return EnumSet.allOf(CamelJfrEvents.class);
        }
        CamelJfrEvents answer = CamelJfrEvents.byShortName(event);
        if (answer == null) {
            throw new IllegalArgumentException(
                    "unknown event: " + event + ". Valid values: " + CamelJfrEvents.shortNames() + ", " + ALL);
        }
        return EnumSet.of(answer);
    }

    /**
     * The outcome of a toggle, so callers can tell "nothing was changed" from "the events were changed" without having
     * to parse the message.
     */
    private record ToggleResult(boolean success, String message) {
    }

    private ToggleResult doToggle(Map<String, Object> options, boolean enable) {
        String event = optionString(options, EVENT);
        Set<CamelJfrEvents> targets;
        try {
            targets = resolveEvents(event);
        } catch (IllegalArgumentException e) {
            return new ToggleResult(false, e.getMessage());
        }

        List<Recording> recordings = runningRecordings();
        if (recordings.isEmpty()) {
            return new ToggleResult(
                    false,
                    "no running recording: nothing to toggle live. Start one via --jfr, "
                           + "'jcmd <pid> JFR.start', or JMX first.");
        }

        for (Recording recording : recordings) {
            for (CamelJfrEvents target : targets) {
                if (enable) {
                    recording.enable(target.getEventClass());
                } else {
                    recording.disable(target.getEventClass());
                }
            }
        }
        return new ToggleResult(
                true,
                (enable ? "enabled " : "disabled ") + (event != null ? event : ALL)
                      + " on " + recordings.size() + " recording(s)");
    }

    /**
     * Generates a {@code .jfc} settings overlay, so the events can be turned on for a recording started outside of
     * Camel.
     *
     * @throws IllegalArgumentException if the disable option names an unknown event
     */
    private String doJfc(Map<String, Object> options) {
        String disable = optionString(options, DISABLE);
        Set<CamelJfrEvents> disabled = EnumSet.noneOf(CamelJfrEvents.class);
        if (disable != null) {
            for (String name : disable.split(",")) {
                disabled.addAll(resolveEvents(name.trim()));
            }
        }

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<configuration version=\"2.0\">\n");
        for (CamelJfrEvents event : CamelJfrEvents.values()) {
            xml.append("<event name=\"").append(event.getEventName()).append("\">\n")
                    .append("    <setting name=\"enabled\">").append(!disabled.contains(event)).append("</setting>\n")
                    .append("</event>\n");
        }
        xml.append("</configuration>\n");

        return xml + "\nSave the above to a file, e.g. camel-runtime-events.jfc, then run:\n"
               + "jcmd " + ProcessHandle.current().pid() + " JFR.start settings=default,camel-runtime-events.jfc"
               + "\n(replace 'default' with whatever base profile your recording already uses)";
    }

    // ---- snapshot ----

    private static class DurationStats {
        long count;
        long failed;
        long minNanos = Long.MAX_VALUE;
        long maxNanos;
        long totalNanos;

        void record(long nanos, boolean isFailed) {
            count++;
            if (isFailed) {
                failed++;
            }
            totalNanos += nanos;
            if (nanos < minNanos) {
                minNanos = nanos;
            }
            if (nanos > maxNanos) {
                maxNanos = nanos;
            }
        }

        double minMs() {
            return count == 0 ? 0 : minNanos / 1_000_000.0;
        }

        double meanMs() {
            return count == 0 ? 0 : (totalNanos / (double) count) / 1_000_000.0;
        }

        double maxMs() {
            return maxNanos / 1_000_000.0;
        }
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private Response doSnapshot(Map<String, Object> options) {
        String routeIdFilter = optionString(options, ROUTE_ID);
        int limit = DEFAULT_LIMIT;
        String limitStr = optionString(options, LIMIT);
        if (limitStr != null) {
            try {
                limit = Integer.parseInt(limitStr);
            } catch (NumberFormatException e) {
                // keep default
            }
        }

        boolean snapshotFlag = true;
        long snapshotTimestamp = System.currentTimeMillis();
        String error = null;
        Integer eventCount = null;
        List<RouteStatEntry> routes = null;
        List<ProcessorStatEntry> processors = null;
        List<EndpointStatEntry> endpoints = null;
        List<FailureEntry> failures = null;
        List<RedeliveryEntry> redeliveries = null;

        Path tempFile = null;
        try (Recording snapshot = FlightRecorder.getFlightRecorder().takeSnapshot()) {
            if (snapshot.getSize() == 0) {
                return new Response(
                        null, null, null, null, null, null, "no JFR data available: ensure a recording is active",
                        snapshotFlag, snapshotTimestamp, null, null, null, null, null, null);
            }
            tempFile = Files.createTempFile("camel-jfr-snapshot-", ".jfr");
            snapshot.dump(tempFile);

            Map<String, DurationStats> routeStats = new LinkedHashMap<>();
            Map<String, DurationStats> processorStats = new LinkedHashMap<>();
            Map<String, String> processorTypes = new LinkedHashMap<>();
            Map<String, String> processorRoutes = new LinkedHashMap<>();
            Map<String, DurationStats> endpointStats = new LinkedHashMap<>();
            List<FailureEntry> failuresList = new ArrayList<>();
            List<RedeliveryEntry> redeliveriesList = new ArrayList<>();
            int eventCountVal = 0;

            String routeEventName = CamelJfrEvents.ROUTE.getEventName();
            String processorEventName = CamelJfrEvents.PROCESSOR.getEventName();
            String sendEventName = CamelJfrEvents.SEND.getEventName();
            String failedEventName = CamelJfrEvents.FAILED.getEventName();
            String redeliveryEventName = CamelJfrEvents.REDELIVERY.getEventName();

            try (RecordingFile rf = new RecordingFile(tempFile)) {
                while (rf.hasMoreEvents()) {
                    RecordedEvent event = rf.readEvent();
                    String eventName = event.getEventType().getName();

                    if (routeEventName.equals(eventName)) {
                        String routeId = event.getString("routeId");
                        if (routeIdFilter == null || routeIdFilter.equals(routeId)) {
                            long nanos = durationNanos(event);
                            boolean failed = event.getBoolean("failed");
                            routeStats.computeIfAbsent(routeId, k -> new DurationStats()).record(nanos, failed);
                            eventCountVal++;
                        }
                    } else if (processorEventName.equals(eventName)) {
                        String routeId = event.getString("routeId");
                        if (routeIdFilter == null || routeIdFilter.equals(routeId)) {
                            String processorId = event.getString("processorId");
                            long nanos = durationNanos(event);
                            boolean failed = event.getBoolean("failed");
                            processorStats.computeIfAbsent(processorId, k -> new DurationStats()).record(nanos, failed);
                            processorTypes.putIfAbsent(processorId, event.getString("processorType"));
                            processorRoutes.putIfAbsent(processorId, routeId);
                            eventCountVal++;
                        }
                    } else if (sendEventName.equals(eventName)) {
                        String endpointUri = event.getString("endpointUri");
                        long nanos = durationNanos(event);
                        boolean failed = event.getBoolean("failed");
                        endpointStats.computeIfAbsent(endpointUri, k -> new DurationStats()).record(nanos, failed);
                        eventCountVal++;
                    } else if (failedEventName.equals(eventName)) {
                        String routeId = event.getString("routeId");
                        if (routeIdFilter == null || routeIdFilter.equals(routeId)) {
                            failuresList.add(new FailureEntry(
                                    event.getStartTime().toString(), event.getString("exchangeId"), routeId,
                                    event.getString("exceptionType"), event.getString("exceptionMessage")));
                            eventCountVal++;
                        }
                    } else if (redeliveryEventName.equals(eventName)) {
                        String routeId = event.getString("routeId");
                        if (routeIdFilter == null || routeIdFilter.equals(routeId)) {
                            redeliveriesList.add(new RedeliveryEntry(
                                    event.getStartTime().toString(), event.getString("exchangeId"), routeId,
                                    event.getInt("attempt"), event.getInt("maxAttempts")));
                            eventCountVal++;
                        }
                    }
                }
            }

            eventCount = eventCountVal;

            // routes — sorted by total descending
            List<RouteStatEntry> routesList = new ArrayList<>();
            routeStats.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<String, DurationStats>> comparingLong(e -> e.getValue().count).reversed())
                    .forEach(e -> {
                        DurationStats st = e.getValue();
                        routesList.add(new RouteStatEntry(
                                st.count, st.failed, round3(st.minMs()), round3(st.meanMs()), round3(st.maxMs()),
                                e.getKey()));
                    });
            routes = routesList;

            // processors — sorted by mean duration descending (slowest first)
            List<ProcessorStatEntry> processorsList = new ArrayList<>();
            processorStats.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<String, DurationStats>> comparingDouble(
                            e -> e.getValue().meanMs()).reversed())
                    .forEach(e -> {
                        DurationStats st = e.getValue();
                        processorsList.add(new ProcessorStatEntry(
                                st.count, st.failed, round3(st.minMs()), round3(st.meanMs()), round3(st.maxMs()),
                                e.getKey(), processorTypes.get(e.getKey()), processorRoutes.get(e.getKey())));
                    });
            processors = processorsList;

            // endpoints — sorted by total descending
            List<EndpointStatEntry> endpointsList = new ArrayList<>();
            endpointStats.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<String, DurationStats>> comparingLong(
                            e -> e.getValue().count).reversed())
                    .forEach(e -> {
                        DurationStats st = e.getValue();
                        endpointsList.add(new EndpointStatEntry(
                                st.count, st.failed, round3(st.minMs()), round3(st.meanMs()), round3(st.maxMs()),
                                e.getKey()));
                    });
            endpoints = endpointsList;

            // failures — newest first, capped at limit
            failuresList.sort(Comparator.comparing(FailureEntry::timestamp).reversed());
            failures = failuresList.stream().limit(limit).toList();

            // redeliveries — newest first, capped at limit
            redeliveriesList.sort(Comparator.comparing(RedeliveryEntry::timestamp).reversed());
            redeliveries = redeliveriesList.stream().limit(limit).toList();

        } catch (IOException e) {
            error = "failed to read JFR snapshot: " + e.getMessage();
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }

        return new Response(
                null, null, null, null, null, null, error, snapshotFlag, snapshotTimestamp, eventCount, routes,
                processors, endpoints, failures, redeliveries);
    }

    private static long durationNanos(RecordedEvent event) {
        Duration d = event.getDuration();
        return d != null ? d.toNanos() : 0;
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        String command = optionString(options, COMMAND);
        return switch (command != null ? command : "status") {
            case "status" -> doStatus();
            case "enable" -> doToggle(options, true).message();
            case "disable" -> doToggle(options, false).message();
            case "jfc" -> {
                try {
                    yield doJfc(options);
                } catch (IllegalArgumentException e) {
                    yield e.getMessage();
                }
            }
            case "snapshot" -> JsonRecordSupport.toJsonObject(doSnapshot(options)).toJson();
            default -> unknownCommand(command);
        };
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        String command = optionString(options, COMMAND);
        return switch (command != null ? command : "status") {
            case "status" -> {
                boolean runtimeEvents = isInstrumentationRegistered();
                List<RecordingEntry> recordings = new ArrayList<>();
                for (Recording recording : FlightRecorder.getFlightRecorder().getRecordings()) {
                    String destination = recording.getDestination() != null ? recording.getDestination().toString() : null;
                    recordings.add(new RecordingEntry(
                            recording.getName(),
                            StringHelper.capitalize(recording.getState().toString().toLowerCase(java.util.Locale.US)),
                            destination));
                }
                Map<String, Boolean> events = new LinkedHashMap<>();
                for (CamelJfrEvents event : CamelJfrEvents.values()) {
                    events.put(event.getShortName(), event.isEnabled());
                }
                yield JsonRecordSupport.toJsonObject(new Response(
                        runtimeEvents, recordings, events, null, null, null, null, null, null, null, null, null,
                        null, null, null));
            }
            case "enable", "disable" -> {
                ToggleResult result = doToggle(options, "enable".equals(command));
                yield JsonRecordSupport.toJsonObject(new Response(
                        null, null, null, result.success(), result.message(), null, null, null, null, null, null,
                        null, null, null, null));
            }
            case "jfc" -> {
                String jfc = null;
                String error = null;
                try {
                    jfc = doJfc(options);
                } catch (IllegalArgumentException e) {
                    error = e.getMessage();
                }
                yield JsonRecordSupport.toJsonObject(new Response(
                        null, null, null, null, null, jfc, error, null, null, null, null, null, null, null, null));
            }
            case "snapshot" -> JsonRecordSupport.toJsonObject(doSnapshot(options));
            default -> JsonRecordSupport.toJsonObject(new Response(
                    null, null, null, null, null, null, unknownCommand(command), null, null, null, null, null, null,
                    null, null));
        };
    }

    private static String unknownCommand(String command) {
        return "unknown command: " + command + ". Valid values: status, enable, disable, jfc, snapshot";
    }
}
