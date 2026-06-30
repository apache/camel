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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collects and maintains sparkline/chart history data for all metric families. Extracted from CamelMonitor to
 * consolidate the 30+ sliding-window maps and their update/cleanup/reset logic.
 */
class MetricsCollector {

    static final int MAX_SPARKLINE_POINTS = 300;
    static final int MAX_ENDPOINT_CHART_POINTS = 300;
    static final int MAX_HEAP_HISTORY_POINTS = 120;
    static final long HEAP_SAMPLE_INTERVAL_MS = 5000;

    // Throughput history per PID (one point per second)
    private final Map<String, LinkedList<Long>> throughputHistory = new ConcurrentHashMap<>();
    private final Map<String, LinkedList<Long>> failedHistory = new ConcurrentHashMap<>();
    private final Map<String, LinkedList<long[]>> throughputSamples = new ConcurrentHashMap<>();
    private final Map<String, Long> previousExchangesTime = new ConcurrentHashMap<>();

    // Endpoint in/out sliding window history per PID — all endpoints
    private final Map<String, LinkedList<Long>> endpointInHistory = new ConcurrentHashMap<>();
    private final Map<String, LinkedList<Long>> endpointOutHistory = new ConcurrentHashMap<>();
    private final Map<String, LinkedList<long[]>> endpointSamples = new ConcurrentHashMap<>();
    private final Map<String, Long> previousEndpointTime = new ConcurrentHashMap<>();

    // Endpoint in/out sliding window history per PID — remote endpoints only
    private final Map<String, LinkedList<Long>> endpointRemoteInHistory = new ConcurrentHashMap<>();
    private final Map<String, LinkedList<Long>> endpointRemoteOutHistory = new ConcurrentHashMap<>();
    private final Map<String, LinkedList<long[]>> endpointRemoteSamples = new ConcurrentHashMap<>();
    private final Map<String, Long> previousEndpointRemoteTime = new ConcurrentHashMap<>();

    // Endpoint in/out sliding window history per PID — remote+stub endpoints
    private final Map<String, LinkedList<Long>> endpointRemoteStubInHistory = new ConcurrentHashMap<>();
    private final Map<String, LinkedList<Long>> endpointRemoteStubOutHistory = new ConcurrentHashMap<>();
    private final Map<String, LinkedList<long[]>> endpointRemoteStubSamples = new ConcurrentHashMap<>();
    private final Map<String, Long> previousEndpointRemoteStubTime = new ConcurrentHashMap<>();

    // Endpoint payload size (mean body size) history per PID
    private final Map<String, LinkedList<Long>> endpointInSizeHistory = new ConcurrentHashMap<>();
    private final Map<String, LinkedList<Long>> endpointOutSizeHistory = new ConcurrentHashMap<>();
    private final Map<String, Long> previousEndpointSizeTime = new ConcurrentHashMap<>();

    // Per-endpoint in/out rate history — keyed by pid + "|" + uri
    private final Map<String, LinkedList<Long>> perEndpointInHistory = new ConcurrentHashMap<>();
    private final Map<String, LinkedList<Long>> perEndpointOutHistory = new ConcurrentHashMap<>();
    private final Map<String, LinkedList<long[]>> perEndpointSamples = new ConcurrentHashMap<>();
    private final Map<String, Long> previousPerEndpointTime = new ConcurrentHashMap<>();

    // Circuit breaker throughput history per PID/cbId
    private final Map<String, LinkedList<Long>> cbSuccessHistory = new ConcurrentHashMap<>();
    private final Map<String, LinkedList<Long>> cbFailHistory = new ConcurrentHashMap<>();
    private final Map<String, LinkedList<long[]>> cbThroughputSamples = new ConcurrentHashMap<>();
    private final Map<String, Long> previousCbTime = new ConcurrentHashMap<>();

    // Heap memory usage history per PID (one point per 5 seconds, in bytes)
    private final Map<String, LinkedList<Long>> heapMemHistory = new ConcurrentHashMap<>();
    private final Map<String, Long> previousHeapTime = new ConcurrentHashMap<>();

    // Load averages (EWMA) — CPU%, per PID
    private final Map<String, LoadAvg> cpuLoadAvg = new ConcurrentHashMap<>();
    private final Map<String, long[]> prevCpuSample = new ConcurrentHashMap<>();

    // --- Getters for tab read access ---

    Map<String, LinkedList<Long>> getThroughputHistory() {
        return throughputHistory;
    }

    Map<String, LinkedList<Long>> getFailedHistory() {
        return failedHistory;
    }

    Map<String, LoadAvg> getCpuLoadAvg() {
        return cpuLoadAvg;
    }

