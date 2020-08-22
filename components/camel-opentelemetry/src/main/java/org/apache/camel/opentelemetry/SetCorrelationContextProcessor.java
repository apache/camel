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
 * @author rvargasp
 */
public class SetCorrelationContextProcessor extends AsyncProcessorSupport implements Traceable, IdAware, RouteIdAware {

    private static final Logger LOG = LoggerFactory.getLogger(SetCorrelationContextProcessor.class);

    private String id;
    private String routeId;
    private final String baggageName;
    private final Expression expression;

    public SetCorrelationContextProcessor(String baggageName, Expression expression) {
        this.baggageName = baggageName;
        this.expression = expression;
        ObjectHelper.notNull(baggageName, "baggageName");
        ObjectHelper.notNull(expression, "expression");
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            OpenTelemetrySpanAdapter camelSpan = (OpenTelemetrySpanAdapter) ActiveSpanManager.getSpan(exchange);
            if (camelSpan != null) {
                String item = expression.evaluate(exchange, String.class);
                camelSpan.setCorrelationContextItem(baggageName, item);
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
    public String toString() {
        return id;
    }

    @Override
    public String getTraceLabel() {
        return "setCorrelationContext[" + baggageName + ", " + expression + "]";
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

    public String getBaggageName() {
        return baggageName.toString();
    }

    public Expression getExpression() {
        return expression;
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
