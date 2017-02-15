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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;

public class KafkaComponent extends UriEndpointComponent {
    
    // Topic name validation as per Kafka documentation [a-zA-Z0-9\\._\\-] as of 0.10
    // hostname and port are extracted as per pattern. IP and hostname syntax is not validated using regex.
    
    static final Pattern SIMPLE_KAFKA_URI_PATTERN = Pattern.compile("([a-z0-9\\.]*)(:?)([0-9]*)/([a-zA-Z0-9\\._\\-]*)", Pattern.CASE_INSENSITIVE);
    
    static final String DEFAULT_PORT = "9092";

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
        
        Matcher matcher = SIMPLE_KAFKA_URI_PATTERN.matcher(remaining);
               
        if (matcher.matches()) {
            String hostName = matcher.group(1);          
            String port = matcher.group(3);
            String topic = matcher.group(4);
            
            if (port != null && port.length() == 0) {
                port = DEFAULT_PORT;
            }            
            endpoint.getConfiguration().setBrokers(hostName + ":" + port);
            endpoint.getConfiguration().setTopic(topic);
        } else {
            String brokers = remaining.split("\\?")[0];
            if (brokers != null) {
                endpoint.getConfiguration().setBrokers(brokers);
            }            
        }

        // configure component options before endpoint properties which can override from params
        endpoint.getConfiguration().setWorkerPool(workerPool);

        setProperties(endpoint.getConfiguration(), params);
        setProperties(endpoint, params);
        return endpoint;
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
