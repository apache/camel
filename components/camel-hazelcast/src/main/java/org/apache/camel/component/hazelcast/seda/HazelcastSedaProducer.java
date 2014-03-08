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
package org.apache.camel.component.hazelcast.seda;

import java.io.Serializable;
import java.util.concurrent.BlockingQueue;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.component.hazelcast.HazelcastComponentHelper;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.impl.DefaultExchangeHolder;

/**
 * Implementation of Hazelcast SEDA {@link Producer} component. Just appends exchange body into a Hazelcast {@link BlockingQueue}.
 */
public class HazelcastSedaProducer extends DefaultAsyncProducer {

    private final transient BlockingQueue<Object> queue;

    public HazelcastSedaProducer(final HazelcastSedaEndpoint endpoint, final BlockingQueue<Object> hzlq) {
        super(endpoint);
        this.queue = hzlq;
    }

    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        checkAndStore(exchange);
        callback.done(true);
        return true;
    }

    private void checkAndStore(final Exchange exchange) {
        Object obj;
        Object body = exchange.getIn().getBody();

        final HazelcastSedaEndpoint endpoint = (HazelcastSedaEndpoint) this.getEndpoint();
        final HazelcastSedaConfiguration configuration = endpoint.getConfiguration();

        if (configuration.isTransferExchange()) {
            obj = DefaultExchangeHolder.marshal(exchange);
        } else {
            // in case body is not serializable convert to byte array
            if (!(body instanceof Serializable)) {
                obj = exchange.getIn().getBody(byte[].class);
            } else {
                obj = body;
            }
        }

        queue.add(obj);

        // finally copy headers
        HazelcastComponentHelper.copyHeaders(exchange);
    }

}
