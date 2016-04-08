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

import java.util.List;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ServiceHelper;

public class VertxCamelFilter extends ServiceSupport implements Handler<Message<Exchange>> {

    private final CamelContext camelContext;
    private final Vertx vertx;
    private final String id;
    private MessageConsumer<Exchange> consumer;
    private final DeliveryOptions options;

    public VertxCamelFilter(CamelContext camelContext, Vertx vertx, String id) {
        this.camelContext = camelContext;
        this.vertx = vertx;
        this.id = id;
        this.options = new DeliveryOptions();
        this.options.setCodecName("camel");
    }

    @Override
    protected void doStart() throws Exception {
        consumer = vertx.eventBus().localConsumer(id, this);
    }

    @Override
    protected void doStop() throws Exception {
        if (consumer != null) {
            consumer.unregister();
        }
    }

    @Override
    public void handle(Message<Exchange> event) {
        Exchange exchange = event.body();
        Predicate predicate = (Predicate) exchange.removeProperty("CamelVertxPredicate");
        List<Processor> children = (List<Processor>) exchange.removeProperty("CamelVertxChildren");

        boolean matches = false;

        try {
            matches = matches(exchange, predicate);
        } catch (Exception e) {
            exchange.setException(e);
        }

        if (matches) {
            exchange.setProperty("CamelVerxReplyAddress", event.replyAddress());
            Processor child = children.get(0);
            try {
                child.process(exchange);
            } catch (Exception e) {
                // ignore
            }
        } else {
            // signal we are done
            event.reply(exchange, options);
        }
    }

    private boolean matches(Exchange exchange, Predicate predicate) {
        boolean matches = predicate.matches(exchange);

        // set property whether the filter matches or not
        exchange.setProperty(Exchange.FILTER_MATCHED, matches);

        return matches;
    }

}
