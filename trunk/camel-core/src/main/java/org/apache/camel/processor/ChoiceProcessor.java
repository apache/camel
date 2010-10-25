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
import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Navigate;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.impl.converter.AsyncProcessorTypeConverter;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Implements a Choice structure where one or more predicates are used which if
 * they are true their processors are used, with a default otherwise clause used
 * if none match.
 * 
 * @version $Revision$
 */
public class ChoiceProcessor extends ServiceSupport implements AsyncProcessor, Navigate<Processor>, Traceable {
    private static final transient Log LOG = LogFactory.getLog(ChoiceProcessor.class);
    private final List<FilterProcessor> filters;
    private final AsyncProcessor otherwise;

    public ChoiceProcessor(List<FilterProcessor> filters, Processor otherwise) {
        this.filters = filters;
        this.otherwise = AsyncProcessorTypeConverter.convert(otherwise);
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        for (int i = 0; i < filters.size(); i++) {
            FilterProcessor filter = filters.get(i);
            Predicate predicate = filter.getPredicate();

            boolean matches = false;
            try {
                // ensure we handle exceptions thrown when matching predicate
                if (predicate != null) {
                    matches = predicate.matches(exchange);
                }
            } catch (Throwable e) {
                exchange.setException(e);
                callback.done(true);
                return true;
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("#" + i + " - " + predicate + " matches: " + matches + " for: " + exchange);
            }

            if (matches) {
                // process next will also take care (has not null test) if next was a stop().
                // stop() has no processor to execute, and thus we will end in a NPE
                return filter.processNext(exchange, callback);
            }
        }
        if (otherwise != null) {
            return AsyncProcessorHelper.process(otherwise, exchange, callback);
        } else {
            callback.done(true);
            return true;
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("choice{");
        boolean first = true;
        for (FilterProcessor processor : filters) {
            if (first) {
                first = false;
            } else {
                builder.append(", ");
            }
            builder.append("when ");
            builder.append(processor.getPredicate().toString());
            builder.append(": ");
            builder.append(processor.getProcessor());
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
