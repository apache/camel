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
package org.apache.camel.impl;

import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.cache.DefaultProducerCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * After the producer cache is purged, a send does not use the (stopped) producer that was used last.
 */
public class DefaultProducerCachePurgeTest extends ContextTestSupport {

    @Test
    public void testSendAfterPurge() throws Exception {
        context.addComponent("stoppable", new DefaultComponent() {
            @Override
            protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
                return new DefaultEndpoint(uri, this) {
                    @Override
                    public Producer createProducer() {
                        return new DefaultProducer(this) {
                            @Override
                            public void process(Exchange exchange) {
                                if (!isStarted()) {
                                    throw new IllegalStateException("Producer is stopped");
                                }
                            }
                        };
                    }

                    @Override
                    public Consumer createConsumer(Processor processor) {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        });
        Endpoint endpoint = context.getEndpoint("stoppable:a");

        DefaultProducerCache cache = new DefaultProducerCache(this, context, 0);
        cache.start();
        try {
            Exchange first = cache.send(endpoint, new DefaultExchange(context), null);
            assertNull(first.getException());

            cache.purge();

            Exchange second = cache.send(endpoint, new DefaultExchange(context), null);
            assertNull(second.getException(), "the stopped producer should not be used after the purge");
        } finally {
            cache.stop();
        }
    }
}
