/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.vertx;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;

public class VertxConsumer extends DefaultConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(VertxConsumer.class);
    private final VertxEndpoint endpoint;

    private Handler<? extends Message> handler = new Handler<Message>() {
        public void handle(Message event) {
            onEventBusEvent(event);
        }
    };

    public VertxConsumer(VertxEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    protected void doStart() throws Exception {
        endpoint.getEventBus().registerHandler(endpoint.getAddress(), handler);
        super.doStart();

    }

    protected void doStop() throws Exception {
        endpoint.getEventBus().unregisterHandler(endpoint.getAddress(), handler);
        super.doStop();

    }

    protected void onEventBusEvent(Message event) {
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody(event.body());
        try {
            getProcessor().process(exchange);
        } catch (Exception e) {
            LOG.error("Failed to prcess message " + exchange);
        }
    }
}
