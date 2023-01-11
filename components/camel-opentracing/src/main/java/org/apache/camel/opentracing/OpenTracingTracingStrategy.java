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
package org.apache.camel.opentracing;

import java.util.HashMap;
import java.util.Map;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.processor.LogProcessor;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.apache.camel.tracing.ActiveSpanManager;
import org.apache.camel.tracing.SpanDecorator;

@Deprecated
public class OpenTracingTracingStrategy implements InterceptStrategy {
    private static final String UNNAMED = "unnamed";
    private final OpenTracingTracer tracer;

    public OpenTracingTracingStrategy(OpenTracingTracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Processor wrapProcessorInInterceptors(
            CamelContext camelContext,
            NamedNode processorDefinition, Processor target, Processor nextTarget)
            throws Exception {
        if (!shouldTrace(processorDefinition)) {
            return new DelegateAsyncProcessor(target);
        }

        return new DelegateAsyncProcessor((Exchange exchange) -> {
            OpenTracingSpanAdapter spanWrapper = (OpenTracingSpanAdapter) ActiveSpanManager.getSpan(exchange);
            Span span = spanWrapper.getOpenTracingSpan();
            if (span == null) {
                target.process(exchange);
                return;
            }

            final Span processorSpan = tracer.getTracer().buildSpan(getOperationName(processorDefinition))
                    .asChildOf(span)
                    .withTag(Tags.COMPONENT.getKey(), getComponentName(processorDefinition))
                    .start();

            boolean activateExchange = !(target instanceof LogProcessor
                    || target instanceof TagProcessor
                    || target instanceof GetBaggageProcessor
                    || target instanceof SetBaggageProcessor);

            if (activateExchange) {
                ActiveSpanManager.activate(exchange, new OpenTracingSpanAdapter(processorSpan));
            }

            try (Scope scope = tracer.getTracer().scopeManager().activate(processorSpan)) {
                target.process(exchange);
            } catch (Exception ex) {
                processorSpan.log(errorLogs(ex));
                throw ex;
            } finally {
                if (activateExchange) {
                    ActiveSpanManager.deactivate(exchange);
                }

                processorSpan.finish();
            }
        });
    }

    private static String getComponentName(NamedNode processorDefinition) {
        return SpanDecorator.CAMEL_COMPONENT + processorDefinition.getShortName();
    }

    private static String getOperationName(NamedNode processorDefinition) {
        final String name = processorDefinition.getId();
        return name == null ? UNNAMED : name;
    }

    private static Map<String, Object> errorLogs(final Throwable t) {
        final Map<String, Object> logEvent = new HashMap<>(3);
        logEvent.put("event", Tags.ERROR.getKey());
        logEvent.put("error.kind", "Exception");
        logEvent.put("message", t.getMessage());
        return logEvent;
    }

    // Adapted from org.apache.camel.impl.engine.DefaultTracer.shouldTrace
    // org.apache.camel.impl.engine.DefaultTracer.shouldTracePattern
    private boolean shouldTrace(NamedNode definition) {
        for (String pattern : tracer.getExcludePatterns()) {
            // use matchPattern method from endpoint helper that has a good matcher we use in Camel
            if (PatternHelper.matchPattern(definition.getId(), pattern)) {
                return false;
            }
        }

        return true;
    }
}
