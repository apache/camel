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
import java.util.Comparator;
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
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.JsonRecordSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dev console for JFR-based old object sampling. Captures objects surviving multiple GC cycles and their reference
 * chains back to GC roots, enabling memory leak diagnosis.
 */
@DevConsole(name = "jfr-memory-leak", displayName = "JFR Memory Leak",
            description = "JFR-based old object sampling for memory leak diagnosis", readOnly = false)
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

    public record StackFrameEntry(
            @Metadata(description = "The fully qualified method name") String method,
            @Metadata(description = "The line number") int line) {
    }

    public record ReferenceLink(
            @Metadata(description = "The type name (only present when known)") String type,
            @Metadata(description = "The field name (only present when known)") String field,
            @Metadata(description = "A description of the link (only present when known)") String description) {
    }

    public record SampleEntry(
            @Metadata(description = "The allocated class name (only present when known)") String allocationClass,
            @Metadata(description = "The allocation size in bytes (only present when known)") Long allocationSize,
            @Metadata(description = "The last known heap usage in bytes (only present when known)") Long lastKnownHeapUsage,
            @Metadata(description = "The number of array elements (only present for arrays)") Integer arrayElements,
            @Metadata(description = "The object age in milliseconds (only present when known)") Long objectAge,
            @Metadata(description = "Epoch time in milliseconds when the object was allocated") long allocationTime,
            @Metadata(description = "The allocation stack trace (only present when requested)") List<StackFrameEntry> stackTrace,
            @Metadata(description = "The reference chain back to the GC root (only present when there is one)") List<ReferenceLink> referenceChain,
            @Metadata(description = "The number of samples aggregated into this entry") int count,
            @Metadata(description = "The total sampled size in bytes across the aggregated samples") long sampledSize) {
    }

    public record ComparisonEntry(
            @Metadata(description = "The allocated class name") String allocationClass,
            @Metadata(description = "The baseline sampled size in bytes") long baselineSampledSize,
            @Metadata(description = "The baseline sample count") int baselineCount,
            @Metadata(description = "The current sampled size in bytes") long currentSampledSize,
            @Metadata(description = "The current sample count") int currentCount,
            @Metadata(description = "The growth ratio between baseline and current sampled size") double growthRatio,
            @Metadata(description = "The trend: new, gone, growing, suspicious, shrinking, or stable") String trend,
            @Metadata(description = "Whether the comparison has low statistical confidence") boolean lowConfidence,
            @Metadata(description = "The reference chain back to the GC root (only present when there is one)") List<ReferenceLink> referenceChain,
            @Metadata(description = "The allocation stack trace (only present when requested)") List<StackFrameEntry> stackTrace) {
    }

    public record RecordingInfo(
            @Metadata(description = "The recording duration in milliseconds") long recordingDurationMs,
            @Metadata(description = "The number of aggregated samples") int sampleCount,
            @Metadata(description = "The number of garbage collections observed") int gcCount) {
    }

    public record Response(
            @Metadata(description = "The console status: recording, completed, compared, idle, or error") String status,
            @Metadata(description = "An error message (only present when status is error)") String error,
            @Metadata(description = "An informational note (only present for an idle query)") String note,
            @Metadata(description = "Epoch time in milliseconds the recording started (only present while recording)") Long startTime,
            @Metadata(description = "The requested recording duration in seconds (only present when set)") Integer durationSeconds,
            @Metadata(description = "Elapsed time in milliseconds since the recording started (only present while recording)") Long elapsedMs,
            @Metadata(description = "Remaining time in milliseconds until auto-stop (only present when a duration was requested)") Long remainingMs,
            @Metadata(description = "Whether cached results are available (only present for a status query)") Boolean hasCachedResults,
            @Metadata(description = "Whether a previous recording is available for comparison (only present for a status query)") Boolean hasComparisonData,
            @Metadata(description = "The number of aggregated samples (only present when results are available)") Integer sampleCount,
            @Metadata(description = "The recording duration in milliseconds (only present when results are available)") Long recordingDurationMs,
            @Metadata(description = "Epoch time in milliseconds the recording ended (only present when results are available)") Long recordingEndTime,
            @Metadata(description = "The number of raw samples before aggregation (only present when results are available)") Integer rawSampleCount,
            @Metadata(description = "The number of garbage collections observed (only present when results are available)") Integer gcCount,
            @Metadata(description = "The aggregated samples (only present when results are available)") List<SampleEntry> samples,
            @Metadata(description = "The baseline recording info (only present for a comparison)") RecordingInfo baseline,
            @Metadata(description = "The current recording info (only present for a comparison)") RecordingInfo current,
            @Metadata(description = "The ratio of the current to the baseline recording duration (only present for a comparison)") Double durationRatio,
            @Metadata(description = "The per-class comparisons, sorted by growth ratio descending (only present for a comparison)") List<ComparisonEntry> comparisons) {
    }

    private record RawSample(
            String allocationClass, Long allocationSize, Long lastKnownHeapUsage, Integer arrayElements,
            Long objectAge, long allocationTime, List<StackFrameEntry> stackTrace, List<ReferenceLink> referenceChain) {
    }

    private record ParsedSamples(List<SampleEntry> samples, int sampleCount, int rawSampleCount, int gcCount) {
    }

    private record RecordingSnapshot(
            List<SampleEntry> samples, int sampleCount, int rawSampleCount, int gcCount, long recordingDurationMs,
            long recordingEndTime) {
    }

    private static final class SampleAccumulator {
        private final RawSample first;
        private int count = 1;
        private long sampledSize;
        private Long maxObjectAge;

        SampleAccumulator(RawSample first) {
            this.first = first;
            this.sampledSize = first.allocationSize() != null ? first.allocationSize() : 0;
            this.maxObjectAge = first.objectAge();
        }

        void merge(RawSample sample) {
            count++;
            sampledSize += sample.allocationSize() != null ? sample.allocationSize() : 0;
            Long age = sample.objectAge();
            if (age != null && (maxObjectAge == null || age > maxObjectAge)) {
                maxObjectAge = age;
            }
        }

        SampleEntry toEntry() {
            return new SampleEntry(
                    first.allocationClass(), first.allocationSize(), first.lastKnownHeapUsage(),
                    first.arrayElements(), maxObjectAge, first.allocationTime(), first.stackTrace(),
                    first.referenceChain(), count, sampledSize);
        }
    }

    // Private monitor for GC wait delays so that wait() does not release the
    // 'this' monitor of synchronized methods (which guards recording state).
    private final Object gcWaitMonitor = new Object();

    private volatile Recording activeRecording;
    private volatile RecordingSnapshot cachedResults;
    private volatile RecordingSnapshot previousResults;
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
        return ((JsonObject) doCallJson(options)).toJson();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        String command = optionString(options, COMMAND);
        if (command == null) {
            command = "status";
        }

        Response response = switch (command) {
            case "start" -> doStart(options);
            case "stop" -> doStop(options);
            case "status" -> doStatus();
            case "query" -> doQuery(options);
            case "compare" -> doCompare(options);
            default -> errorResponse("Unknown command: " + command);
        };
        return JsonRecordSupport.toJsonObject(response);
    }

    private synchronized Response doStart(Map<String, Object> options) {
        if (activeRecording != null) {
            return errorResponse("A JFR recording is already active. Stop it first.");
        }

        Recording rec = new Recording();
        try {
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
                synchronized (gcWaitMonitor) {
                    StopWatch watch = new StopWatch();
                    long remaining;
                    while ((remaining = 500 - watch.taken()) > 0) {
                        gcWaitMonitor.wait(remaining);
                    }
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

            return recordingStartedResponse(recordingStartTime, duration > 0 ? duration : null);
        } catch (Exception e) {
            // Clean up state in case the exception occurred after activeRecording was set
            // (e.g. during auto-stop scheduling), so the console does not get stuck
            // referencing a closed Recording.
            cancelAutoStop();
            activeRecording = null;
            recordingStartTime = 0;
            requestedDurationSeconds = 0;
            rec.close();
            LOG.warn("Failed to start JFR recording: {}", e.getMessage(), e);
            return errorResponse("Failed to start JFR recording: " + e.getMessage());
        }
    }

    private Response doStop(Map<String, Object> options) {
        if (activeRecording == null) {
            if (cachedResults != null) {
                return snapshotResponse("completed", applyFilters(cachedResults, options));
            }
            return errorResponse("No active JFR recording to stop.");
        }

        cancelAutoStop();
        int limit = optionInt(options, LIMIT, DEFAULT_LIMIT);

        try {
            RecordingSnapshot snapshot = doStopRecordingAndParse(limit);
            return snapshotResponse("completed", applyFilters(snapshot, options));
        } catch (IOException e) {
            LOG.warn("Error parsing JFR recording: {}", e.getMessage(), e);
            return errorResponse("Error parsing JFR recording: " + e.getMessage());
        } catch (Exception e) {
            LOG.warn("Error stopping JFR recording: {}", e.getMessage(), e);
            return errorResponse("Error stopping JFR recording: " + e.getMessage());
        }
    }

    private synchronized RecordingSnapshot doStopRecordingAndParse(int limit) throws IOException {
        Recording rec = activeRecording;
        if (rec == null) {
            return cachedResults != null ? cachedResults : new RecordingSnapshot(List.of(), 0, 0, 0, 0, 0);
        }

        Path tempDir = null;
        Path tempFile = null;
        try {
            tempDir = Files.createTempDirectory("camel-jfr-");
            tempFile = Files.createTempFile(tempDir, "camel-jfr-memory-leak-", ".jfr");
            // trigger GC before stopping to flush objects into the recording
            System.gc();
            try {
                synchronized (gcWaitMonitor) {
                    StopWatch watch = new StopWatch();
                    long remaining;
                    while ((remaining = 500 - watch.taken()) > 0) {
                        gcWaitMonitor.wait(remaining);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            rec.stop();
            rec.dump(tempFile);

            long endTime = System.currentTimeMillis();
            long durationMs = endTime - recordingStartTime;
            ParsedSamples parsed = parseRecordingFile(tempFile, limit);
            RecordingSnapshot snapshot = new RecordingSnapshot(
                    parsed.samples(), parsed.sampleCount(), parsed.rawSampleCount(), parsed.gcCount(), durationMs,
                    endTime);

            previousResults = cachedResults;
            cachedResults = snapshot;
            return snapshot;
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
            if (tempDir != null) {
                try {
                    Files.deleteIfExists(tempDir);
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private Response doStatus() {
        if (activeRecording != null) {
            long elapsed = System.currentTimeMillis() - recordingStartTime;
            Integer durationSeconds = null;
            Long remainingMs = null;
            if (requestedDurationSeconds > 0) {
                durationSeconds = requestedDurationSeconds;
                remainingMs = Math.max(0, (requestedDurationSeconds * 1000L) - elapsed);
            }
            return recordingStatusResponse(recordingStartTime, elapsed, durationSeconds, remainingMs);
        } else if (cachedResults != null) {
            return completedStatusResponse(true, previousResults != null, cachedResults.sampleCount());
        } else {
            return idleStatusResponse();
        }
    }

    private Response doQuery(Map<String, Object> options) {
        if (cachedResults != null) {
            return snapshotResponse("completed", applyFilters(cachedResults, options));
        }
        if (activeRecording != null) {
            return doStatus();
        }
        return idleQueryResponse("No results available. Start a recording first.");
    }

    private Response doCompare(Map<String, Object> options) {
        if (previousResults == null || cachedResults == null) {
            return errorResponse("Need two recordings to compare. Run two recordings first.");
        }

        long minSize = optionLong(options, MIN_SIZE, 0);

        long baselineDurationMs = previousResults.recordingDurationMs();
        long currentDurationMs = cachedResults.recordingDurationMs();
        double durationRatio = baselineDurationMs > 0 ? (double) currentDurationMs / baselineDurationMs : 1.0;

        // index baseline samples by group key
        Map<String, SampleEntry> baselineMap = new LinkedHashMap<>();
        for (SampleEntry s : previousResults.samples()) {
            baselineMap.put(sampleGroupKey(s.allocationClass(), s.stackTrace()), s);
        }

        // index current samples by group key
        Map<String, SampleEntry> currentMap = new LinkedHashMap<>();
        for (SampleEntry s : cachedResults.samples()) {
            currentMap.put(sampleGroupKey(s.allocationClass(), s.stackTrace()), s);
        }

        // collect all keys preserving order (current first, then baseline-only)
        Map<String, SampleEntry> allKeys = new LinkedHashMap<>(currentMap);
        for (String key : baselineMap.keySet()) {
            allKeys.putIfAbsent(key, baselineMap.get(key));
        }

        List<ComparisonEntry> comparisons = new ArrayList<>();
        for (String key : allKeys.keySet()) {
            SampleEntry baseline = baselineMap.get(key);
            SampleEntry current = currentMap.get(key);

            String className = current != null ? current.allocationClass() : baseline.allocationClass();
            if (className == null) {
                className = "unknown";
            }

            long baseSize = baseline != null ? baseline.sampledSize() : 0;
            int baseCount = baseline != null ? baseline.count() : 0;
            long curSize = current != null ? current.sampledSize() : 0;
            int curCount = current != null ? current.count() : 0;

            if (minSize > 0 && baseSize < minSize && curSize < minSize) {
                continue;
            }

            String trend;
            double growthRatio = 0;
            if (baseline == null) {
                trend = "new";
            } else if (current == null) {
                trend = "gone";
            } else if (baseSize == 0) {
                trend = curSize > 0 ? "new" : "stable";
            } else {
                // compare raw sizes without duration normalization: JFR samples
                // allocation events, so stable objects produce similar counts
                // regardless of recording length — only leaks accumulate more
                growthRatio = (double) curSize / baseSize;
                if (growthRatio >= 1.5) {
                    trend = "growing";
                } else if (growthRatio >= 1.3) {
                    trend = "suspicious";
                } else if (growthRatio < 0.7) {
                    trend = "shrinking";
                } else {
                    trend = "stable";
                }
            }
            growthRatio = Math.round(growthRatio * 100.0) / 100.0;

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

            // carry forward reference chain and stack trace from current (or baseline if gone)
            SampleEntry source = current != null ? current : baseline;

            comparisons.add(new ComparisonEntry(
                    className, baseSize, baseCount, curSize, curCount, growthRatio, trend, lowConfidence,
                    source.referenceChain(), source.stackTrace()));
        }

        // sort by growth ratio descending (leaks first)
        comparisons.sort(Comparator.comparingDouble(ComparisonEntry::growthRatio).reversed());

        RecordingInfo baselineInfo = new RecordingInfo(
                baselineDurationMs, previousResults.sampleCount(), previousResults.gcCount());
        RecordingInfo currentInfo = new RecordingInfo(
                currentDurationMs, cachedResults.sampleCount(), cachedResults.gcCount());

        return compareResponse(baselineInfo, currentInfo, Math.round(durationRatio * 100.0) / 100.0, comparisons);
    }

    private RecordingSnapshot applyFilters(RecordingSnapshot original, Map<String, Object> options) {
        long minSize = optionLong(options, MIN_SIZE, 0);
        boolean includeStacktrace = optionBoolean(options, STACKTRACE, false);

        if (minSize <= 0 && includeStacktrace) {
            return original;
        }

        // build a filtered copy so the cached results remain unmodified
        List<SampleEntry> filtered = new ArrayList<>();
        for (SampleEntry sample : original.samples()) {
            if (minSize > 0 && sample.sampledSize() < minSize) {
                continue;
            }
            filtered.add(includeStacktrace ? sample : withoutStackTrace(sample));
        }
        return new RecordingSnapshot(
                filtered, filtered.size(), original.rawSampleCount(), original.gcCount(),
                original.recordingDurationMs(), original.recordingEndTime());
    }

    private static SampleEntry withoutStackTrace(SampleEntry sample) {
        return new SampleEntry(
                sample.allocationClass(), sample.allocationSize(), sample.lastKnownHeapUsage(),
                sample.arrayElements(), sample.objectAge(), sample.allocationTime(), null, sample.referenceChain(),
                sample.count(), sample.sampledSize());
    }

    private ParsedSamples parseRecordingFile(Path file, int limit) throws IOException {
        // parse all raw samples and count GC events
        List<RawSample> rawSamples = new ArrayList<>();
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
                RawSample sample = parseOldObjectSampleEvent(event);
                if (sample != null) {
                    rawSamples.add(sample);
                }
            }
        }

        // aggregate by class + stack trace fingerprint
        Map<String, SampleAccumulator> groups = new LinkedHashMap<>();
        for (RawSample sample : rawSamples) {
            String key = sampleGroupKey(sample.allocationClass(), sample.stackTrace());
            SampleAccumulator existing = groups.get(key);
            if (existing == null) {
                groups.put(key, new SampleAccumulator(sample));
            } else {
                existing.merge(sample);
            }
        }

        List<SampleEntry> samples = new ArrayList<>();
        int count = 0;
        for (SampleAccumulator group : groups.values()) {
            if (limit > 0 && count >= limit) {
                break;
            }
            samples.add(group.toEntry());
            count++;
        }

        return new ParsedSamples(samples, count, rawSamples.size(), gcCount);
    }

    private static String sampleGroupKey(String allocationClass, List<StackFrameEntry> stackTrace) {
        StringBuilder sb = new StringBuilder();
        sb.append(allocationClass != null ? allocationClass : "");
        // find the first user-code frame (skip JDK internals and Camel framework frames)
        // this gives stable keys across JFR runs since user code frames don't shift
        if (stackTrace != null) {
            for (StackFrameEntry frame : stackTrace) {
                String method = frame.method() != null ? frame.method() : "";
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

    private RawSample parseOldObjectSampleEvent(RecordedEvent event) {
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
        String allocationClass = null;
        if (objectRef != null && objectRef.hasField("type")) {
            try {
                RecordedClass type = objectRef.getClass("type");
                if (type != null) {
                    allocationClass = StringHelper.readableClassName(type.getName());
                }
            } catch (Exception e) {
                // ignore
            }
        }
        if (allocationClass == null && event.hasField("objectClass")) {
            try {
                RecordedClass objectClass = event.getClass("objectClass");
                if (objectClass != null) {
                    allocationClass = StringHelper.readableClassName(objectClass.getName());
                }
            } catch (Exception e) {
                // ignore
            }
        }

        // allocation size — try multiple field names across JDK versions
        Long allocationSize = null;
        if (event.hasField("allocationSize")) {
            allocationSize = event.getLong("allocationSize");
        } else if (event.hasField("objectSize")) {
            allocationSize = event.getLong("objectSize");
        }

        // last known heap usage
        Long lastKnownHeapUsage = null;
        if (event.hasField("lastKnownHeapUsage")) {
            lastKnownHeapUsage = event.getLong("lastKnownHeapUsage");
        }

        // array elements
        Integer arrayElements = null;
        if (event.hasField("arrayElements")) {
            int n = event.getInt("arrayElements");
            if (n > 0) {
                arrayElements = n;
            }
        }

        // object age
        Long objectAge = null;
        if (event.hasField("objectAge")) {
            try {
                objectAge = event.getDuration("objectAge").toMillis();
            } catch (Exception e) {
                // some JDK versions may not support this field as Duration
            }
        }

        // allocation time
        long allocationTime = event.getStartTime().toEpochMilli();

        // stack trace (where the object was allocated)
        List<StackFrameEntry> stackTrace = null;
        RecordedStackTrace recordedStackTrace = event.getStackTrace();
        if (recordedStackTrace != null) {
            List<StackFrameEntry> frames = new ArrayList<>();
            for (RecordedFrame frame : recordedStackTrace.getFrames()) {
                frames.add(new StackFrameEntry(
                        frame.getMethod().getType().getName() + "." + frame.getMethod().getName(),
                        frame.getLineNumber()));
                if (frames.size() >= MAX_STACK_FRAMES) {
                    break;
                }
            }
            stackTrace = frames;
        }

        // reference chain (path from object to GC root)
        List<ReferenceLink> referenceChain = null;
        if (objectRef != null) {
            List<ReferenceLink> chain = extractReferenceChain(objectRef);
            appendGcRoot(event, chain);
            if (!chain.isEmpty()) {
                referenceChain = chain;
            }
        }

        return new RawSample(
                allocationClass, allocationSize, lastKnownHeapUsage, arrayElements, objectAge, allocationTime,
                stackTrace, referenceChain);
    }

    private List<ReferenceLink> extractReferenceChain(RecordedObject objectRef) {
        List<ReferenceLink> chain = new ArrayList<>();
        try {
            RecordedObject obj = objectRef;
            int depth = 0;
            while (obj != null && depth < MAX_CHAIN_DEPTH) {
                String type = null;
                if (obj.hasField("type")) {
                    try {
                        RecordedClass recordedClass = obj.getClass("type");
                        if (recordedClass != null) {
                            type = StringHelper.readableClassName(recordedClass.getName());
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }

                String field = null;
                if (obj.hasField("field")) {
                    try {
                        RecordedObject fieldObj = obj.getValue("field");
                        if (fieldObj != null && fieldObj.hasField("name")) {
                            field = fieldObj.getString("name");
                        }
                    } catch (Exception e) {
                        try {
                            field = obj.getString("field");
                        } catch (Exception ex) {
                            // ignore
                        }
                    }
                }

                String description = null;
                if (obj.hasField("description")) {
                    try {
                        String desc = obj.getString("description");
                        if (desc != null && !desc.isEmpty()) {
                            description = desc;
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }

                if (type != null || field != null || description != null) {
                    chain.add(new ReferenceLink(type, field, description));
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

    private void appendGcRoot(RecordedEvent event, List<ReferenceLink> chain) {
        if (!event.hasField("root")) {
            return;
        }
        try {
            RecordedObject root = event.getValue("root");
            if (root == null) {
                return;
            }
            String type = null;
            if (root.hasField("type")) {
                RecordedClass recordedClass = root.getClass("type");
                if (recordedClass != null) {
                    type = StringHelper.readableClassName(recordedClass.getName());
                }
            }
            String description = null;
            if (root.hasField("description")) {
                String desc = root.getString("description");
                if (desc != null && !desc.isEmpty()) {
                    description = desc;
                }
            }
            if (root.hasField("system")) {
                try {
                    String system = root.getString("system");
                    if (system != null && !system.isEmpty()) {
                        description = (description != null ? description : "") + " [GC Root: " + system + "]";
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
            if (type != null || description != null) {
                chain.add(new ReferenceLink(type, null, description));
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

    private static Response errorResponse(String message) {
        return new Response(
                "error", message, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    private static Response idleStatusResponse() {
        return new Response(
                "idle", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }

    private static Response idleQueryResponse(String note) {
        return new Response(
                "idle", null, note, null, null, null, null, null, null, 0, null, null, null, null, List.of(), null,
                null, null, null);
    }

    private static Response recordingStartedResponse(long startTime, Integer durationSeconds) {
        return new Response(
                "recording", null, null, startTime, durationSeconds, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    private static Response recordingStatusResponse(
            long startTime, long elapsedMs, Integer durationSeconds, Long remainingMs) {
        return new Response(
                "recording", null, null, startTime, durationSeconds, elapsedMs, remainingMs, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }

    private static Response completedStatusResponse(
            boolean hasCachedResults, boolean hasComparisonData, int sampleCount) {
        return new Response(
                "completed", null, null, null, null, null, null, hasCachedResults, hasComparisonData, sampleCount,
                null, null, null, null, null, null, null, null, null);
    }

    private static Response snapshotResponse(String status, RecordingSnapshot snapshot) {
        return new Response(
                status, null, null, null, null, null, null, null, null, snapshot.sampleCount(),
                snapshot.recordingDurationMs(), snapshot.recordingEndTime(), snapshot.rawSampleCount(),
                snapshot.gcCount(), snapshot.samples(), null, null, null, null);
    }

    private static Response compareResponse(
            RecordingInfo baseline, RecordingInfo current, double durationRatio, List<ComparisonEntry> comparisons) {
        return new Response(
                "compared", null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                baseline, current, durationRatio, comparisons);
    }
}
