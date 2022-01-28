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

import io.opentracing.Span;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.tracing.ActiveSpanManager;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A processor which gets a baggage item from the active {@link Span} and sets it as a Header
 */
@Deprecated
public class GetBaggageProcessor extends AsyncProcessorSupport implements Traceable, IdAware, RouteIdAware {

    private static final Logger LOG = LoggerFactory.getLogger(GetBaggageProcessor.class);

    private String id;
    private String routeId;
    private final String headerName;
    private final String baggageName;

    public GetBaggageProcessor(String baggageName, String headerName) {
        this.baggageName = baggageName;
        this.headerName = headerName;
        ObjectHelper.notNull(baggageName, "baggageName");
        ObjectHelper.notNull(headerName, "headerName");
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            OpenTracingSpanAdapter camelSpan = (OpenTracingSpanAdapter) ActiveSpanManager.getSpan(exchange);
            Span span = camelSpan.getOpenTracingSpan();
            if (span != null) {
                String item = span.getBaggageItem(baggageName);
                exchange.getMessage().setHeader(headerName, item);
            } else {
                LOG.warn("OpenTracing: could not find managed span for exchange={}", exchange);
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
        return "getBaggage[" + baggageName + ", " + headerName + "]";
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
        return baggageName;
    }

    public String getHeaderName() {
        return headerName;
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
