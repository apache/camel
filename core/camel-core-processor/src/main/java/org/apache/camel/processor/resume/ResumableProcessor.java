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

package org.apache.camel.processor.resume;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.Resumable;
import org.apache.camel.ResumeStrategy;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.AsyncProcessorSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResumableProcessor extends AsyncProcessorSupport
        implements Navigate<Processor>, CamelContextAware, IdAware, RouteIdAware {
    private static final Logger LOG = LoggerFactory.getLogger(ResumableProcessor.class);
    private CamelContext camelContext;
    private ResumeStrategy resumeStrategy;
    private AsyncProcessor processor;
    private String id;
    private String routeId;

    private static class ResumableProcessorCallback implements AsyncCallback {

        private final Exchange exchange;
        private final Synchronization completion;
        private final AsyncCallback callback;

        public ResumableProcessorCallback(Exchange exchange, Synchronization completion, AsyncCallback callback) {
            this.exchange = exchange;
            this.completion = completion;
            this.callback = callback;
        }

        @Override
        public void done(boolean doneSync) {
            try {
                if (exchange.isFailed()) {
                    completion.onFailure(exchange);
                } else {
                    completion.onComplete(exchange);
                }
            } finally {
                callback.done(doneSync);
            }
        }
    }

    public ResumableProcessor(ResumeStrategy resumeStrategy, Processor processor) {
        this.resumeStrategy = Objects.requireNonNull(resumeStrategy);
        this.processor = AsyncProcessorConverterHelper.convert(processor);

        LOG.info("Enabling the resumable strategy of type: {}", resumeStrategy.getClass().getSimpleName());
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        Object offset = exchange.getMessage().getHeader(Exchange.OFFSET);

        if (offset instanceof Resumable) {
            Resumable<?, ?> resumable = (Resumable<?, ?>) offset;

            LOG.warn("Processing the resumable: {}", resumable.getAddressable());
            LOG.warn("Processing the resumable of type: {}", resumable.getLastOffset().offset());

            final Synchronization onCompletion = new ResumableCompletion(resumeStrategy, resumable);
            final AsyncCallback target = new ResumableProcessorCallback(exchange, onCompletion, callback);
            return processor.process(exchange, target);

        } else {
            exchange.setException(new NoOffsetException(exchange));
            LOG.warn("Cannot update the last offset because it's not available");
            return true;
        }
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
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
    public List<Processor> next() {
        if (!hasNext()) {
            return null;
        }
        List<Processor> answer = new ArrayList<>(1);
        answer.add(processor);
        return answer;
    }

    @Override
    public boolean hasNext() {
        return processor != null;
    }
}
