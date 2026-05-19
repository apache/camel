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
package org.apache.camel.spi;

import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;

/**
 * Extended {@link BacklogTracer} API for components that process exchanges inline and bypass the normal route pipeline
 * (e.g. mock mode in the rest-openapi consumer). Such components cannot rely on the automatic tracing that
 * {@code CamelInternalProcessor} applies to every node in the route graph, so they must emit synthetic first/last trace
 * events manually to participate in message-history capture.
 * <p>
 * Callers obtain a {@link SyntheticBacklogTracer} from the context plugin manager and guard on null before invoking the
 * synthetic tracing methods:
 *
 * <pre>
 * <code>
 * SyntheticBacklogTracer tracer = camelContext.getCamelContextExtension().getContextPlugin(SyntheticBacklogTracer.class);
 * if (tracer != null &amp;&amp; (tracer.isEnabled() || tracer.isStandby())) {
 *     tracer.traceFirstNode(node, exchange);
 * }
 * try {
 *     // ... inline processing ...
 * } finally {
 *     if (tracer != null &amp;&amp; (tracer.isEnabled() || tracer.isStandby())) {
 *         tracer.traceLastNode(node, exchange);
 *     }
 * }
 * </code>
 * </pre>
 *
 * @since 4.21
 */
public interface SyntheticBacklogTracer extends BacklogTracer {

    /**
     * Emits a synthetic <em>first</em> trace event ({@code first=true, last=false}) for the given node and exchange.
     * <p>
     * Call this before the inline processing begins. It pairs with {@link #traceLastNode} to bracket the operation,
     * mirroring what {@code BacklogTracerRouteAdvice} does automatically for normal route nodes.
     *
     * @param node     the synthetic node representing the inline operation
     * @param exchange the current exchange
     * @since          4.21
     */
    void traceFirstNode(NamedNode node, Exchange exchange);

    /**
     * Emits a synthetic <em>last</em> trace event ({@code first=false, last=true}) for the given node and exchange.
     * <p>
     * Call this after the inline processing completes (typically in a {@code finally} block). The {@code last=true}
     * flag triggers message-history completion in the tracer, making the exchange visible in the history view.
     *
     * @param node     the synthetic node representing the inline operation
     * @param exchange the current exchange
     * @since          4.21
     */
    void traceLastNode(NamedNode node, Exchange exchange);
}
