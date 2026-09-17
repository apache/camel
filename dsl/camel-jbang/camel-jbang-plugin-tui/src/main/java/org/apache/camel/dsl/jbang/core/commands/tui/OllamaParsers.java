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

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.GpuStats;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.LoadedModel;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.ModelShape;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.RunnerInfo;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.SlotState;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * Pure parsers for what the Ollama tab reads: the Ollama REST API ({@code /api/ps}, {@code /api/show}), the
 * llama-server runner ({@code /slots}, its command line) and the host probes ({@code ioreg} on macOS,
 * {@code nvidia-smi} on Linux, {@code ps}). Kept free of I/O so each format has a unit test against a captured sample.
 */
final class OllamaParsers {

    private static final Pattern IOREG_UTIL = Pattern.compile("\"Device Utilization %\"\\s*=\\s*(\\d+)");
    private static final Pattern IOREG_MEM_IN_USE = Pattern.compile("\"In use system memory\"\\s*=\\s*(\\d+)");
    private static final Pattern IOREG_MEM_ALLOC = Pattern.compile("\"Alloc system memory\"\\s*=\\s*(\\d+)");

    private OllamaParsers() {
    }

    // ---- Ollama REST API ----

    /** Models currently loaded, from {@code GET /api/ps}. */
    static List<LoadedModel> parsePs(JsonObject json) {
        List<LoadedModel> result = new ArrayList<>();
        if (json == null) {
            return result;
        }
        Collection<?> models = json.getCollection("models");
        if (models == null) {
            return result;
        }
        for (Object o : models) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            String name = str(m, "name");
            if (name == null) {
                name = str(m, "model");
            }
            if (name == null) {
                continue;
            }
            String family = null;
            String parameterSize = null;
            String quantization = null;
            if (m.get("details") instanceof Map<?, ?> details) {
                family = str(details, "family");
                parameterSize = str(details, "parameter_size");
                quantization = str(details, "quantization_level");
            }
            result.add(new LoadedModel(
                    name, family, parameterSize, quantization,
                    num(m, "size"), num(m, "size_vram"), num(m, "context_length"),
                    parseInstant(str(m, "expires_at")), null));
        }
        return result;
    }

    /** Architecture facts of one model, from {@code POST /api/show}. */
    static ModelShape parseShow(JsonObject json) {
        if (json == null) {
            return null;
        }
        String architecture = null;
        long layers = 0;
        long experts = 0;
        long expertsUsed = 0;
        long maxContext = 0;
        long embedding = 0;
        long parameters = 0;
        if (json.get("model_info") instanceof Map<?, ?> info) {
            architecture = str(info, "general.architecture");
            parameters = num(info, "general.parameter_count");
            for (Map.Entry<?, ?> e : info.entrySet()) {
                String key = String.valueOf(e.getKey());
                if (key.endsWith(".block_count")) {
                    layers = toLong(e.getValue());
                } else if (key.endsWith(".expert_count")) {
                    experts = toLong(e.getValue());
                } else if (key.endsWith(".expert_used_count")) {
                    expertsUsed = toLong(e.getValue());
                } else if (key.endsWith(".context_length")) {
                    maxContext = toLong(e.getValue());
                } else if (key.endsWith(".embedding_length")) {
                    embedding = toLong(e.getValue());
                }
            }
        }
        if (architecture == null && json.get("details") instanceof Map<?, ?> details) {
            architecture = str(details, "family");
        }
        List<String> capabilities = new ArrayList<>();
        Collection<?> caps = json.getCollection("capabilities");
        if (caps != null) {
            for (Object c : caps) {
                capabilities.add(String.valueOf(c));
            }
        }
        return new ModelShape(
                architecture, (int) layers, (int) experts, (int) expertsUsed, maxContext,
                (int) embedding, parameters, List.copyOf(capabilities));
    }

    // ---- llama-server runner ----

    /**
     * Live slot state from the runner's {@code GET /slots}. Several slots (parallel requests) are folded into one:
     * processing when any slot is, token counts summed, the context size of the largest slot.
     */
    static SlotState parseSlots(JsonArray slots, Instant now) {
        if (slots == null || slots.isEmpty()) {
            return null;
        }
        boolean processing = false;
        long promptTokens = 0;
        long promptProcessed = 0;
        long cacheTokens = 0;
        long decoded = 0;
        long contextSize = 0;
        String speculative = null;
        int slotCount = 0;
        for (Object o : slots) {
            if (!(o instanceof Map<?, ?> slot)) {
                continue;
            }
            slotCount++;
            processing |= Boolean.TRUE.equals(slot.get("is_processing"));
            promptTokens += num(slot, "n_prompt_tokens");
            promptProcessed += num(slot, "n_prompt_tokens_processed");
            cacheTokens += num(slot, "n_prompt_tokens_cache");
            contextSize = Math.max(contextSize, num(slot, "n_ctx"));
            Object next = slot.get("next_token");
            if (next instanceof Collection<?> c && !c.isEmpty() && c.iterator().next() instanceof Map<?, ?> nt) {
                decoded += num(nt, "n_decoded");
            } else if (next instanceof Map<?, ?> nt) {
                decoded += num(nt, "n_decoded");
            }
            if (speculative == null && slot.get("params") instanceof Map<?, ?> params) {
                String types = str(params, "speculative.types");
                if (types != null && !types.isBlank() && !"none".equals(types)) {
                    // "none,draft-mtp" lists the fallback first; the active method is the last entry
                    String[] parts = types.split(",");
                    speculative = parts[parts.length - 1].trim();
                }
            }
        }
        return new SlotState(
                processing, promptTokens, promptProcessed, cacheTokens, decoded, contextSize, speculative,
                slotCount, now);
    }

    /** True when the command line is an Ollama model runner (upstream llama-server or the older ollama runner). */
    static boolean isRunnerCommandLine(String commandLine) {
        if (commandLine == null) {
            return false;
        }
        String lower = commandLine.toLowerCase(Locale.ROOT);
        if (!lower.contains("--port")) {
            return false;
        }
        return lower.contains("llama-server") || (lower.contains("ollama") && lower.contains(" runner"));
    }

    /**
     * Port, context size, parallel slots and model path from a runner command line such as
     * {@code llama-server --model /blobs/sha256-... --port 58237 --host 127.0.0.1 -c 262144 -np 1}.
     */
    static RunnerInfo parseRunnerCommandLine(long pid, String commandLine) {
        if (!isRunnerCommandLine(commandLine)) {
            return null;
        }
        String[] tokens = commandLine.trim().split("\\s+");
        int port = 0;
        long ctx = 0;
        int parallel = 0;
        String modelPath = null;
        for (int i = 0; i < tokens.length - 1; i++) {
            String t = tokens[i];
            String v = tokens[i + 1];
            switch (t) {
                case "--port" -> port = parseIntOrZero(v);
                case "-c", "--ctx-size" -> ctx = parseLongOrZero(v);
                case "-np", "--parallel" -> parallel = parseIntOrZero(v);
                case "--model", "-m" -> modelPath = v;
                default -> {
                }
            }
        }
        if (port <= 0) {
            return null;
        }
        String executable = tokens[0];
        int slash = Math.max(executable.lastIndexOf('/'), executable.lastIndexOf('\\'));
        if (slash >= 0) {
            executable = executable.substring(slash + 1);
        }
        return new RunnerInfo(pid, port, ctx, parallel, modelPath, executable);
    }

    // ---- host probes ----

    /**
     * GPU load on macOS from {@code ioreg -r -d 1 -c IOAccelerator}. Apple silicon reports a device utilization
     * percentage and the unified memory the GPU has wired; several accelerator entries are folded into the busiest one
     * for utilization and the sum for memory.
     */
    static GpuStats parseIoreg(String output) {
        if (output == null || output.isBlank()) {
            return null;
        }
        int util = -1;
        Matcher m = IOREG_UTIL.matcher(output);
        while (m.find()) {
            util = Math.max(util, parseIntOrZero(m.group(1)));
        }
        long inUse = 0;
        Matcher mem = IOREG_MEM_IN_USE.matcher(output);
        while (mem.find()) {
            inUse += parseLongOrZero(mem.group(1));
        }
        long alloc = 0;
        Matcher am = IOREG_MEM_ALLOC.matcher(output);
        while (am.find()) {
            alloc += parseLongOrZero(am.group(1));
        }
        if (util < 0 && inUse == 0) {
            return null;
        }
        return new GpuStats("Apple GPU", Math.max(0, util), inUse, Math.max(alloc, inUse), 1);
    }

    /**
     * GPU load from {@code nvidia-smi --query-gpu=name,utilization.gpu,memory.used,memory.total
     * --format=csv,noheader,nounits}; memory columns are MiB. Several GPUs are folded into the mean utilization and the
     * summed memory.
     */
    static GpuStats parseNvidiaSmi(String output) {
        if (output == null || output.isBlank()) {
            return null;
        }
        String name = null;
        int count = 0;
        long utilSum = 0;
        long used = 0;
        long total = 0;
        for (String line : output.split("\\R")) {
            String[] cols = line.split(",");
            if (cols.length < 4) {
                continue;
            }
            if (name == null) {
                name = cols[0].trim();
            }
            count++;
            utilSum += parseLongOrZero(cols[1].trim());
            used += parseLongOrZero(cols[2].trim()) * 1024L * 1024L;
            total += parseLongOrZero(cols[3].trim()) * 1024L * 1024L;
        }
        if (count == 0) {
            return null;
        }
        return new GpuStats(name, (int) (utilSum / count), used, total, count);
    }

    /**
     * Accumulated CPU time and resident memory from {@code ps -o cputime=,rss= -p <pid>}, as {@code [cpuMillis,
     * rssBytes]}. The JDK reports no CPU time for other users' processes on macOS, so the tab diffs this between polls.
     */
    static long[] parsePsCpuAndRss(String psOutput) {
        if (psOutput == null) {
            return null;
        }
        String[] cols = psOutput.trim().split("\\s+");
        if (cols.length < 2) {
            return null;
        }
        long cpu = parseCpuTimeMillis(cols[0]);
        long rss = parseLongOrZero(cols[1]) * 1024L;
        if (cpu < 0) {
            return null;
        }
        return new long[] { cpu, rss };
    }

    /** {@code ps} CPU time: {@code mm:ss.cc}, {@code hh:mm:ss} or {@code d-hh:mm:ss}; -1 when unreadable. */
    static long parseCpuTimeMillis(String text) {
        if (text == null || text.isBlank()) {
            return -1;
        }
        String t = text.trim();
        long days = 0;
        int dash = t.indexOf('-');
        if (dash > 0) {
            days = parseLongOrZero(t.substring(0, dash));
            t = t.substring(dash + 1);
        }
        String[] parts = t.split(":");
        double seconds;
        try {
            if (parts.length == 3) {
                seconds = Long.parseLong(parts[0]) * 3600.0 + Long.parseLong(parts[1]) * 60.0
                          + Double.parseDouble(parts[2]);
            } else if (parts.length == 2) {
                seconds = Long.parseLong(parts[0]) * 60.0 + Double.parseDouble(parts[1]);
            } else if (parts.length == 1) {
                seconds = Double.parseDouble(parts[0]);
            } else {
                return -1;
            }
        } catch (NumberFormatException e) {
            return -1;
        }
        return Math.round((days * 86400.0 + seconds) * 1000.0);
    }

    // ---- misc ----

    /** Ollama timestamps carry a zone offset ({@code 2026-09-17T11:22:44.593036+02:00}) or a trailing Z. */
    static Instant parseInstant(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (Exception e) {
            try {
                return Instant.parse(text);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    /** True when the URL points at this machine, which is when the runner and host probes make sense. */
    static boolean isLoopbackUrl(String url) {
        if (url == null) {
            return false;
        }
        try {
            String host = URI.create(url).getHost();
            return host != null && (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1")
                    || host.equals("::1") || host.equals("[::1]") || host.equals("0.0.0.0"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** {@code http://localhost:11434} becomes {@code localhost:11434}. */
    static String displayHost(String url) {
        if (url == null) {
            return "";
        }
        String host = url;
        if (host.startsWith("http://")) {
            host = host.substring("http://".length());
        } else if (host.startsWith("https://")) {
            host = host.substring("https://".length());
        }
        while (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        return host;
    }

    static String str(Map<?, ?> m, String key) {
        Object v = m.get(key);
        if (v == null) {
            return null;
        }
        String s = v.toString();
        return s.isBlank() ? null : s;
    }

    static long num(Map<?, ?> m, String key) {
        return toLong(m.get(key));
    }

    static long toLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v instanceof String s) {
            return parseLongOrZero(s.trim());
        }
        return 0;
    }

    private static int parseIntOrZero(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long parseLongOrZero(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            try {
                return (long) Double.parseDouble(s);
            } catch (NumberFormatException e2) {
                return 0;
            }
        }
    }
}
