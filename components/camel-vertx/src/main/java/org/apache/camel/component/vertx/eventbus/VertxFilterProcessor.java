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

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.util.AsyncProcessorHelper;

public class VertxFilterProcessor implements AsyncProcessor {

    private final Vertx vertx;
    private final String id;
    private final Predicate predicate;
    private final DeliveryOptions options;
    private final List<Processor> children;

    public VertxFilterProcessor(Vertx vertx, String id, Predicate predicate, List<Processor> children) {
        this.vertx = vertx;
        this.id = id;
        this.predicate = predicate;
        this.children = children;
        this.options = new DeliveryOptions();
        this.options.setCodecName("camel");
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        exchange.setProperty("CamelVertxPredicate", predicate);
        exchange.setProperty("CamelVertxChildren", children);
        vertx.eventBus().send(VertxCamelFilter.class.getName(), exchange, options, (handler) -> {
            if (handler.failed()) {
                Throwable t = handler.cause();
                exchange.setException(t);
            }
            callback.done(false);
        });
        return false;
    }

}
