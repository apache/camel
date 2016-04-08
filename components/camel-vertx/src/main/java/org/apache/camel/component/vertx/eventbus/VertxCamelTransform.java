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
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ExchangeHelper;

public class VertxCamelTransform extends ServiceSupport implements Handler<Message<Exchange>> {

    private final CamelContext camelContext;
    private final Vertx vertx;
    private final String id;
    private MessageConsumer<Exchange> consumer;
    private final DeliveryOptions options;

    public VertxCamelTransform(CamelContext camelContext, Vertx vertx, String id) {
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
        Expression expression = (Expression) exchange.removeProperty("CamelVertxExpression");
        // TODO: execute blocking
        transform(exchange, expression);
        // signal we are done
        event.reply(exchange, options);
    }

    private void transform(Exchange exchange, Expression expression) {
        Object newBody = expression.evaluate(exchange, Object.class);

        if (exchange.getException() != null) {
            // the expression threw an exception so we should break-out
            return;
        }

        boolean out = exchange.hasOut();
        org.apache.camel.Message old = out ? exchange.getOut() : exchange.getIn();

        // create a new message container so we do not drag specialized message objects along
        // but that is only needed if the old message is a specialized message
        boolean copyNeeded = !(old.getClass().equals(DefaultMessage.class));

        if (copyNeeded) {
            org.apache.camel.Message msg = new DefaultMessage();
            msg.copyFrom(old);
            msg.setBody(newBody);

            // replace message on exchange (must set as OUT)
            ExchangeHelper.replaceMessage(exchange, msg, true);
        } else {
            // no copy needed so set replace value directly
            old.setBody(newBody);

            // but the message must be on OUT
            if (!exchange.hasOut()) {
                exchange.setOut(exchange.getIn());
            }
        }
    }

}
