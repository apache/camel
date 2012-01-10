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
package org.apache.camel.impl;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.FailedToCreateConsumerException;
import org.apache.camel.IsSingleton;
import org.apache.camel.PollingConsumer;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.LRUCache;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cache containing created {@link org.apache.camel.Consumer}.
 *
 * @version 
 */
public class ConsumerCache extends ServiceSupport {
    private static final transient Logger LOG = LoggerFactory.getLogger(ConsumerCache.class);
    private final CamelContext camelContext;
    private final Map<String, PollingConsumer> consumers;

    public ConsumerCache(CamelContext camelContext) {
        this(camelContext, CamelContextHelper.getMaximumCachePoolSize(camelContext));
    }

    public ConsumerCache(CamelContext camelContext, int maximumCacheSize) {
        this(camelContext, new LRUCache<String, PollingConsumer>(maximumCacheSize));
    }

    public ConsumerCache(CamelContext camelContext, Map<String, PollingConsumer> cache) {
        this.camelContext = camelContext;
        this.consumers = cache;
    }

    public synchronized PollingConsumer getConsumer(Endpoint endpoint) {
        String key = endpoint.getEndpointUri();
        PollingConsumer answer = consumers.get(key);
        if (answer == null) {
            try {
                answer = endpoint.createPollingConsumer();
                answer.start();
            } catch (Exception e) {
                throw new FailedToCreateConsumerException(endpoint, e);
            }

            boolean singleton = true;
            if (answer instanceof IsSingleton) {
                singleton = ((IsSingleton) answer).isSingleton();
            }

            if (singleton) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Adding to consumer cache with key: " + endpoint + " for consumer: " + answer);
                }
                consumers.put(key, answer);
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Consumer for endpoint: " + key + " is not singleton and thus not added to consumer cache");
                }
            }
        }
        return answer;
    }

    public Exchange receive(Endpoint endpoint) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("<<<< " + endpoint);
        }

        PollingConsumer consumer = getConsumer(endpoint);
        return consumer.receive();
    }

    public Exchange receive(Endpoint endpoint, long timeout) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("<<<< " + endpoint);
        }

        PollingConsumer consumer = getConsumer(endpoint);
        return consumer.receive(timeout);
    }

    public Exchange receiveNoWait(Endpoint endpoint) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("<<<< " + endpoint);
        }

        PollingConsumer consumer = getConsumer(endpoint);
        return consumer.receiveNoWait();
    }
    
    public CamelContext getCamelContext() {
        return camelContext;
    }

    protected void doStart() throws Exception {
        ServiceHelper.startServices(consumers);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(consumers);
        consumers.clear();
    }

    /**
     * Returns the current size of the consumer cache
     *
     * @return the current size
     */
    int size() {
        return consumers.size();
    }

}
