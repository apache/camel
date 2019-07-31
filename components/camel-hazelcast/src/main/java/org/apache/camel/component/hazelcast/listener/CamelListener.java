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
package org.apache.camel.component.hazelcast.listener;

import org.apache.camel.Exchange;
import org.apache.camel.component.hazelcast.HazelcastComponentHelper;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.component.hazelcast.HazelcastDefaultConsumer;

public class CamelListener {

    private final String cacheName;
    private final HazelcastDefaultConsumer consumer;

    public CamelListener(HazelcastDefaultConsumer consumer, String cacheName) {
        this.cacheName = cacheName;
        this.consumer = consumer;
    }

    protected void sendExchange(String operation, Object key, Object value) {
        Exchange exchange = consumer.getEndpoint().createExchange();

        // set object to body
        exchange.getIn().setBody(value);

        // set headers
        if (key != null) {
            exchange.getIn().setHeader(HazelcastConstants.OBJECT_ID, key);
        }

        HazelcastComponentHelper.setListenerHeaders(exchange, HazelcastConstants.CACHE_LISTENER, operation, cacheName);

        try {
            consumer.getProcessor().process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }

        if (exchange.getException() != null) {
            consumer.getExceptionHandler().handleException(String.format("Error processing exchange for hazelcast consumer on object '%s' in cache '%s'.", key, cacheName), exchange,
                    exchange.getException());
        }
    }

    public String getCacheName() {
        return cacheName;
    }

    public HazelcastDefaultConsumer getConsumer() {
        return consumer;
    }

}
