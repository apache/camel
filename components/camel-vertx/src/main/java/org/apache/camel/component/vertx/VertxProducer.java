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
package org.apache.camel.component.vertx;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadRuntimeException;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.vertx.VertxHelper.getVertxBody;

public class VertxProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(VertxProducer.class);

    public VertxProducer(VertxEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public VertxEndpoint getEndpoint() {
        return (VertxEndpoint) super.getEndpoint();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        EventBus eventBus = getEndpoint().getEventBus();
        if (eventBus == null) {
            exchange.setException(new IllegalStateException("EventBus is not started or not configured"));
            callback.done(true);
            return true;
        }

        String address = getEndpoint().getAddress();

        boolean reply = ExchangeHelper.isOutCapable(exchange);
        boolean pubSub = getEndpoint().isPubSub();

        Object body = getVertxBody(exchange);
        if (body != null) {
            if (reply) {
                LOG.debug("Sending to: {} with body: {}", address, body);
                eventBus.send(address, body, new CamelReplyHandler(exchange, callback));
                return false;
            } else {
                if (pubSub) {
                    LOG.debug("Publishing to: {} with body: {}", address, body);
                    eventBus.publish(address, body);
                } else {
                    LOG.debug("Sending to: {} with body: {}", address, body);
                    eventBus.send(address, body);
                }
                callback.done(true);
                return true;
            }
        }

        exchange.setException(new InvalidPayloadRuntimeException(exchange, String.class));
        callback.done(true);
        return true;
    }

    private static final class CamelReplyHandler implements Handler<AsyncResult<Message<Object>>> {

        private final Exchange exchange;
        private final AsyncCallback callback;

        private CamelReplyHandler(Exchange exchange, AsyncCallback callback) {
            this.exchange = exchange;
            this.callback = callback;
        }

        @Override
        public void handle(AsyncResult<Message<Object>> event) {
            try {
                // preserve headers
                MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), false);
                Throwable e = event.cause();
                if (e != null) {
                    exchange.setException(e);
                } else {
                    exchange.getOut().setBody(event.result().body());
                }
            } finally {
                callback.done(false);
            }
        }

    }
}
