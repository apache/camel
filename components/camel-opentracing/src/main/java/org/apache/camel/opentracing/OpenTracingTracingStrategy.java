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
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenTracingTracingStrategy implements InterceptStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(OpenTracingTracingStrategy.class);
    private static final String UNNAMED = "unnamed";
    private final Tracer tracer;

    public OpenTracingTracingStrategy(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Processor wrapProcessorInInterceptors(CamelContext camelContext,
                                                 NamedNode processorDefinition, Processor target, Processor nextTarget)
            throws Exception {

        return new DelegateAsyncProcessor((Exchange exchange) -> {
            Span span = ActiveSpanManager.getSpan(exchange);
            if (span == null) {
                target.process(exchange);
                return;
            }

            final Span processorSpan = tracer.buildSpan(getOperationName(processorDefinition))
                    .asChildOf(span)
                    .withTag(Tags.COMPONENT, getComponentName(processorDefinition))
                    .start();

            ActiveSpanManager.activate(exchange, processorSpan);

            try (final Scope inScope = tracer.activateSpan(processorSpan)) {
                target.process(exchange);
            } catch (Exception ex) {
                processorSpan.log(errorLogs(ex));
                throw ex;
            } finally {
                ActiveSpanManager.deactivate(exchange);
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
        final Map<String, Object> errorLogs = new HashMap<>(2);
        errorLogs.put("event", Tags.ERROR.getKey());
        errorLogs.put("error.object", t);
        return errorLogs;
    }
}
