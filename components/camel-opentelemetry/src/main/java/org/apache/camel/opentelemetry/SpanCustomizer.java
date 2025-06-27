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
package org.apache.camel.opentelemetry;

import io.opentelemetry.api.trace.SpanBuilder;
import org.apache.camel.Exchange;

/**
 * An abstraction to customize the generation of a {@link io.opentelemetry.api.trace.Span} produced by
 * {@link OpenTelemetryTracer}.
 */
public interface SpanCustomizer {
    /**
     * Applies customizations to Spans created by {@link OpenTelemetryTracer}.
     *
     * @param spanBuilder   The {@link SpanBuilder} instance for customizing the span
     * @param operationName The name of the tracing operation
     * @param exchange      The exchange instance
     */
    void customize(SpanBuilder spanBuilder, String operationName, Exchange exchange);

    /**
     * Determines whether customizations to the {@link io.opentelemetry.api.trace.Span} should be applied. For example,
     * if the operation name or some properties of the exchange match specific conditions. By default, customizations
     * are applied to all spans.
     *
     * @param  operationName The name of the tracing operation
     * @param  exchange      The exchange instance
     * @return               {@code true} if customizations should be applied a {@link io.opentelemetry.api.trace.Span},
     *                       else {@code false} if they should not be applied.
     */
    default boolean isEnabled(String operationName, Exchange exchange) {
        return true;
    }
}
