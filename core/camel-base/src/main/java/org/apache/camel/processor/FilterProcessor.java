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
package org.apache.camel.processor;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The processor which implements the
 * <a href="http://camel.apache.org/message-filter.html">Message Filter</a> EIP pattern.
 */
public class FilterProcessor extends DelegateAsyncProcessor implements Traceable, IdAware, RouteIdAware {

    private static final Logger LOG = LoggerFactory.getLogger(FilterProcessor.class);

    private final CamelContext context;
    private String id;
    private String routeId;
    private final Predicate predicate;
    private transient long filtered;

    public FilterProcessor(CamelContext context, Predicate predicate, Processor processor) {
        super(processor);
        this.context = context;
        this.predicate = predicate;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        predicate.init(context);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        boolean matches = false;

        try {
            matches = matches(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }

        if (matches) {
            return processor.process(exchange, callback);
        } else {
            callback.done(true);
            return true;
        }
    }

    public boolean matches(Exchange exchange) {
        boolean matches = predicate.matches(exchange);

        LOG.debug("Filter matches: {} for exchange: {}", matches, exchange);

        // set property whether the filter matches or not
        exchange.setProperty(Exchange.FILTER_MATCHED, matches);

        if (matches) {
            filtered++;
        }

        return matches;
    }

    @Override
    public String toString() {
        return id;
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

    @Override
    public String getTraceLabel() {
        return "filter[if: " + predicate + "]";
    }

    public Predicate getPredicate() {
        return predicate;
    }

    /**
     * Gets the number of Exchanges that matched the filter predicate and therefore as filtered.
     */
    public long getFilteredCount() {
        return filtered;
    }

    /**
     * Reset counters.
     */
    public void reset() {
        filtered = 0;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        // restart counter
        reset();
        ServiceHelper.startService(predicate);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(predicate);
        super.doStop();
    }
}
