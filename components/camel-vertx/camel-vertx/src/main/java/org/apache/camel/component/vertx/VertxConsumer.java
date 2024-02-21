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
package org.apache.camel.component.vertx;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VertxConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(VertxConsumer.class);

    private final VertxEndpoint endpoint;
    private transient MessageConsumer<?> messageConsumer;

    private final Handler<Message<Object>> handler = VertxConsumer.this::onEventBusEvent;

    public VertxConsumer(VertxEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    protected void onEventBusEvent(final Message<?> event) {
        LOG.debug("onEvent {}", event);

        final boolean reply = event.replyAddress() != null;
        Exchange exchange = createExchange(true);
        exchange.setPattern(reply ? ExchangePattern.InOut : ExchangePattern.InOnly);
        exchange.getIn().setBody(event.body());

        try {
            getAsyncProcessor().process(exchange, new AsyncCallback() {
                @Override
                public void done(boolean doneSync) {
                    if (reply) {
                        Object body = exchange.getMessage().getBody();
                        if (body != null) {
                            LOG.debug("Sending reply to: {} with body: {}", event.replyAddress(), body);
                            event.reply(body);
                        }
                    }
                }
            });
        } catch (Exception e) {
            getExceptionHandler().handleException("Error processing Vertx event: " + event, e);
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Registering EventBus handler on address {}", endpoint.getAddress());
        }

        if (endpoint.getEventBus() != null) {
            messageConsumer = endpoint.getEventBus().consumer(endpoint.getAddress(), handler);
        }
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Unregistering EventBus handler on address {}", endpoint.getAddress());
        }

        try {
            if (messageConsumer != null && messageConsumer.isRegistered()) {
                messageConsumer.unregister();
                messageConsumer = null;
            }
        } catch (IllegalStateException e) {
            LOG.warn("EventBus already stopped on address {}", endpoint.getAddress());
            // ignore if already stopped as vertx throws this exception if its already stopped etc.
            // unfortunately it does not provide an nicer api to know its state
        }
        super.doStop();
    }
}