    Map<String, LinkedList<Long>> getEndpointInHistory() {
        return endpointInHistory;
    }

    Map<String, LinkedList<Long>> getEndpointOutHistory() {
        return endpointOutHistory;
    }

    Map<String, LinkedList<Long>> getEndpointRemoteInHistory() {
        return endpointRemoteInHistory;
    }

    Map<String, LinkedList<Long>> getEndpointRemoteOutHistory() {
        return endpointRemoteOutHistory;
    }

    Map<String, LinkedList<Long>> getEndpointRemoteStubInHistory() {
        return endpointRemoteStubInHistory;
    }

    Map<String, LinkedList<Long>> getEndpointRemoteStubOutHistory() {
        return endpointRemoteStubOutHistory;
    }

    Map<String, LinkedList<Long>> getEndpointInSizeHistory() {
        return endpointInSizeHistory;
    }

    Map<String, LinkedList<Long>> getEndpointOutSizeHistory() {
        return endpointOutSizeHistory;
    }

    Map<String, LinkedList<Long>> getPerEndpointInHistory() {
        return perEndpointInHistory;
    }

    Map<String, LinkedList<Long>> getPerEndpointOutHistory() {
        return perEndpointOutHistory;
    }

    Map<String, LinkedList<Long>> getCbSuccessHistory() {
        return cbSuccessHistory;
    }

    Map<String, LinkedList<Long>> getCbFailHistory() {
        return cbFailHistory;
    }

    Map<String, LinkedList<Long>> getHeapMemHistory() {
        return heapMemHistory;
    }

    // --- Update methods ---

    void updateThroughputHistory(IntegrationInfo info) {
        long currentTotal = info.exchangesTotal;
        long currentFailed = info.failed;
        long now = System.currentTimeMillis();

        String pid = info.pid;
        LinkedList<long[]> samples = throughputSamples.computeIfAbsent(pid, k -> new LinkedList<>());
        samples.add(new long[] { now, currentTotal, currentFailed });

        while (!samples.isEmpty() && now - samples.get(0)[0] > 2000) {
            samples.remove(0);
        }

        if (samples.size() >= 2) {
            long[] oldest = samples.get(0);
            long[] newest = samples.get(samples.size() - 1);
            long deltaTotal = newest[1] - oldest[1];
            long deltaFailed = newest[2] - oldest[2];
            long deltaTimeMs = newest[0] - oldest[0];
            long tp = deltaTimeMs > 0 ? (deltaTotal * 1000) / deltaTimeMs : 0;
            long fp = deltaTimeMs > 0 ? (deltaFailed * 1000) / deltaTimeMs : 0;

            Long lastTime = previousExchangesTime.get(pid);
            if (lastTime == null || now - lastTime >= 1000) {
                previousExchangesTime.put(pid, now);
                LinkedList<Long> hist = throughputHistory.computeIfAbsent(pid, k -> new LinkedList<>());
                hist.add(tp);
                while (hist.size() > MAX_SPARKLINE_POINTS) {
                    hist.remove(0);
                }
                LinkedList<Long> fhist = failedHistory.computeIfAbsent(pid, k -> new LinkedList<>());
                fhist.add(fp);
                while (fhist.size() > MAX_SPARKLINE_POINTS) {
                    fhist.remove(0);
                }
            }
        }
    }

