/**
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
import java.util.Iterator;
import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorConverterHelper;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.processor.PipelineHelper.continueProcessing;

/**
 * Implements a Choice structure where one or more predicates are used which if
 * they are true their processors are used, with a default otherwise clause used
 * if none match.
 * 
 * @version 
 */
public class ChoiceProcessor extends ServiceSupport implements AsyncProcessor, Navigate<Processor>, Traceable, IdAware {
    private static final Logger LOG = LoggerFactory.getLogger(ChoiceProcessor.class);
    private String id;
    private final List<FilterProcessor> filters;
    private final Processor otherwise;
    private transient long notFiltered;

    public ChoiceProcessor(List<FilterProcessor> filters, Processor otherwise) {
        this.filters = filters;
        this.otherwise = otherwise;
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        Iterator<Processor> processors = next().iterator();

        // callback to restore existing FILTER_MATCHED property on the Exchange
        final Object existing = exchange.getProperty(Exchange.FILTER_MATCHED);
        final AsyncCallback choiceCallback = new AsyncCallback() {
            @Override
            public void done(boolean doneSync) {
                if (existing != null) {
                    exchange.setProperty(Exchange.FILTER_MATCHED, existing);
                } else {
                    exchange.removeProperty(Exchange.FILTER_MATCHED);
                }
                callback.done(doneSync);
            }
        };

        // as we only pick one processor to process, then no need to have async callback that has a while loop as well
        // as this should not happen, eg we pick the first filter processor that matches, or the otherwise (if present)
        // and if not, we just continue without using any processor
        while (processors.hasNext()) {
            // get the next processor
            Processor processor = processors.next();

            // evaluate the predicate on filter predicate early to be faster
            // and avoid issues when having nested choices
            // as we should only pick one processor
            boolean matches = false;
            if (processor instanceof FilterProcessor) {
                FilterProcessor filter = (FilterProcessor) processor;
                try {
                    matches = filter.matches(exchange);
                    // as we have pre evaluated the predicate then use its processor directly when routing
                    processor = filter.getProcessor();
                } catch (Throwable e) {
                    exchange.setException(e);
                }
            } else {
                // its the otherwise processor, so its a match
                notFiltered++;
                matches = true;
            }

            // check for error if so we should break out
            if (!continueProcessing(exchange, "so breaking out of choice", LOG)) {
                break;
            }

            // if we did not match then continue to next filter
            if (!matches) {
                continue;
            }

            // okay we found a filter or its the otherwise we are processing
            AsyncProcessor async = AsyncProcessorConverterHelper.convert(processor);
            return async.process(exchange, choiceCallback);
        }

        // when no filter matches and there is no otherwise, then just continue
        choiceCallback.done(true);
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("choice{");
        boolean first = true;
        for (Processor processor : filters) {
            if (first) {
                first = false;
            } else {
                builder.append(", ");
            }
            builder.append("when ");
            builder.append(processor);
        }
        if (otherwise != null) {
            builder.append(", otherwise: ");
            builder.append(otherwise);
        }
        builder.append("}");
        return builder.toString();
    }

    public String getTraceLabel() {
        return "choice";
    }

    public List<FilterProcessor> getFilters() {
        return filters;
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
        for (FilterProcessor filter : getFilters()) {
            filter.reset();
        }
        notFiltered = 0;
    }

    public List<Processor> next() {
        if (!hasNext()) {
            return null;
        }
        List<Processor> answer = new ArrayList<Processor>();
        if (filters != null) {
            answer.addAll(filters);
        }
        if (otherwise != null) {
            answer.add(otherwise);
        }
        return answer;
    }

    public boolean hasNext() {
        return otherwise != null || (filters != null && !filters.isEmpty());
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    protected void doStart() throws Exception {
        ServiceHelper.startServices(filters, otherwise);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(otherwise, filters);
    }

}
