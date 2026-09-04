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
package org.apache.camel.component.ai.observability;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.apache.camel.CamelContext;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Micrometer-backed metrics recording. Loaded reflectively only when {@link MeterRegistry} is on the classpath.
 */
final class GenAiMicrometerSupport implements GenAiMetricsBackend {

    private final MeterRegistry meterRegistry;

    GenAiMicrometerSupport(CamelContext camelContext) {
        MeterRegistry registry = CamelContextHelper.findSingleByType(camelContext, MeterRegistry.class);
        if (registry == null) {
            registry = camelContext.getRegistry().lookupByNameAndType("metricsRegistry", MeterRegistry.class);
        }
        this.meterRegistry = registry;
    }

    @Override
    public boolean isAvailable() {
        return meterRegistry != null;
    }

    @Override
    public void recordMetrics(GenAiObservationContext context, GenAiUsage usage, Throwable error, long startNanos) {
        if (meterRegistry == null) {
            return;
        }
        Iterable<Tag> baseTags = baseTags(context, error);
        Timer.builder(GenAiMetrics.CLIENT_OPERATION)
                .tags(baseTags)
                .register(meterRegistry)
                .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
        recordTokenUsage(context, usage, error);
    }

    @Override
    public void recordTokenUsage(GenAiObservationContext context, GenAiUsage usage, Throwable error) {
        if (meterRegistry == null || usage == null) {
            return;
        }
        recordTokenCounter(usage.inputTokens(), GenAiMetrics.TOKEN_TYPE_INPUT, context, error);
        recordTokenCounter(usage.outputTokens(), GenAiMetrics.TOKEN_TYPE_OUTPUT, context, error);
    }

    private void recordTokenCounter(
            Long tokens, String tokenType, GenAiObservationContext context, Throwable error) {
        if (tokens == null || tokens <= 0) {
            return;
        }
        Counter.builder(GenAiMetrics.CLIENT_TOKEN_USAGE)
                .tags(baseTags(context, error))
                .tag(GenAiMetrics.TAG_TOKEN_TYPE, tokenType)
                .register(meterRegistry)
                .increment(tokens.doubleValue());
    }

    private static Iterable<Tag> baseTags(GenAiObservationContext context, Throwable error) {
        List<Tag> tags = new ArrayList<>();
        tags.add(Tag.of(GenAiMetrics.TAG_OPERATION_NAME, context.operationName().value()));
        tags.add(Tag.of(GenAiMetrics.TAG_SYSTEM, nullToUnknown(context.system())));
        tags.add(Tag.of(GenAiMetrics.TAG_REQUEST_MODEL, nullToUnknown(context.requestModel())));
        if (ObjectHelper.isNotEmpty(context.componentScheme())) {
            tags.add(Tag.of(GenAiMetrics.TAG_CAMEL_COMPONENT, context.componentScheme()));
        }
        if (error != null) {
            tags.add(Tag.of(GenAiMetrics.TAG_ERROR_TYPE, error.getClass().getSimpleName()));
        }
        return tags;
    }

    private static String nullToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
