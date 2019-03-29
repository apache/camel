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

import static org.apache.camel.component.vertx.VertxHelper.getVertxBody;

public class VertxConsumer extends DefaultConsumer {

    private final VertxEndpoint endpoint;
    private transient MessageConsumer messageConsumer;

    private Handler<Message<Object>> handler = new Handler<Message<Object>>() {
        public void handle(Message event) {
            onEventBusEvent(event);
        }
    };

    public VertxConsumer(VertxEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    protected void onEventBusEvent(final Message event) {
        log.debug("onEvent {}", event);

        final boolean reply = event.replyAddress() != null;
        final Exchange exchange = endpoint.createExchange(reply ? ExchangePattern.InOut : ExchangePattern.InOnly);
        exchange.getIn().setBody(event.body());

        try {
            getAsyncProcessor().process(exchange, new AsyncCallback() {
                @Override
                public void done(boolean doneSync) {
                    if (reply) {
                        Object body = getVertxBody(exchange);
                        if (body != null) {
                            log.debug("Sending reply to: {} with body: {}", event.replyAddress(), body);
                            event.reply(body);
                        }
                    }
                }
            });
        } catch (Exception e) {
            getExceptionHandler().handleException("Error processing Vertx event: " + event, exchange, e);
        }
    }

    protected void doStart() throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Registering EventBus handler on address {}", endpoint.getAddress());
        }

        if (endpoint.getEventBus() != null) {
            messageConsumer = endpoint.getEventBus().consumer(endpoint.getAddress(), handler);
        }
        super.doStart();
    }

    protected void doStop() throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Unregistering EventBus handler on address {}", endpoint.getAddress());
        }

        try {
            if (messageConsumer != null && messageConsumer.isRegistered()) {
                messageConsumer.unregister();
                messageConsumer = null;
            }
        } catch (IllegalStateException e) {
            log.warn("EventBus already stopped on address {}", endpoint.getAddress());
            // ignore if already stopped as vertx throws this exception if its already stopped etc.
            // unfortunately it does not provide an nicer api to know its state
        }
        super.doStop();
    }
}
