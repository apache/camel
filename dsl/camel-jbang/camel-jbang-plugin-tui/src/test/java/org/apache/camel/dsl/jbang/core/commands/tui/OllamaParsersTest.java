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

import java.time.Instant;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.GpuStats;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.LoadedModel;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.ModelShape;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.RunnerInfo;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.SlotState;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The samples below were captured from Ollama 0.33.3 serving qwen3.6:35b-a3b on an Apple M4 Pro, and from the
 * llama-server runner it spawned.
 */
class OllamaParsersTest {

    private static final String PS = """
            {"models":[{"name":"qwen3.6:35b-a3b","model":"qwen3.6:35b-a3b","size":23567972432,
            "digest":"096fdbd02fe6","details":{"parent_model":"","format":"gguf","family":"qwen35moe",
            "families":["qwen35moe"],"parameter_size":"35.5B","quantization_level":"Q4_K_M"},
            "expires_at":"2026-09-17T11:22:44.593036+02:00","size_vram":23567972432,"context_length":262144}]}
            """;

    private static final String SHOW = """
            {"details":{"family":"qwen35moe","parameter_size":"35.5B","quantization_level":"Q4_K_M"},
            "model_info":{"general.architecture":"qwen35moe","general.parameter_count":35505251456,
            "qwen35moe.block_count":41,"qwen35moe.context_length":262144,"qwen35moe.embedding_length":2048,
            "qwen35moe.expert_count":256,"qwen35moe.expert_used_count":8,"qwen35moe.attention.head_count":16},
            "capabilities":["completion","vision","tools","thinking"]}
            """;

    private static final String SLOTS = """
            [{"id":0,"n_ctx":262144,"speculative":true,"is_processing":true,"id_task":3,"n_prompt_tokens":25,
            "n_prompt_tokens_processed":25,"n_prompt_tokens_cache":9,
            "params":{"n_predict":16,"speculative.types":"none,draft-mtp"},
            "next_token":[{"has_next_token":true,"n_remain":10,"n_decoded":6}]}]
            """;

    private static final String RUNNER_CMD = "/opt/homebrew/Cellar/ollama/0.33.3/libexec/lib/ollama/llama-server "
                                             + "--model /Users/me/.ollama/models/blobs/sha256-d372de8e "
                                             + "--port 58237 --host 127.0.0.1 --no-webui --offline -c 262144 -np 1 "
                                             + "--log-verbosity 4 --chat-template chatml";

    private static final String IOREG = """
            +-o AGXAcceleratorG16X  <class AGXAcceleratorG16X, id 0x100000a0b, registered, matched, active>
                {
                  "PerformanceStatistics" = {"Alloc system memory"=34170552320,"Tiler Utilization %"=3,
                  "Renderer Utilization %"=12,"Device Utilization %"=25,"In use system memory"=31463079936}
                }
            """;

    @Test
    void parsesLoadedModelsFromPs() throws Exception {
        List<LoadedModel> models = OllamaParsers.parsePs((JsonObject) Jsoner.deserialize(PS));
        assertEquals(1, models.size());
        LoadedModel m = models.get(0);
        assertEquals("qwen3.6:35b-a3b", m.name());
        assertEquals("qwen35moe", m.family());
        assertEquals("35.5B", m.parameterSize());
        assertEquals("Q4_K_M", m.quantization());
        assertEquals(23567972432L, m.sizeBytes());
        assertEquals(23567972432L, m.sizeVram());
        assertEquals(100, m.gpuPercent());
        assertEquals(262144L, m.contextLength());
        assertEquals(Instant.parse("2026-09-17T09:22:44.593036Z"), m.expiresAt());
        assertNull(m.shape());
    }

    @Test
    void parsesModelShapeFromShow() throws Exception {
        ModelShape shape = OllamaParsers.parseShow((JsonObject) Jsoner.deserialize(SHOW));
        assertNotNull(shape);
        assertEquals("qwen35moe", shape.architecture());
        assertEquals(41, shape.layers());
        assertEquals(256, shape.experts());
        assertEquals(8, shape.expertsUsed());
        assertEquals(262144L, shape.maxContext());
        assertEquals(2048, shape.embeddingLength());
        assertEquals(35505251456L, shape.parameters());
        assertEquals(List.of("completion", "vision", "tools", "thinking"), shape.capabilities());
    }

    @Test
    void parsesRunnerSlots() throws Exception {
        Instant now = Instant.parse("2026-09-17T09:00:00Z");
        SlotState slot = OllamaParsers.parseSlots((JsonArray) Jsoner.deserialize(SLOTS), now);
        assertNotNull(slot);
        assertTrue(slot.processing());
        assertEquals(25, slot.promptTokens());
        assertEquals(25, slot.promptProcessed());
        assertEquals(9, slot.cacheTokens());
        assertEquals(6, slot.decoded());
        assertEquals(262144, slot.contextSize());
        assertEquals("draft-mtp", slot.speculative());
        assertEquals(1, slot.slots());
        assertEquals(31, slot.contextUsed());
        assertEquals(36, slot.cacheHitPercent());
        assertEquals(now, slot.sampledAt());
    }

