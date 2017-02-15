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
package org.apache.camel.component.kafka;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ObjectHelper;

public class KafkaComponent extends UriEndpointComponent {
    
    @Metadata(label = "common")
    private String brokers;

    @Metadata(label = "advanced")
    private ExecutorService workerPool;

    public KafkaComponent() {
        super(KafkaEndpoint.class);
    }

    public KafkaComponent(CamelContext context) {
        super(context, KafkaEndpoint.class);
    }

    @Override
    protected KafkaEndpoint createEndpoint(String uri, String remaining, Map<String, Object> params) throws Exception {
        KafkaEndpoint endpoint = new KafkaEndpoint(uri, this);

        if (ObjectHelper.isEmpty(remaining)) {
            throw new IllegalArgumentException("Topic must be configured on endpoint using syntax kafka:topic");
        }
        endpoint.getConfiguration().setTopic(remaining);
        endpoint.getConfiguration().setWorkerPool(getWorkerPool());

        // brokers can be configured on either component or endpoint level
        // and the consumer and produce is aware of this and act accordingly

        setProperties(endpoint.getConfiguration(), params);
        setProperties(endpoint, params);
        return endpoint;
    }

    public String getBrokers() {
        return brokers;
    }

    /**
     * URL of the Kafka brokers to use.
     * The format is host1:port1,host2:port2, and the list can be a subset of brokers or a VIP pointing to a subset of brokers.
     * <p/>
     * This option is known as <tt>bootstrap.servers</tt> in the Kafka documentation.
     */
    public void setBrokers(String brokers) {
        this.brokers = brokers;
    }

    public ExecutorService getWorkerPool() {
        return workerPool;
    }

    /**
     * To use a shared custom worker pool for continue routing {@link Exchange} after kafka server has acknowledge
     * the message that was sent to it from {@link KafkaProducer} using asynchronous non-blocking processing.
     * If using this option then you must handle the lifecycle of the thread pool to shut the pool down when no longer needed.
     */
    public void setWorkerPool(ExecutorService workerPool) {
        this.workerPool = workerPool;
    }

}
