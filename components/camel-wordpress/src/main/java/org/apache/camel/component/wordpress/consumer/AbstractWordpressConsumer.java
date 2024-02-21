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
package org.apache.camel.component.wordpress.consumer;

import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.wordpress.WordpressConfiguration;
import org.apache.camel.component.wordpress.WordpressEndpoint;
import org.apache.camel.support.ScheduledPollConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractWordpressConsumer extends ScheduledPollConsumer {

    protected static final Logger LOG = LoggerFactory.getLogger(AbstractWordpressConsumer.class);

    private WordpressConfiguration configuration;

    public AbstractWordpressConsumer(WordpressEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.configuration = endpoint.getConfiguration();
        this.initConsumer();
    }

    public AbstractWordpressConsumer(WordpressEndpoint endpoint, Processor processor,
                                     ScheduledExecutorService scheduledExecutorService) {
        super(endpoint, processor, scheduledExecutorService);
        this.configuration = endpoint.getConfiguration();
        this.initConsumer();
    }

    public WordpressConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public boolean isGreedy() {
        return false;
    }

    private void initConsumer() {
        this.configureService(configuration);
    }

    /**
     * Should be implemented to configure the endpoint calls. Called during consumer initialization
     *
     * @param configuration the endpoint configuration
     */
    protected void configureService(WordpressConfiguration configuration) {
        // noop
    }

    @Override
    protected abstract int poll() throws Exception;

    protected final void process(final Object result) {
        Exchange exchange = createExchange(false);
        try {
            exchange.getIn().setBody(result);
            getProcessor().process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }
        if (exchange.getException() != null) {
            getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
        }
        releaseExchange(exchange, false);
    }
}
