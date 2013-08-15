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
import org.apache.camel.InvalidPayloadRuntimeException;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

/**
 */
public class VertxProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(VertxProducer.class);
    private final VertxEndpoint endpoint;

    public VertxProducer(VertxEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public void process(Exchange exchange) throws Exception {
        EventBus eventBus = endpoint.getEventBus();
        String address = endpoint.getAddress();

        Message in = exchange.getIn();

        JsonObject jsonObject = in.getBody(JsonObject.class);
        if (jsonObject != null) {
            eventBus.publish(address, jsonObject);
            return;
        }

        String text = in.getBody(String.class);
        if (text != null) {
            eventBus.publish(address, new JsonObject(text));
            return;
        }
        JsonArray jsonArray = in.getBody(JsonArray.class);
        if (jsonArray != null) {
            eventBus.publish(address, jsonArray);
            return;
        }
        throw new InvalidPayloadRuntimeException(exchange, String.class);
    }
}
