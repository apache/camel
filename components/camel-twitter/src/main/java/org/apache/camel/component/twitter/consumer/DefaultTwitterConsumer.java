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
package org.apache.camel.component.twitter.consumer;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.twitter.AbstractTwitterEndpoint;
import org.apache.camel.component.twitter.data.EndpointType;
import org.apache.camel.support.ScheduledPollConsumer;

/**
 * Provides a scheduled polling consumer.
 */
public class DefaultTwitterConsumer extends ScheduledPollConsumer implements TwitterEventListener {

    public static final long DEFAULT_CONSUMER_DELAY = 30 * 1000L;
    private final AbstractTwitterEndpoint endpoint;
    private final AbstractTwitterConsumerHandler handler;

    public DefaultTwitterConsumer(AbstractTwitterEndpoint endpoint, Processor processor, AbstractTwitterConsumerHandler handler) {
        super(endpoint, processor);
        setDelay(DEFAULT_CONSUMER_DELAY);
        this.endpoint = endpoint;
        this.handler = handler;
    }

    @Override
    public AbstractTwitterEndpoint getEndpoint() {
        return (AbstractTwitterEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (endpoint.getEndpointType().equals(EndpointType.DIRECT)) {
            List<Exchange> exchanges = handler.directConsume();
            for (int i = 0; i < exchanges.size(); i++) {
                getProcessor().process(exchanges.get(i));
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
    }

    @Override
    protected int poll() throws Exception {
        List<Exchange> exchanges = handler.pollConsume();

        int index = 0;
        for (; index < exchanges.size(); index++) {
            getProcessor().process(exchanges.get(index));
        }

        return index;
    }

    @Override
    public void onEvent(Exchange exchange) {
        if (!isRunAllowed()) {
            return;
        }

        try {
            getProcessor().process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }

        if (exchange.getException() != null) {
            getExceptionHandler().handleException("Error processing exchange on status update", exchange, exchange.getException());
        }
    }

}