    @Test
    void emptySlotsMeanNoRunnerState() {
        assertNull(OllamaParsers.parseSlots(new JsonArray(), Instant.now()));
        assertNull(OllamaParsers.parseSlots(null, Instant.now()));
    }

    @Test
    void recognisesRunnerCommandLines() {
        assertTrue(OllamaParsers.isRunnerCommandLine(RUNNER_CMD));
        assertTrue(OllamaParsers.isRunnerCommandLine("/usr/bin/ollama runner --model /x --port 41231 --threads 8"));
        assertFalse(OllamaParsers.isRunnerCommandLine("ollama serve"));
        assertFalse(OllamaParsers.isRunnerCommandLine("/usr/bin/java -jar camel-tui.jar --port 8123"));
        assertFalse(OllamaParsers.isRunnerCommandLine(null));
    }

    @Test
    void parsesRunnerCommandLine() {
        RunnerInfo runner = OllamaParsers.parseRunnerCommandLine(23629, RUNNER_CMD);
        assertNotNull(runner);
        assertEquals(23629, runner.pid());
        assertEquals(58237, runner.port());
        assertEquals(262144, runner.contextSize());
        assertEquals(1, runner.parallel());
        assertEquals("/Users/me/.ollama/models/blobs/sha256-d372de8e", runner.modelPath());
        assertEquals("llama-server", runner.executable());
    }

    @Test
    void runnerWithoutPortIsIgnored() {
        assertNull(OllamaParsers.parseRunnerCommandLine(1, "llama-server --model /x --port"));
        assertNull(OllamaParsers.parseRunnerCommandLine(1, "ollama serve"));
    }

    @Test
    void parsesAppleGpuFromIoreg() {
        GpuStats gpu = OllamaParsers.parseIoreg(IOREG);
        assertNotNull(gpu);
        assertEquals(25, gpu.utilizationPercent());
        assertEquals(31463079936L, gpu.memoryUsedBytes());
        assertEquals(34170552320L, gpu.memoryTotalBytes());
        assertEquals("Apple GPU", gpu.name());
        assertNull(OllamaParsers.parseIoreg(""));
        assertNull(OllamaParsers.parseIoreg("+-o Something {\"Unrelated\"=1}"));
    }

    @Test
    void parsesNvidiaSmiCsv() {
        GpuStats gpu = OllamaParsers.parseNvidiaSmi("NVIDIA GeForce RTX 4090, 35, 12000, 24564\n"
                                                    + "NVIDIA GeForce RTX 4090, 65, 2000, 24564\n");
        assertNotNull(gpu);
        assertEquals("NVIDIA GeForce RTX 4090", gpu.name());
        assertEquals(50, gpu.utilizationPercent());
        assertEquals(14000L * 1024 * 1024, gpu.memoryUsedBytes());
        assertEquals(2L * 24564 * 1024 * 1024, gpu.memoryTotalBytes());
        assertEquals(2, gpu.count());
        assertNull(OllamaParsers.parseNvidiaSmi("No devices were found"));
    }

    @Test
    void parsesCpuTimeAndResidentSizeFromPs() {
        long[] stats = OllamaParsers.parsePsCpuAndRss("   0:11.04 28790208\n");
        assertNotNull(stats);
        assertEquals(11040, stats[0]);
        assertEquals(28790208L * 1024, stats[1]);
        assertNull(OllamaParsers.parsePsCpuAndRss(""));
        assertNull(OllamaParsers.parsePsCpuAndRss(null));

        assertEquals(11040, OllamaParsers.parseCpuTimeMillis("0:11.04"));
        assertEquals(3723000, OllamaParsers.parseCpuTimeMillis("01:02:03"));
        assertEquals(86400000L + 3600000 + 120000 + 3000, OllamaParsers.parseCpuTimeMillis("1-01:02:03"));
        assertEquals(1500, OllamaParsers.parseCpuTimeMillis("1.5"));
        assertEquals(-1, OllamaParsers.parseCpuTimeMillis("n/a"));
        assertEquals(-1, OllamaParsers.parseCpuTimeMillis(null));
    }

    @Test
    void parsesOllamaTimestamps() {
        assertEquals(Instant.parse("2026-09-17T09:22:44.593036Z"),
                OllamaParsers.parseInstant("2026-09-17T11:22:44.593036+02:00"));
        assertEquals(Instant.parse("2026-09-17T09:17:44.592552Z"),
                OllamaParsers.parseInstant("2026-09-17T09:17:44.592552Z"));
        assertNull(OllamaParsers.parseInstant("0001-01-01"));
        assertNull(OllamaParsers.parseInstant(null));
    }

    @Test
    void loopbackAndDisplayHost() {
        assertTrue(OllamaParsers.isLoopbackUrl("http://localhost:11434"));
        assertTrue(OllamaParsers.isLoopbackUrl("http://127.0.0.1:32771"));
        assertFalse(OllamaParsers.isLoopbackUrl("http://gpu-box.lan:11434"));
        assertFalse(OllamaParsers.isLoopbackUrl(null));
        assertEquals("localhost:11434", OllamaParsers.displayHost("http://localhost:11434/"));
        assertEquals("gpu-box.lan:11434", OllamaParsers.displayHost("https://gpu-box.lan:11434"));
    }
}
