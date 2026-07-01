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
package org.apache.camel.impl.console;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dev console for JFR-based old object sampling. Captures objects surviving multiple GC cycles and their reference
 * chains back to GC roots, enabling memory leak diagnosis.
 */
@DevConsole(name = "jfr-old-objects", displayName = "JFR Old Object Samples",
            description = "JFR-based old object sampling for memory leak diagnosis")
@Configurer(extended = true)
public class JfrOldObjectSampleDevConsole extends AbstractDevConsole {

    private static final Logger LOG = LoggerFactory.getLogger(JfrOldObjectSampleDevConsole.class);

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_STACK_FRAMES = 10;
    private static final int MAX_CHAIN_DEPTH = 20;

    private volatile Recording activeRecording;
    private volatile JsonObject cachedResults;
    private volatile long recordingStartTime;
    private volatile int requestedDurationSeconds;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> autoStopFuture;

    public JfrOldObjectSampleDevConsole() {
        super("jvm", "jfr-old-objects", "JFR Old Object Samples",
              "JFR-based old object sampling for memory leak diagnosis");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        JsonObject json = doCallJson(options);
        return json.toJson();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        String command = optionString(options, "command");
        if (command == null) {
            command = "status";
        }

        return switch (command) {
            case "start" -> doStart(options);
            case "stop" -> doStop(options);
            case "status" -> doStatus();
            case "query" -> doQuery(options);
            default -> errorJson("Unknown command: " + command);
        };
    }

    private JsonObject doStart(Map<String, Object> options) {
        if (activeRecording != null) {
            return errorJson("A JFR recording is already active. Stop it first.");
        }

        try {
            Recording rec = new Recording();
            rec.setName("Camel OldObjectSample");
            rec.enable("jdk.OldObjectSample").withStackTrace().withPeriod(Duration.ofSeconds(1));

            int duration = optionInt(options, "duration", 0);
            requestedDurationSeconds = duration;
            if (duration > 0) {
                rec.setMaxAge(Duration.ofSeconds(duration + 10));
            }

            rec.start();
            activeRecording = rec;
            recordingStartTime = System.currentTimeMillis();

            if (duration > 0) {
                ensureScheduler();
                autoStopFuture = scheduler.schedule(() -> {
                    try {
                        doStopRecordingAndParse(DEFAULT_LIMIT);
                    } catch (Exception e) {
                        LOG.warn("Error auto-stopping JFR recording: {}", e.getMessage(), e);
                    }
                }, duration, TimeUnit.SECONDS);
            }

            JsonObject result = new JsonObject();
            result.put("status", "recording");
            result.put("startTime", recordingStartTime);
            if (duration > 0) {
                result.put("durationSeconds", duration);
            }
            return result;
        } catch (Exception e) {
            LOG.warn("Failed to start JFR recording: {}", e.getMessage(), e);
            return errorJson("Failed to start JFR recording: " + e.getMessage());
        }
    }

    private JsonObject doStop(Map<String, Object> options) {
        if (activeRecording == null) {
            if (cachedResults != null) {
                return cachedResults;
            }
            return errorJson("No active JFR recording to stop.");
        }

        cancelAutoStop();
        int limit = optionInt(options, "limit", DEFAULT_LIMIT);

        try {
            return doStopRecordingAndParse(limit);
        } catch (Exception e) {
            LOG.warn("Error stopping JFR recording: {}", e.getMessage(), e);
            return errorJson("Error stopping JFR recording: " + e.getMessage());
        }
    }

