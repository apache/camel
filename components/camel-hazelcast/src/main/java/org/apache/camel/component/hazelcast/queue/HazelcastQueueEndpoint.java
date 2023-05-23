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
package org.apache.camel.component.hazelcast.queue;

import java.util.concurrent.ExecutorService;

import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.hazelcast.HazelcastCommand;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.component.hazelcast.HazelcastDefaultEndpoint;
import org.apache.camel.component.hazelcast.HazelcastOperation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

import static org.apache.camel.component.hazelcast.HazelcastConstants.SCHEME_QUEUE;

/**
 * Perform operations on <a href="http://www.hazelcast.com/">Hazelcast</a> distributed queue.
 */
@UriEndpoint(firstVersion = "2.7.0", scheme = SCHEME_QUEUE, title = "Hazelcast Queue",
             syntax = "hazelcast-queue:cacheName", category = { Category.CACHE, Category.CLUSTERING, Category.MESSAGING },
             headersClass = HazelcastConstants.class)
public class HazelcastQueueEndpoint extends HazelcastDefaultEndpoint {

    @UriParam
    private final HazelcastQueueConfiguration configuration;

    public HazelcastQueueEndpoint(HazelcastInstance hazelcastInstance, String endpointUri, Component component,
                                  String cacheName, final HazelcastQueueConfiguration configuration) {
        super(hazelcastInstance, endpointUri, component, cacheName);
        this.configuration = configuration;
        setCommand(HazelcastCommand.queue);
        setDefaultOperation(HazelcastOperation.ADD);
    }

    public HazelcastQueueConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        HazelcastQueueConsumer answer
                = new HazelcastQueueConsumer(hazelcastInstance, this, processor, cacheName, configuration);
        configureConsumer(answer);
        return answer;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new HazelcastQueueProducer(hazelcastInstance, this, cacheName);
    }

    public ExecutorService createExecutor() {
        return getCamelContext().getExecutorServiceManager().newFixedThreadPool(this, "QueueConsumer",
                configuration.getPoolSize());
    }

}
