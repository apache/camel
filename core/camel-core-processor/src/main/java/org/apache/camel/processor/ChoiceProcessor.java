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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.processor.PipelineHelper.continueProcessing;

/**
 * Implements a Choice structure where one or more predicates are used which if they are true their processors are used,
 * with a default otherwise clause used if none match.
 */
public class ChoiceProcessor extends AsyncProcessorSupport implements Navigate<Processor>, Traceable, IdAware, RouteIdAware {

    private static final Logger LOG = LoggerFactory.getLogger(ChoiceProcessor.class);

    private String id;
    private String routeId;
    // optimize to use an array
    private final FilterProcessor[] filters;
    private final int len;
    private final AsyncProcessor otherwise;
    private transient long notFiltered;

    public ChoiceProcessor(List<FilterProcessor> filters, Processor otherwise) {
        this.filters = filters.toArray(new FilterProcessor[0]);
        this.len = filters.size();
        this.otherwise = otherwise != null ? AsyncProcessorConverterHelper.convert(otherwise) : null;
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        // find the first matching filter and process the exchange using it
        for (int i = 0; i < len; i++) {
            FilterProcessor filter = filters[i];
            // evaluate the predicate on filter predicate early to be faster
            // and avoid issues when having nested choices
            // as we should only pick one processor
            boolean matches = false;
            try {
                matches = filter.matches(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }

            // check for error if so we should break out
            if (!continueProcessing(exchange, "so breaking out of choice", LOG)) {
                // okay there was an error in the predicate so we should exit
                callback.done(true);
                return true;
            }

            // if we did not match then continue to next filter
            if (!matches) {
                continue;
            }

            // okay we found a filter then process it directly via its processor as we have already done the matching
            return filter.getProcessor().process(exchange, callback);
        }

        if (otherwise != null) {
            // no filter matched then use otherwise
            notFiltered++;
            return otherwise.process(exchange, callback);
        } else {
            // when no filter matches and there is no otherwise, then just continue
            callback.done(true);
            return true;
        }
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public String getTraceLabel() {
        return "choice";
    }

    public List<FilterProcessor> getFilters() {
        return Arrays.asList(filters);
    }

    public Processor getOtherwise() {
        return otherwise;
    }

    /**
     * Gets the number of Exchanges that did not match any predicate and are routed using otherwise
     */
    public long getNotFilteredCount() {
        return notFiltered;
    }

    /**
     * Reset counters.
     */
    public void reset() {
        for (int i = 0; i < len; i++) {
            filters[i].reset();
        }
        notFiltered = 0;
    }

    @Override
    public List<Processor> next() {
        if (!hasNext()) {
            return null;
        }
        List<Processor> answer = new ArrayList<>();
        if (len > 0) {
            answer.addAll(Arrays.asList(filters));
        }
        if (otherwise != null) {
            answer.add(otherwise);
        }
        return answer;
    }

    @Override
    public boolean hasNext() {
        return otherwise != null || len > 0;
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
    protected void doInit() throws Exception {
        ServiceHelper.initService(Arrays.asList(filters), otherwise);
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(Arrays.asList(filters), otherwise);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(otherwise, Arrays.asList(filters));
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(otherwise, Arrays.asList(filters));
    }

}
