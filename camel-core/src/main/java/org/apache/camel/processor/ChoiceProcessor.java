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
public class ChoiceProcessor extends ServiceSupport implements AsyncProcessor, Navigate<Processor>, Traceable {
    private static final Logger LOG = LoggerFactory.getLogger(ChoiceProcessor.class);
    private final List<Processor> filters;
    private final Processor otherwise;

    public ChoiceProcessor(List<Processor> filters, Processor otherwise) {
        this.filters = filters;
        this.otherwise = otherwise;
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        Iterator<Processor> processors = next().iterator();

        exchange.setProperty(Exchange.FILTER_MATCHED, false);
        while (continueRouting(processors, exchange)) {
            // get the next processor
            Processor processor = processors.next();

            AsyncProcessor async = AsyncProcessorConverterHelper.convert(processor);
            boolean sync = process(exchange, callback, processors, async);

            // continue as long its being processed synchronously
            if (!sync) {
                LOG.trace("Processing exchangeId: {} is continued being processed asynchronously", exchange.getExchangeId());
                // the remainder of the CBR will be completed async
                // so we break out now, then the callback will be invoked which then continue routing from where we left here
                return false;
            }

            LOG.trace("Processing exchangeId: {} is continued being processed synchronously", exchange.getExchangeId());

            // check for error if so we should break out
            if (!continueProcessing(exchange, "so breaking out of content based router", LOG)) {
                break;
            }
        }

        LOG.trace("Processing complete for exchangeId: {} >>> {}", exchange.getExchangeId(), exchange);

        callback.done(true);
        return true;
    }

    protected boolean continueRouting(Iterator<Processor> it, Exchange exchange) {
        boolean answer = it.hasNext();
        if (answer) {
            Object matched = exchange.getProperty(Exchange.FILTER_MATCHED);
            if (matched != null) {
                boolean hasMatched = exchange.getContext().getTypeConverter().convertTo(Boolean.class, matched);
                if (hasMatched) {
                    LOG.debug("ExchangeId: {} has been matched: {}", exchange.getExchangeId(), exchange);
                    answer = false;
                }
            }
        }
        LOG.trace("ExchangeId: {} should continue matching: {}", exchange.getExchangeId(), answer);
        return answer;
    }

    private boolean process(final Exchange exchange, final AsyncCallback callback,
                            final Iterator<Processor> processors, final AsyncProcessor asyncProcessor) {
        // this does the actual processing so log at trace level
        LOG.trace("Processing exchangeId: {} >>> {}", exchange.getExchangeId(), exchange);

        // implement asynchronous routing logic in callback so we can have the callback being
        // triggered and then continue routing where we left
        boolean sync = asyncProcessor.process(exchange, new AsyncCallback() {
            public void done(boolean doneSync) {
                // we only have to handle async completion of the pipeline
                if (doneSync) {
                    return;
                }

                // continue processing the pipeline asynchronously
                while (continueRouting(processors, exchange)) {
                    AsyncProcessor processor = AsyncProcessorConverterHelper.convert(processors.next());

                    // check for error if so we should break out
                    if (!continueProcessing(exchange, "so breaking out of pipeline", LOG)) {
                        break;
                    }

                    doneSync = process(exchange, callback, processors, processor);
                    if (!doneSync) {
                        LOG.trace("Processing exchangeId: {} is continued being processed asynchronously", exchange.getExchangeId());
                        return;
                    }
                }

                LOG.trace("Processing complete for exchangeId: {} >>> {}", exchange.getExchangeId(), exchange);
                callback.done(false);
            }
        });

        return sync;
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

    public List<Processor> getFilters() {
        return filters;
    }

    public Processor getOtherwise() {
        return otherwise;
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

    protected void doStart() throws Exception {
        ServiceHelper.startServices(filters, otherwise);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(otherwise, filters);
    }
}
