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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dev console for JFR-based old object sampling. Captures objects surviving multiple GC cycles and their reference
 * chains back to GC roots, enabling memory leak diagnosis.
 */
@DevConsole(name = "jfr-memory-leak", displayName = "JFR Memory Leak",
            description = "JFR-based old object sampling for memory leak diagnosis")
@Configurer(extended = true)
public class JfrMemoryLeakDevConsole extends AbstractDevConsole {

    private static final Logger LOG = LoggerFactory.getLogger(JfrMemoryLeakDevConsole.class);

    @Metadata(label = "query", description = "Command to execute", javaType = "java.lang.String",
              defaultValue = "status", enums = "start,stop,status,query,compare")
    public static final String COMMAND = "command";

    @Metadata(label = "query", description = "Recording duration in seconds (0 means manual stop)",
              javaType = "java.lang.Integer", defaultValue = "0")
    public static final String DURATION = "duration";

    @Metadata(label = "query", description = "Limits the number of entries displayed",
              javaType = "java.lang.Integer", defaultValue = "100")
    public static final String LIMIT = "limit";

    @Metadata(label = "query", description = "Minimum object size in bytes to include in results",
              javaType = "java.lang.Long", defaultValue = "0")
    public static final String MIN_SIZE = "minSize";

    @Metadata(label = "query", description = "Whether to include stack traces in the output",
              javaType = "java.lang.Boolean", defaultValue = "false")
    public static final String STACKTRACE = "stacktrace";

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_STACK_FRAMES = 10;
    private static final int MAX_CHAIN_DEPTH = 20;

    private volatile Recording activeRecording;
    private volatile JsonObject cachedResults;
    private volatile JsonObject previousResults;
    private volatile long recordingStartTime;
    private volatile int requestedDurationSeconds;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> autoStopFuture;

    public JfrMemoryLeakDevConsole() {
        super("jvm", "jfr-memory-leak", "JFR Memory Leak",
              "JFR-based old object sampling for memory leak diagnosis");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        JsonObject json = doCallJson(options);
        return json.toJson();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        String command = optionString(options, COMMAND);
        if (command == null) {
            command = "status";
        }

        return switch (command) {
            case "start" -> doStart(options);
            case "stop" -> doStop(options);
            case "status" -> doStatus();
            case "query" -> doQuery(options);
            case "compare" -> doCompare(options);
            default -> errorJson("Unknown command: " + command);
        };
    }

