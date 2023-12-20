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

import io.opentelemetry.api.trace.Span;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.tracing.ActiveSpanManager;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A processor which adds a attribute on the active {@link io.opentelemetry.api.trace.Span} with an
 * {@link org.apache.camel.Expression}
 */
public class AttributeProcessor extends AsyncProcessorSupport implements Traceable, IdAware, RouteIdAware {

    private static final Logger LOG = LoggerFactory.getLogger(AttributeProcessor.class);
    private final String attributeName;
    private final Expression expression;
    private String id;
    private String routeId;

    public AttributeProcessor(String tagName, Expression expression) {
        this.attributeName = ObjectHelper.notNull(tagName, "tagName");
        this.expression = ObjectHelper.notNull(expression, "expression");
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            OpenTelemetrySpanAdapter camelSpan = (OpenTelemetrySpanAdapter) ActiveSpanManager.getSpan(exchange);
            Span span = camelSpan.getOpenTelemetrySpan();
            if (span != null) {
                String tag = expression.evaluate(exchange, String.class);
                span.setAttribute(attributeName, tag);
            } else {
                LOG.warn("OpenTelemetry: could not find managed span for exchange={}", exchange);
            }
        } catch (Exception e) {
            exchange.setException(e);
        } finally {
            // callback must be invoked
            callback.done(true);
        }

        return true;
    }

    @Override
    public String getTraceLabel() {
        return "attribute[" + attributeName + ", " + expression + "]";
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public Expression getExpression() {
        return expression;
    }

    @Override
    public String toString() {
        return id;
    }
}
