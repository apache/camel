/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.vertx.eventbus;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ServiceHelper;

public class VertxCamelProducer extends ServiceSupport implements Handler<Message<Exchange>> {

    private final CamelContext camelContext;
    private final ProducerTemplate template;
    private final Vertx vertx;
    private final String id;
    private MessageConsumer<Exchange> consumer;

    public VertxCamelProducer(CamelContext camelContext, Vertx vertx, String id) {
        this.camelContext = camelContext;
        this.template = camelContext.createProducerTemplate();
        this.vertx = vertx;
        this.id = id;
    }

    @Override
    protected void doStart() throws Exception {
        consumer = vertx.eventBus().localConsumer(id, this);
        ServiceHelper.startService(template);
    }

    @Override
    protected void doStop() throws Exception {
        if (consumer != null) {
            consumer.unregister();
        }
        ServiceHelper.stopService(template);
    }

    @Override
    public void handle(Message<Exchange> event) {
        Exchange exchange = event.body();
        String url = (String) exchange.removeProperty("CamelVertxUrl");
        // TODO: execute blocking
        template.send(url, exchange);
    }
}