    void updateEndpointHistory(IntegrationInfo info) {
        long inTotal = info.endpoints.stream()
                .filter(ep -> "in".equals(ep.direction))
                .mapToLong(ep -> ep.hits).sum();
        long outTotal = info.endpoints.stream()
                .filter(ep -> "out".equals(ep.direction))
                .mapToLong(ep -> ep.hits).sum();
        long inRemote = info.endpoints.stream()
                .filter(ep -> "in".equals(ep.direction) && ep.remote)
                .mapToLong(ep -> ep.hits).sum();
        long outRemote = info.endpoints.stream()
                .filter(ep -> "out".equals(ep.direction) && ep.remote)
                .mapToLong(ep -> ep.hits).sum();
        long inRemoteStub = info.endpoints.stream()
                .filter(ep -> "in".equals(ep.direction) && (ep.remote || ep.stub))
                .mapToLong(ep -> ep.hits).sum();
        long outRemoteStub = info.endpoints.stream()
                .filter(ep -> "out".equals(ep.direction) && (ep.remote || ep.stub))
                .mapToLong(ep -> ep.hits).sum();

        long now = System.currentTimeMillis();
        String pid = info.pid;

        recordEndpointSample(pid, now, inTotal, outTotal,
                endpointSamples, previousEndpointTime, endpointInHistory, endpointOutHistory);
        recordEndpointSample(pid, now, inRemote, outRemote,
                endpointRemoteSamples, previousEndpointRemoteTime, endpointRemoteInHistory, endpointRemoteOutHistory);
        recordEndpointSample(pid, now, inRemoteStub, outRemoteStub,
                endpointRemoteStubSamples, previousEndpointRemoteStubTime,
                endpointRemoteStubInHistory, endpointRemoteStubOutHistory);

        // Record payload size snapshots (mean body size per direction)
        long inMeanSize = info.endpoints.stream()
                .filter(ep -> "in".equals(ep.direction) && ep.meanBodySize >= 0)
                .mapToLong(ep -> ep.meanBodySize).max().orElse(0);
        long outMeanSize = info.endpoints.stream()
                .filter(ep -> "out".equals(ep.direction) && ep.meanBodySize >= 0)
                .mapToLong(ep -> ep.meanBodySize).max().orElse(0);
        Long lastSizeTime = previousEndpointSizeTime.get(pid);
        if (lastSizeTime == null || now - lastSizeTime >= 1000) {
            previousEndpointSizeTime.put(pid, now);
            LinkedList<Long> inSizeHist = endpointInSizeHistory.computeIfAbsent(pid, k -> new LinkedList<>());
            inSizeHist.add(inMeanSize);
            while (inSizeHist.size() > MAX_ENDPOINT_CHART_POINTS) {
                inSizeHist.remove(0);
            }
            LinkedList<Long> outSizeHist = endpointOutSizeHistory.computeIfAbsent(pid, k -> new LinkedList<>());
            outSizeHist.add(outMeanSize);
            while (outSizeHist.size() > MAX_ENDPOINT_CHART_POINTS) {
                outSizeHist.remove(0);
            }
        }

        // Per-endpoint rate history (keyed by pid|uri)
        Map<String, long[]> perUri = new LinkedHashMap<>();
        for (EndpointInfo ep : info.endpoints) {
            if (ep.uri == null) {
                continue;
            }
            long[] inOut = perUri.computeIfAbsent(ep.uri, k -> new long[2]);
            if ("in".equals(ep.direction)) {
                inOut[0] += ep.hits;
            } else if ("out".equals(ep.direction)) {
                inOut[1] += ep.hits;
            }
        }
        for (Map.Entry<String, long[]> entry : perUri.entrySet()) {
            String key = pid + "|" + entry.getKey();
            long[] inOut = entry.getValue();
            recordEndpointSample(key, now, inOut[0], inOut[1],
                    perEndpointSamples, previousPerEndpointTime,
                    perEndpointInHistory, perEndpointOutHistory);
        }
    }

    private void recordEndpointSample(
            String pid, long now, long inTotal, long outTotal,
            Map<String, LinkedList<long[]>> samplesMap, Map<String, Long> prevTimeMap,
            Map<String, LinkedList<Long>> inHistMap, Map<String, LinkedList<Long>> outHistMap) {
        LinkedList<long[]> samples = samplesMap.computeIfAbsent(pid, k -> new LinkedList<>());
        samples.add(new long[] { now, inTotal, outTotal });
        while (!samples.isEmpty() && now - samples.get(0)[0] > 2000) {
            samples.remove(0);
        }
        if (samples.size() >= 2) {
            long[] oldest = samples.get(0);
            long[] newest = samples.get(samples.size() - 1);
            long deltaMs = newest[0] - oldest[0];
            long inRate = deltaMs > 0 ? (newest[1] - oldest[1]) * 1000 / deltaMs : 0;
            long outRate = deltaMs > 0 ? (newest[2] - oldest[2]) * 1000 / deltaMs : 0;
            Long lastTime = prevTimeMap.get(pid);
            if (lastTime == null || now - lastTime >= 1000) {
                prevTimeMap.put(pid, now);
                LinkedList<Long> inHist = inHistMap.computeIfAbsent(pid, k -> new LinkedList<>());
                inHist.add(Math.max(0, inRate));
                while (inHist.size() > MAX_ENDPOINT_CHART_POINTS) {
                    inHist.remove(0);
                }
                LinkedList<Long> outHist = outHistMap.computeIfAbsent(pid, k -> new LinkedList<>());
                outHist.add(Math.max(0, outRate));
                while (outHist.size() > MAX_ENDPOINT_CHART_POINTS) {
                    outHist.remove(0);
                }
            }
        }
    }

