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
import org.apache.camel.LoggingLevel;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.resume.ResumeStrategy;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.AsyncProcessorSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resume EIP
 */
public class ResumableProcessor extends AsyncProcessorSupport
        implements Navigate<Processor>, CamelContextAware, IdAware, RouteIdAware {

    private static final Logger LOG = LoggerFactory.getLogger(ResumableProcessor.class);

    private CamelContext camelContext;
    private final ResumeStrategy resumeStrategy;
    private final AsyncProcessor processor;
    private final LoggingLevel loggingLevel;
    private final boolean intermittent;
    private String id;
    private String routeId;

    public ResumableProcessor(ResumeStrategy resumeStrategy, Processor processor, LoggingLevel loggingLevel,
                              boolean intermittent) {
        this.resumeStrategy = Objects.requireNonNull(resumeStrategy);
        this.processor = AsyncProcessorConverterHelper.convert(processor);
        this.loggingLevel = loggingLevel;
        this.intermittent = intermittent;
    }

    @Override
    protected void doStart() throws Exception {
        LOG.info("Starting the resumable strategy: {}", resumeStrategy.getClass().getSimpleName());
        resumeStrategy.start();

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        LOG.info("Stopping the resumable strategy: {}", resumeStrategy.getClass().getSimpleName());
        resumeStrategy.stop();
        super.doStop();
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        final Synchronization onCompletion = new ResumableCompletion(resumeStrategy, loggingLevel, intermittent);

        exchange.getExchangeExtension().addOnCompletion(onCompletion);

        return processor.process(exchange, callback);
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