    private synchronized JsonObject doStart(Map<String, Object> options) {
        if (activeRecording != null) {
            return errorJson("A JFR recording is already active. Stop it first.");
        }

        try {
            Recording rec = new Recording();
            rec.setName("Camel OldObjectSample");
            rec.enable("jdk.OldObjectSample").withStackTrace().withPeriod(Duration.ofSeconds(1));
            rec.enable("jdk.GarbageCollection");

            int duration = optionInt(options, DURATION, 0);
            requestedDurationSeconds = duration;
            if (duration > 0) {
                rec.setMaxAge(Duration.ofSeconds(duration + 10));
            }

            // trigger GC before starting to establish a cleaner baseline
            System.gc();
            try {
                StopWatch watch = new StopWatch();
                long remaining;
                while ((remaining = 500 - watch.taken()) > 0) {
                    wait(remaining);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
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
                return applyFilters(cachedResults, options);
            }
            return errorJson("No active JFR recording to stop.");
        }

        cancelAutoStop();
        int limit = optionInt(options, LIMIT, DEFAULT_LIMIT);

        try {
            JsonObject result = doStopRecordingAndParse(limit);
            return applyFilters(result, options);
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
            tempFile = Files.createTempFile("camel-jfr-memory-leak-", ".jfr");
            // trigger GC before stopping to flush objects into the recording
            System.gc();
            try {
                StopWatch watch = new StopWatch();
                long remaining;
                while ((remaining = 500 - watch.taken()) > 0) {
                    wait(remaining);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            rec.stop();
            rec.dump(tempFile);

            long endTime = System.currentTimeMillis();
            long durationMs = endTime - recordingStartTime;
            JsonObject result = parseRecordingFile(tempFile, limit);
            result.put("status", "completed");
            result.put("recordingDurationMs", durationMs);
            result.put("recordingEndTime", endTime);

            previousResults = cachedResults;
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
            result.put("hasComparisonData", previousResults != null);
            result.put("sampleCount", cachedResults.getIntegerOrDefault("sampleCount", 0));
        } else {
            result.put("status", "idle");
        }
        return result;
    }

    private JsonObject doQuery(Map<String, Object> options) {
        if (cachedResults != null) {
            return applyFilters(cachedResults, options);
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

    private JsonObject doCompare(Map<String, Object> options) {
        if (previousResults == null || cachedResults == null) {
            return errorJson("Need two recordings to compare. Run two recordings first.");
        }

        long minSize = optionLong(options, MIN_SIZE, 0);

        long baselineDurationMs = previousResults.getLongOrDefault("recordingDurationMs", 1);
        long currentDurationMs = cachedResults.getLongOrDefault("recordingDurationMs", 1);
        double durationRatio = baselineDurationMs > 0 ? (double) currentDurationMs / baselineDurationMs : 1.0;

        // index baseline samples by group key
        Map<String, JsonObject> baselineMap = new LinkedHashMap<>();
        JsonArray baselineSamples = (JsonArray) previousResults.get("samples");
        if (baselineSamples != null) {
            for (int i = 0; i < baselineSamples.size(); i++) {
                JsonObject s = (JsonObject) baselineSamples.get(i);
                baselineMap.put(sampleGroupKey(s), s);
            }
        }

        // index current samples by group key
        Map<String, JsonObject> currentMap = new LinkedHashMap<>();
        JsonArray currentSamples = (JsonArray) cachedResults.get("samples");
        if (currentSamples != null) {
            for (int i = 0; i < currentSamples.size(); i++) {
                JsonObject s = (JsonObject) currentSamples.get(i);
                currentMap.put(sampleGroupKey(s), s);
            }
        }

        // collect all keys preserving order (current first, then baseline-only)
        Map<String, JsonObject> allKeys = new LinkedHashMap<>(currentMap);
        for (String key : baselineMap.keySet()) {
            allKeys.putIfAbsent(key, baselineMap.get(key));
        }

        JsonArray comparisons = new JsonArray();
        for (String key : allKeys.keySet()) {
            JsonObject baseline = baselineMap.get(key);
            JsonObject current = currentMap.get(key);

            JsonObject comp = new JsonObject();
            String className = current != null
                    ? current.getStringOrDefault("allocationClass", "unknown")
                    : baseline.getStringOrDefault("allocationClass", "unknown");
            comp.put("allocationClass", className);

            long baseSize = baseline != null ? baseline.getLongOrDefault("sampledSize", 0) : 0;
            int baseCount = baseline != null ? baseline.getIntegerOrDefault("count", 0) : 0;
            long curSize = current != null ? current.getLongOrDefault("sampledSize", 0) : 0;
            int curCount = current != null ? current.getIntegerOrDefault("count", 0) : 0;

            if (minSize > 0 && baseSize < minSize && curSize < minSize) {
                continue;
            }

            comp.put("baselineSampledSize", baseSize);
            comp.put("baselineCount", baseCount);
            comp.put("currentSampledSize", curSize);
            comp.put("currentCount", curCount);

            String trend;
            double growthRatio = 0;
            if (baseline == null) {
                trend = "new";
            } else if (current == null) {
                trend = "gone";
            } else if (baseSize == 0) {
                trend = curSize > 0 ? "new" : "stable";
            } else {
                growthRatio = ((double) curSize / baseSize) / durationRatio;
                if (growthRatio >= 1.2) {
                    trend = "growing";
                } else if (growthRatio >= 1.1) {
                    trend = "suspicious";
                } else if (growthRatio < 0.8) {
                    trend = "shrinking";
                } else {
                    trend = "stable";
                }
            }
            comp.put("growthRatio", Math.round(growthRatio * 100.0) / 100.0);
            comp.put("trend", trend);

            // flag low confidence when sample counts are too low or diverge
            // significantly from the expected duration ratio
            boolean lowConfidence = false;
            if (baseCount > 0 && curCount > 0) {
                if (baseCount < 5 || curCount < 5) {
                    lowConfidence = true;
                } else {
                    double countRatio = (double) curCount / baseCount;
                    double deviation = countRatio / durationRatio;
                    if (deviation < 0.3 || deviation > 3.0) {
                        lowConfidence = true;
                    }
                }
            }
            comp.put("lowConfidence", lowConfidence);

            // carry forward reference chain and stack trace from current (or baseline if gone)
            JsonObject source = current != null ? current : baseline;
            if (source.containsKey("referenceChain")) {
                comp.put("referenceChain", source.get("referenceChain"));
            }
            if (source.containsKey("stackTrace")) {
                comp.put("stackTrace", source.get("stackTrace"));
            }

            comparisons.add(comp);
        }

        // sort by growth ratio descending (leaks first)
        List<Object> sorted = new ArrayList<>(comparisons);
        sorted.sort((a, b) -> {
            double ra = ((JsonObject) a).getDoubleOrDefault("growthRatio", 0);
            double rb = ((JsonObject) b).getDoubleOrDefault("growthRatio", 0);
            return Double.compare(rb, ra);
        });
        JsonArray sortedArr = new JsonArray();
        sortedArr.addAll(sorted);

        JsonObject result = new JsonObject();
        result.put("status", "compared");

        JsonObject baselineInfo = new JsonObject();
        baselineInfo.put("recordingDurationMs", baselineDurationMs);
        baselineInfo.put("sampleCount", previousResults.getIntegerOrDefault("sampleCount", 0));
        baselineInfo.put("gcCount", previousResults.getIntegerOrDefault("gcCount", 0));
        result.put("baseline", baselineInfo);

        JsonObject currentInfo = new JsonObject();
        currentInfo.put("recordingDurationMs", currentDurationMs);
        currentInfo.put("sampleCount", cachedResults.getIntegerOrDefault("sampleCount", 0));
        currentInfo.put("gcCount", cachedResults.getIntegerOrDefault("gcCount", 0));
        result.put("current", currentInfo);

        result.put("durationRatio", Math.round(durationRatio * 100.0) / 100.0);
        result.put("comparisons", sortedArr);
        return result;
    }

    private JsonObject applyFilters(JsonObject original, Map<String, Object> options) {
        long minSize = optionLong(options, MIN_SIZE, 0);
        boolean includeStacktrace = optionBoolean(options, STACKTRACE, false);

        if (minSize <= 0 && includeStacktrace) {
            return original;
        }

        // work on a copy so the cached results remain unmodified
        JsonObject result = new JsonObject(original);
        Object samplesObj = original.get("samples");
        if (samplesObj instanceof JsonArray origSamples) {
            JsonArray filtered = new JsonArray();
            for (int i = 0; i < origSamples.size(); i++) {
                Object obj = origSamples.get(i);
                if (obj instanceof JsonObject sample) {
                    if (minSize > 0 && sample.getLongOrDefault("sampledSize", 0) < minSize) {
                        continue;
                    }
                    if (!includeStacktrace) {
                        // shallow copy to avoid mutating cached data
                        JsonObject copy = new JsonObject(sample);
                        copy.remove("stackTrace");
                        filtered.add(copy);
                    } else {
                        filtered.add(sample);
                    }
                }
            }
            result.put("samples", filtered);
            result.put("sampleCount", filtered.size());
        }
        return result;
    }

    private JsonObject parseRecordingFile(Path file, int limit) throws IOException {
        // parse all raw samples and count GC events
        List<JsonObject> rawSamples = new ArrayList<>();
        int gcCount = 0;
        try (RecordingFile rf = new RecordingFile(file)) {
            while (rf.hasMoreEvents()) {
                RecordedEvent event = rf.readEvent();
                String eventName = event.getEventType().getName();
                if ("jdk.GarbageCollection".equals(eventName)) {
                    gcCount++;
                    continue;
                }
                if (!"jdk.OldObjectSample".equals(eventName)) {
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
                sample.put("sampledSize", size);
                groups.put(key, sample);
            } else {
                existing.put("count", existing.getIntegerOrDefault("count", 1) + 1);
                long prevTotal = existing.getLongOrDefault("sampledSize", 0);
                long curSize = sample.getLongOrDefault("allocationSize", 0);
                existing.put("sampledSize", prevTotal + curSize);
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
        root.put("gcCount", gcCount);
        return root;
    }

    private static String sampleGroupKey(JsonObject sample) {
        StringBuilder sb = new StringBuilder();
        sb.append(sample.getStringOrDefault("allocationClass", ""));
        // find the first user-code frame (skip JDK internals and Camel framework frames)
        // this gives stable keys across JFR runs since user code frames don't shift
        JsonArray st = (JsonArray) sample.get("stackTrace");
        if (st != null) {
            for (int i = 0; i < st.size(); i++) {
                JsonObject frame = (JsonObject) st.get(i);
                String method = frame.getStringOrDefault("method", "");
                if (isUserFrame(method)) {
                    int lambdaIdx = method.indexOf("$$Lambda");
                    if (lambdaIdx > 0) {
                        int lastDot = method.lastIndexOf('.');
                        method = method.substring(0, lambdaIdx) + "$$Lambda."
                                 + (lastDot > lambdaIdx ? method.substring(lastDot + 1) : "apply");
                    }
                    sb.append('|').append(method);
                    break;
                }
            }
        }
        return sb.toString();
    }

    private static boolean isUserFrame(String method) {
        return !method.startsWith("java.")
                && !method.startsWith("javax.")
                && !method.startsWith("jakarta.")
                && !method.startsWith("jdk.")
                && !method.startsWith("sun.")
                && !method.startsWith("org.apache.camel.");
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
                    sample.put("allocationClass", StringHelper.readableClassName(type.getName()));
                }
            } catch (Exception e) {
                // ignore
            }
        }
        if (!sample.containsKey("allocationClass") && event.hasField("objectClass")) {
            try {
                RecordedClass objectClass = event.getClass("objectClass");
                if (objectClass != null) {
                    sample.put("allocationClass", StringHelper.readableClassName(objectClass.getName()));
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
                            link.put("type", StringHelper.readableClassName(type.getName()));
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
                    link.put("type", StringHelper.readableClassName(type.getName()));
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

    private void cancelAutoStop() {
        if (autoStopFuture != null) {
            autoStopFuture.cancel(false);
            autoStopFuture = null;
        }
    }

    private void ensureScheduler() {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "JfrMemoryLeakAutoStop");
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

    private static JsonObject errorJson(String message) {
        JsonObject result = new JsonObject();
        result.put("status", "error");
        result.put("error", message);
        return result;
    }
}
