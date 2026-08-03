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
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * Reports whether the camel-jfr runtime instrumentation is installed, and allows its events to be turned on and off
 * while the application is running.
 *
 * @since 4.22
 */
@DevConsole(name = "jfr", displayName = "JFR Runtime Instrumentation",
            description = "Status and live control of camel-jfr runtime instrumentation")
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
        sb.append("registered: ").append(isInstrumentationRegistered()).append('\n');
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

        JsonObject toJson() {
            JsonObject jo = new JsonObject();
            jo.put("total", count);
            jo.put("failed", failed);
            jo.put("minMs", round3(minMs()));
            jo.put("meanMs", round3(meanMs()));
            jo.put("maxMs", round3(maxMs()));
            return jo;
        }
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private JsonObject doSnapshot(Map<String, Object> options) {
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

        JsonObject result = new JsonObject();
        result.put("snapshot", true);

        Path tempFile = null;
        try (Recording snapshot = FlightRecorder.getFlightRecorder().takeSnapshot()) {
            if (snapshot.getSize() == 0) {
                result.put("error", "no JFR data available: ensure a recording is active");
                return result;
            }
            tempFile = Files.createTempFile("camel-jfr-snapshot-", ".jfr");
            snapshot.dump(tempFile);

            Map<String, DurationStats> routeStats = new LinkedHashMap<>();
            Map<String, DurationStats> processorStats = new LinkedHashMap<>();
            Map<String, String> processorTypes = new LinkedHashMap<>();
            Map<String, String> processorRoutes = new LinkedHashMap<>();
            Map<String, DurationStats> endpointStats = new LinkedHashMap<>();
            List<JsonObject> failures = new ArrayList<>();
            List<JsonObject> redeliveries = new ArrayList<>();
            int eventCount = 0;

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
                            eventCount++;
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
                            eventCount++;
                        }
                    } else if (sendEventName.equals(eventName)) {
                        String endpointUri = event.getString("endpointUri");
                        long nanos = durationNanos(event);
                        boolean failed = event.getBoolean("failed");
                        endpointStats.computeIfAbsent(endpointUri, k -> new DurationStats()).record(nanos, failed);
                        eventCount++;
                    } else if (failedEventName.equals(eventName)) {
                        String routeId = event.getString("routeId");
                        if (routeIdFilter == null || routeIdFilter.equals(routeId)) {
                            JsonObject fo = new JsonObject();
                            fo.put("timestamp", event.getStartTime().toString());
                            fo.put("exchangeId", event.getString("exchangeId"));
                            fo.put("routeId", routeId);
                            fo.put("exceptionType", event.getString("exceptionType"));
                            fo.put("exceptionMessage", event.getString("exceptionMessage"));
                            failures.add(fo);
                            eventCount++;
                        }
                    } else if (redeliveryEventName.equals(eventName)) {
                        String routeId = event.getString("routeId");
                        if (routeIdFilter == null || routeIdFilter.equals(routeId)) {
                            JsonObject ro = new JsonObject();
                            ro.put("timestamp", event.getStartTime().toString());
                            ro.put("exchangeId", event.getString("exchangeId"));
                            ro.put("routeId", routeId);
                            ro.put("attempt", event.getInt("attempt"));
                            ro.put("maxAttempts", event.getInt("maxAttempts"));
                            redeliveries.add(ro);
                            eventCount++;
                        }
                    }
                }
            }

            result.put("eventCount", eventCount);

            // routes — sorted by total descending
            JsonArray routesJson = new JsonArray();
            routeStats.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<String, DurationStats>> comparingLong(e -> e.getValue().count).reversed())
                    .forEach(e -> {
                        JsonObject jo = e.getValue().toJson();
                        jo.put("routeId", e.getKey());
                        routesJson.add(jo);
                    });
            result.put("routes", routesJson);

            // processors — sorted by mean duration descending (slowest first)
            JsonArray processorsJson = new JsonArray();
            processorStats.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<String, DurationStats>> comparingDouble(
                            e -> e.getValue().meanMs()).reversed())
                    .forEach(e -> {
                        JsonObject jo = e.getValue().toJson();
                        jo.put("processorId", e.getKey());
                        jo.put("processorType", processorTypes.get(e.getKey()));
                        jo.put("routeId", processorRoutes.get(e.getKey()));
                        processorsJson.add(jo);
                    });
            result.put("processors", processorsJson);

            // endpoints — sorted by total descending
            JsonArray endpointsJson = new JsonArray();
            endpointStats.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<String, DurationStats>> comparingLong(
                            e -> e.getValue().count).reversed())
                    .forEach(e -> {
                        JsonObject jo = e.getValue().toJson();
                        jo.put("endpointUri", e.getKey());
                        endpointsJson.add(jo);
                    });
            result.put("endpoints", endpointsJson);

            // failures — newest first, capped at limit
            failures.sort(Comparator.comparing((JsonObject o) -> o.getString("timestamp")).reversed());
            JsonArray failuresJson = new JsonArray();
            failures.stream().limit(limit).forEach(failuresJson::add);
            result.put("failures", failuresJson);

            // redeliveries — newest first, capped at limit
            redeliveries.sort(Comparator.comparing((JsonObject o) -> o.getString("timestamp")).reversed());
            JsonArray redeliveriesJson = new JsonArray();
            redeliveries.stream().limit(limit).forEach(redeliveriesJson::add);
            result.put("redeliveries", redeliveriesJson);

        } catch (IOException e) {
            result.put("error", "failed to read JFR snapshot: " + e.getMessage());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }

        return result;
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
            case "snapshot" -> doSnapshot(options).toJson();
            default -> unknownCommand(command);
        };
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        String command = optionString(options, COMMAND);
        JsonObject root = new JsonObject();
        switch (command != null ? command : "status") {
            case "status" -> {
                root.put("registered", isInstrumentationRegistered());
                JsonArray recordingsJson = new JsonArray();
                for (Recording recording : FlightRecorder.getFlightRecorder().getRecordings()) {
                    JsonObject rec = new JsonObject();
                    rec.put("name", recording.getName());
                    rec.put("state", recording.getState().toString());
                    rec.put("destination",
                            recording.getDestination() != null ? recording.getDestination().toString() : null);
                    recordingsJson.add(rec);
                }
                root.put("recordings", recordingsJson);
                JsonObject events = new JsonObject();
                for (CamelJfrEvents event : CamelJfrEvents.values()) {
                    events.put(event.getShortName(), event.isEnabled());
                }
                root.put("events", events);
            }
            case "enable", "disable" -> {
                ToggleResult result = doToggle(options, "enable".equals(command));
                root.put("success", result.success());
                root.put("result", result.message());
            }
            case "jfc" -> {
                try {
                    root.put("jfc", doJfc(options));
                } catch (IllegalArgumentException e) {
                    root.put("error", e.getMessage());
                }
            }
            case "snapshot" -> {
                return doSnapshot(options);
            }
            default -> root.put("error", unknownCommand(command));
        }
        return root;
    }

    private static String unknownCommand(String command) {
        return "unknown command: " + command + ". Valid values: status, enable, disable, jfc, snapshot";
    }
}