    void updateCbHistory(IntegrationInfo info) {
        long now = System.currentTimeMillis();
        for (CircuitBreakerInfo cb : info.circuitBreakers) {
            if (cb.id == null) {
                continue;
            }
            String key = info.pid + "/" + cb.id;
            long success = cb.successfulCalls;
            long failed = cb.failedCalls;
            recordEndpointSample(key, now, success, failed,
                    cbThroughputSamples, previousCbTime, cbSuccessHistory, cbFailHistory);
        }
    }

    void updateHeapHistory(IntegrationInfo info) {
        if (info.heapMemUsed > 0) {
            long now = System.currentTimeMillis();
            Long lastTime = previousHeapTime.get(info.pid);
            if (lastTime == null || now - lastTime >= HEAP_SAMPLE_INTERVAL_MS) {
                previousHeapTime.put(info.pid, now);
                LinkedList<Long> hist = heapMemHistory.computeIfAbsent(info.pid, k -> new LinkedList<>());
                hist.add(info.heapMemUsed);
                while (hist.size() > MAX_HEAP_HISTORY_POINTS) {
                    hist.remove(0);
                }
            }
        }
    }

    void updateLoadMetrics(ProcessHandle ph, IntegrationInfo info) {
        String pid = info.pid;

        Optional<Duration> durOpt = ph.info().totalCpuDuration();
        if (durOpt.isPresent()) {
            long cpuNanos = durOpt.get().toNanos();
            long wallMs = System.currentTimeMillis();
            long[] prev = prevCpuSample.get(pid);
            if (prev != null) {
                long deltaCpuNanos = cpuNanos - prev[0];
                long deltaWallNanos = (wallMs - prev[1]) * 1_000_000L;
                if (deltaWallNanos > 0) {
                    double cpuPct = (double) deltaCpuNanos / deltaWallNanos * 100.0;
                    cpuLoadAvg.computeIfAbsent(pid, k -> new LoadAvg()).update(Math.max(0, cpuPct));
                }
            }
            prevCpuSample.put(pid, new long[] { cpuNanos, wallMs });
        }
    }

    // --- Cleanup methods ---

    void resetStats(String pid) {
        throughputHistory.remove(pid);
        failedHistory.remove(pid);
        throughputSamples.remove(pid);
        previousExchangesTime.remove(pid);

        endpointInHistory.remove(pid);
        endpointOutHistory.remove(pid);
        endpointSamples.remove(pid);
        previousEndpointTime.remove(pid);
        endpointRemoteInHistory.remove(pid);
        endpointRemoteOutHistory.remove(pid);
        endpointRemoteSamples.remove(pid);
        previousEndpointRemoteTime.remove(pid);
        endpointRemoteStubInHistory.remove(pid);
        endpointRemoteStubOutHistory.remove(pid);
        endpointRemoteStubSamples.remove(pid);
        previousEndpointRemoteStubTime.remove(pid);
        endpointInSizeHistory.remove(pid);
        endpointOutSizeHistory.remove(pid);
        previousEndpointSizeTime.remove(pid);

        removeByPrefix(pid + "|", perEndpointInHistory, perEndpointOutHistory,
                perEndpointSamples, previousPerEndpointTime);

        heapMemHistory.remove(pid);
        previousHeapTime.remove(pid);
    }

    void removeVanished(String pid) {
        throughputHistory.remove(pid);
        failedHistory.remove(pid);

        endpointInHistory.remove(pid);
        endpointOutHistory.remove(pid);
        endpointSamples.remove(pid);
        previousEndpointTime.remove(pid);
        endpointRemoteInHistory.remove(pid);
        endpointRemoteOutHistory.remove(pid);
        endpointRemoteSamples.remove(pid);
        previousEndpointRemoteTime.remove(pid);
        endpointRemoteStubInHistory.remove(pid);
        endpointRemoteStubOutHistory.remove(pid);
        endpointRemoteStubSamples.remove(pid);

        endpointInSizeHistory.remove(pid);
        endpointOutSizeHistory.remove(pid);
        previousEndpointSizeTime.remove(pid);
        previousEndpointRemoteStubTime.remove(pid);

        heapMemHistory.remove(pid);
        previousHeapTime.remove(pid);
        cpuLoadAvg.remove(pid);
        prevCpuSample.remove(pid);

        removeByPrefix(pid + "/", cbSuccessHistory, cbFailHistory,
                cbThroughputSamples, previousCbTime);
        removeByPrefix(pid + "|", perEndpointInHistory, perEndpointOutHistory,
                perEndpointSamples, previousPerEndpointTime);
    }

    @SafeVarargs
    private void removeByPrefix(String prefix, Map<String, ?>... maps) {
        for (Map<String, ?> map : maps) {
            map.keySet().removeIf(k -> k.startsWith(prefix));
        }
    }
}
