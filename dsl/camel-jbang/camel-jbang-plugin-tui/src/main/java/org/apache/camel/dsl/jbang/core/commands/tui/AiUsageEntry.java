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

/**
 * One model round trip. {@code question} is the sequence number of the panel question the request was made for (a
 * question takes several round trips when the model calls tools), so usage can be grouped per question; it is 0 for
 * route requests, which belong to no question. {@code cachedInputTokens} are the input tokens served from the
 * provider's prompt cache (part of {@code inputTokens}), when known.
 */
record AiUsageEntry(String model, String provider, int inputTokens, int outputTokens,
        int totalTokens, long latencyMs, String stopReason, Instant timestamp,
        AiUsageSource source, String routeId, int question, int cachedInputTokens) {

    AiUsageEntry(String model, String provider, int inputTokens, int outputTokens,
                 int totalTokens, long latencyMs, String stopReason, Instant timestamp) {
        this(model, provider, inputTokens, outputTokens, totalTokens, latencyMs, stopReason, timestamp,
             AiUsageSource.TUI, null, 0, 0);
    }

    AiUsageEntry(String model, String provider, int inputTokens, int outputTokens,
                 int totalTokens, long latencyMs, String stopReason, Instant timestamp,
                 AiUsageSource source, String routeId) {
        this(model, provider, inputTokens, outputTokens, totalTokens, latencyMs, stopReason, timestamp,
             source, routeId, 0, 0);
    }
}