    private synchronized JsonObject doStopRecordingAndParse(int limit) {
        Recording rec = activeRecording;
        if (rec == null) {
            return cachedResults != null ? cachedResults : errorJson("No active recording.");
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("camel-jfr-old-objects-", ".jfr");
            // trigger GC before stopping to flush objects into the recording
            System.gc();
            rec.stop();
            rec.dump(tempFile);

            long endTime = System.currentTimeMillis();
            long durationMs = endTime - recordingStartTime;
            JsonObject result = parseRecordingFile(tempFile, limit);
            result.put("status", "completed");
            result.put("recordingDurationMs", durationMs);
            result.put("recordingEndTime", endTime);

            cachedResults = result;
            return result;
        } catch (IOException e) {
            LOG.warn("Error parsing JFR recording: {}", e.getMessage(), e);
            return errorJson("Error parsing JFR recording: " + e.getMessage());
        } finally {
            rec.close();
            activeRecording = null;
            recordingStartTime = 0;
            requestedDurationSeconds = 0;
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private JsonObject doStatus() {
        JsonObject result = new JsonObject();
        if (activeRecording != null) {
            result.put("status", "recording");
            result.put("startTime", recordingStartTime);
            long elapsed = System.currentTimeMillis() - recordingStartTime;
            result.put("elapsedMs", elapsed);
            if (requestedDurationSeconds > 0) {
                result.put("durationSeconds", requestedDurationSeconds);
                long remaining = (requestedDurationSeconds * 1000L) - elapsed;
                result.put("remainingMs", Math.max(0, remaining));
            }
        } else if (cachedResults != null) {
            result.put("status", "completed");
            result.put("hasCachedResults", true);
            result.put("sampleCount", cachedResults.getIntegerOrDefault("sampleCount", 0));
        } else {
            result.put("status", "idle");
        }
        return result;
    }

    private JsonObject doQuery(Map<String, Object> options) {
        if (cachedResults != null) {
            return cachedResults;
        }
        if (activeRecording != null) {
            return doStatus();
        }
        JsonObject result = new JsonObject();
        result.put("status", "idle");
        result.put("sampleCount", 0);
        result.put("samples", new JsonArray());
        result.put("note", "No results available. Start a recording first.");
        return result;
    }

    private JsonObject parseRecordingFile(Path file, int limit) throws IOException {
        // parse all raw samples first
        List<JsonObject> rawSamples = new ArrayList<>();
        try (RecordingFile rf = new RecordingFile(file)) {
            while (rf.hasMoreEvents()) {
                RecordedEvent event = rf.readEvent();
                if (!"jdk.OldObjectSample".equals(event.getEventType().getName())) {
                    continue;
                }
                JsonObject sample = parseOldObjectSampleEvent(event);
                if (sample != null) {
                    rawSamples.add(sample);
                }
            }
        }

        // aggregate by class + stack trace fingerprint
        Map<String, JsonObject> groups = new LinkedHashMap<>();
        for (JsonObject sample : rawSamples) {
            String key = sampleGroupKey(sample);
            JsonObject existing = groups.get(key);
            if (existing == null) {
                sample.put("count", 1);
                long size = sample.getLongOrDefault("allocationSize", 0);
                sample.put("totalSize", size);
                groups.put(key, sample);
            } else {
                existing.put("count", existing.getIntegerOrDefault("count", 1) + 1);
                long prevTotal = existing.getLongOrDefault("totalSize", 0);
                long curSize = sample.getLongOrDefault("allocationSize", 0);
                existing.put("totalSize", prevTotal + curSize);
                long prevAge = existing.getLongOrDefault("objectAge", 0);
                long curAge = sample.getLongOrDefault("objectAge", 0);
                if (curAge > prevAge) {
                    existing.put("objectAge", curAge);
                }
            }
        }

        JsonArray samples = new JsonArray();
        int count = 0;
        for (JsonObject group : groups.values()) {
            if (limit > 0 && count >= limit) {
                break;
            }
            samples.add(group);
            count++;
        }

        JsonObject root = new JsonObject();
        root.put("samples", samples);
        root.put("sampleCount", count);
        root.put("rawSampleCount", rawSamples.size());
        return root;
    }

    private static String sampleGroupKey(JsonObject sample) {
        StringBuilder sb = new StringBuilder();
        sb.append(sample.getStringOrDefault("allocationClass", ""));
        JsonArray st = (JsonArray) sample.get("stackTrace");
        if (st != null) {
            for (int i = 0; i < st.size(); i++) {
                JsonObject frame = (JsonObject) st.get(i);
                sb.append('|').append(frame.getStringOrDefault("method", ""))
                        .append(':').append(frame.getIntegerOrDefault("line", 0));
            }
        }
        return sb.toString();
    }

    private JsonObject parseOldObjectSampleEvent(RecordedEvent event) {
        JsonObject sample = new JsonObject();

        // extract the OldObject reference (contains the sampled object's class and reference chain)
        RecordedObject objectRef = null;
        if (event.hasField("object")) {
            try {
                objectRef = event.getValue("object");
            } catch (Exception e) {
                // ignore
            }
        }

        // allocation class — primary source is object.type, fallback to objectClass on event
        if (objectRef != null && objectRef.hasField("type")) {
            try {
                RecordedClass type = objectRef.getClass("type");
                if (type != null) {
                    sample.put("allocationClass", readableClassName(type.getName()));
                }
            } catch (Exception e) {
                // ignore
            }
        }
        if (!sample.containsKey("allocationClass") && event.hasField("objectClass")) {
            try {
                RecordedClass objectClass = event.getClass("objectClass");
                if (objectClass != null) {
                    sample.put("allocationClass", readableClassName(objectClass.getName()));
                }
            } catch (Exception e) {
                // ignore
            }
        }

        // allocation size — try multiple field names across JDK versions
        if (event.hasField("allocationSize")) {
            sample.put("allocationSize", event.getLong("allocationSize"));
        } else if (event.hasField("objectSize")) {
            sample.put("allocationSize", event.getLong("objectSize"));
        }

        // last known heap usage
        if (event.hasField("lastKnownHeapUsage")) {
            sample.put("lastKnownHeapUsage", event.getLong("lastKnownHeapUsage"));
        }

        // array elements
        if (event.hasField("arrayElements")) {
            int arrayElements = event.getInt("arrayElements");
            if (arrayElements > 0) {
                sample.put("arrayElements", arrayElements);
            }
        }

        // object age
        if (event.hasField("objectAge")) {
            try {
                sample.put("objectAge", event.getDuration("objectAge").toMillis());
            } catch (Exception e) {
                // some JDK versions may not support this field as Duration
            }
        }

        // allocation time
        sample.put("allocationTime", event.getStartTime().toEpochMilli());

        // stack trace (where the object was allocated)
        RecordedStackTrace stackTrace = event.getStackTrace();
        if (stackTrace != null) {
            JsonArray frames = new JsonArray();
            for (RecordedFrame frame : stackTrace.getFrames()) {
                JsonObject f = new JsonObject();
                f.put("method", frame.getMethod().getType().getName() + "." + frame.getMethod().getName());
                f.put("line", frame.getLineNumber());
                frames.add(f);
                if (frames.size() >= MAX_STACK_FRAMES) {
                    break;
                }
            }
            sample.put("stackTrace", frames);
        }

        // reference chain (path from object to GC root)
        if (objectRef != null) {
            JsonArray chain = extractReferenceChain(objectRef);
            appendGcRoot(event, chain);
            if (!chain.isEmpty()) {
                sample.put("referenceChain", chain);
            }
        }

        return sample;
    }

    private JsonArray extractReferenceChain(RecordedObject objectRef) {
        JsonArray chain = new JsonArray();
        try {
            RecordedObject obj = objectRef;
            int depth = 0;
            while (obj != null && depth < MAX_CHAIN_DEPTH) {
                JsonObject link = new JsonObject();

                if (obj.hasField("type")) {
                    try {
                        RecordedClass type = obj.getClass("type");
                        if (type != null) {
                            link.put("type", readableClassName(type.getName()));
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }

                if (obj.hasField("field")) {
                    try {
                        RecordedObject field = obj.getValue("field");
                        if (field != null && field.hasField("name")) {
                            link.put("field", field.getString("name"));
                        }
                    } catch (Exception e) {
                        try {
                            String fieldName = obj.getString("field");
                            if (fieldName != null) {
                                link.put("field", fieldName);
                            }
                        } catch (Exception ex) {
                            // ignore
                        }
                    }
                }

                if (obj.hasField("description")) {
                    try {
                        String desc = obj.getString("description");
                        if (desc != null && !desc.isEmpty()) {
                            link.put("description", desc);
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }

                if (!link.isEmpty()) {
                    chain.add(link);
                }

                // walk to next referrer
                if (obj.hasField("referrer")) {
                    try {
                        obj = obj.getValue("referrer");
                    } catch (Exception e) {
                        break;
                    }
                } else {
                    break;
                }
                depth++;
            }
        } catch (Exception e) {
            LOG.debug("Error extracting reference chain: {}", e.getMessage());
        }
        return chain;
    }

    private void appendGcRoot(RecordedEvent event, JsonArray chain) {
        if (!event.hasField("root")) {
            return;
        }
        try {
            RecordedObject root = event.getValue("root");
            if (root == null) {
                return;
            }
            JsonObject link = new JsonObject();
            if (root.hasField("type")) {
                RecordedClass type = root.getClass("type");
                if (type != null) {
                    link.put("type", readableClassName(type.getName()));
                }
            }
            if (root.hasField("description")) {
                String desc = root.getString("description");
                if (desc != null && !desc.isEmpty()) {
                    link.put("description", desc);
                }
            }
            if (root.hasField("system")) {
                try {
                    String system = root.getString("system");
                    if (system != null && !system.isEmpty()) {
                        link.put("description",
                                link.getStringOrDefault("description", "") + " [GC Root: " + system + "]");
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
            if (!link.isEmpty()) {
                chain.add(link);
            }
        } catch (Exception e) {
            LOG.debug("Error extracting GC root: {}", e.getMessage());
        }
    }

    static String readableClassName(String name) {
        if (name == null) {
            return null;
        }
        // JVM array descriptors → human-readable
        if (name.startsWith("[")) {
            int dims = 0;
            int i = 0;
            while (i < name.length() && name.charAt(i) == '[') {
                dims++;
                i++;
            }
            String suffix = "[]".repeat(dims);
            if (i < name.length()) {
                String element = switch (name.charAt(i)) {
                    case 'B' -> "byte";
                    case 'C' -> "char";
                    case 'D' -> "double";
                    case 'F' -> "float";
                    case 'I' -> "int";
                    case 'J' -> "long";
                    case 'S' -> "short";
                    case 'Z' -> "boolean";
                    case 'L' -> name.substring(i + 1, name.endsWith(";") ? name.length() - 1 : name.length());
                    default -> name.substring(i);
                };
                return element + suffix;
            }
        }
        return name;
    }

    private void cancelAutoStop() {
        if (autoStopFuture != null) {
            autoStopFuture.cancel(false);
            autoStopFuture = null;
        }
    }

    private void ensureScheduler() {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "JfrOldObjectSampleAutoStop");
                t.setDaemon(true);
                return t;
            });
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        cancelAutoStop();

        Recording rec = activeRecording;
        if (rec != null) {
            try {
                rec.stop();
                rec.close();
            } catch (Exception e) {
                // ignore
            }
            activeRecording = null;
        }

        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private static String optionString(Map<String, Object> options, String key) {
        Object val = options.get(key);
        return val != null ? val.toString() : null;
    }

    private static int optionInt(Map<String, Object> options, String key, int defaultValue) {
        Object val = options.get(key);
        if (val != null) {
            try {
                return Integer.parseInt(val.toString());
            } catch (NumberFormatException e) {
                // use default
            }
        }
        return defaultValue;
    }

    private static JsonObject errorJson(String message) {
        JsonObject result = new JsonObject();
        result.put("status", "error");
        result.put("error", message);
        return result;
    }
}
