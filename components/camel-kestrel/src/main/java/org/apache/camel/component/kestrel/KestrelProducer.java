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
package org.apache.camel.component.kestrel;

import net.spy.memcached.MemcachedClient;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;

/**
 * Camel producer for communication with a kestrel based queue.
 */
public class KestrelProducer extends DefaultProducer {
    private final KestrelEndpoint endpoint;
    private final MemcachedClient memcachedClient;

    public KestrelProducer(final KestrelEndpoint endpoint, final MemcachedClient memcachedClient) {
        super(endpoint);
        this.endpoint = endpoint;
        this.memcachedClient = memcachedClient;
    }

    public void process(Exchange exchange) throws Exception {
        String msg = exchange.getIn().getBody(String.class);
        String queue = endpoint.getQueue();
        if (msg != null) {
            try {
                log.debug("Sending to: {} message: {}", queue, msg);
                memcachedClient.set(queue, 0, msg);
            } catch (Exception e) {
                throw new CamelExchangeException("Error sending to: " + queue, exchange, e);
            }
        } else {
            log.debug("No message body to send to: " + queue);
        }
    }
}
