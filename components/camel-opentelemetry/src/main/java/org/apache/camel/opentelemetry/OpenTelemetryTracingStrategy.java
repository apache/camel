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

import static org.apache.camel.tracing.ActiveSpanManager.MDC_SPAN_ID;
import static org.apache.camel.tracing.ActiveSpanManager.MDC_TRACE_ID;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.apache.camel.tracing.ActiveSpanManager;
import org.apache.camel.tracing.SpanDecorator;
import org.slf4j.MDC;

public class OpenTelemetryTracingStrategy implements InterceptStrategy {

    private static final String UNNAMED = "unnamed";

    private final OpenTelemetryTracer tracer;
    private boolean propagateContext;

    public OpenTelemetryTracingStrategy(OpenTelemetryTracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Processor wrapProcessorInInterceptors(
            CamelContext camelContext,
            NamedNode processorDefinition, Processor target, Processor nextTarget) {
        if (tracer.isCreateSpanPerProcessor() && shouldTrace(processorDefinition)) {
            return new PropagateContextAndCreateSpan(processorDefinition, target);
        } else if (isPropagateContext()) {
            return new PropagateContext(target);
        } else {
            return new DelegateAsyncProcessor(target);
        }
    }

    public boolean isPropagateContext() {
        return propagateContext;
    }

    public void setPropagateContext(boolean propagateContext) {
        this.propagateContext = propagateContext;
    }

    private class PropagateContextAndCreateSpan implements Processor {
        private final NamedNode processorDefinition;
        private final Processor target;

        public PropagateContextAndCreateSpan(NamedNode processorDefinition, Processor target) {
            this.processorDefinition = processorDefinition;
            this.target = target;
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            Span span = null;
            OpenTelemetrySpanAdapter spanWrapper = (OpenTelemetrySpanAdapter) ActiveSpanManager.getSpan(exchange);
            if (spanWrapper != null) {
                span = spanWrapper.getOpenTelemetrySpan();
            }

            if (span == null) {
                target.process(exchange);
                return;
            }

            final Span processorSpan = tracer.getTracer().spanBuilder(getOperationName(processorDefinition))
                    .setParent(Context.current().with(span))
                    .setAttribute("component", getComponentName(processorDefinition))
                    .startSpan();

            boolean activateExchange = !(target instanceof GetCorrelationContextProcessor
                    || target instanceof SetCorrelationContextProcessor);

            if (activateExchange) {
                ActiveSpanManager.activate(exchange, new OpenTelemetrySpanAdapter(processorSpan));
            }

            try (Scope ignored = processorSpan.makeCurrent()) {
                target.process(exchange);
            } catch (Exception ex) {
                span.setStatus(StatusCode.ERROR);
                span.recordException(ex);
                throw ex;
            } finally {
                if (activateExchange) {
                    ActiveSpanManager.deactivate(exchange);
                }
                processorSpan.end();
            }
        }
    }

    private static class PropagateContext extends DelegateAsyncProcessor {

        public PropagateContext(Processor target) {
            super(target);
        }

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            Span span = null;
            OpenTelemetrySpanAdapter spanWrapper = (OpenTelemetrySpanAdapter) ActiveSpanManager.getSpan(exchange);
            if (spanWrapper != null) {
                span = spanWrapper.getOpenTelemetrySpan();
            }

            if (span == null) {
                return super.process(exchange, callback);
            }
            exchange.setProperty(ExchangePropertyKey.OTEL_CLOSE_CLIENT_SCOPE, true);

            if (Boolean.TRUE.equals(exchange.getContext().isUseMDCLogging())) {
                MDC.put(MDC_TRACE_ID, spanWrapper.traceId());
                MDC.put(MDC_SPAN_ID, spanWrapper.spanId());
            }

            return super.process(exchange, callback);
        }

    }

    private static String getComponentName(NamedNode processorDefinition) {
        return SpanDecorator.CAMEL_COMPONENT + processorDefinition.getShortName();
    }

    private static String getOperationName(NamedNode processorDefinition) {
        final String name = processorDefinition.getId();
        return name == null ? UNNAMED : name;
    }

    // Adapted from org.apache.camel.impl.engine.DefaultTracer.shouldTrace
    // org.apache.camel.impl.engine.DefaultTracer.shouldTracePattern
    private boolean shouldTrace(NamedNode definition) {
        if (tracer.getExcludePatterns() != null) {
            for (String pattern : tracer.getExcludePatterns().split(",")) {
                pattern = pattern.trim();
                // use matchPattern method from endpoint helper that has a good matcher we use in Camel
                if (PatternHelper.matchPattern(definition.getId(), pattern)) {
                    return false;
                }
            }
        }

        return true;
    }
}
