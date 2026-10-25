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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

import static org.apache.camel.component.hazelcast.HazelcastConstants.SCHEME_QUEUE;

/**
 * Perform operations on <a href="http://www.hazelcast.com/">Hazelcast</a> distributed queue.
 */
@UriEndpoint(firstVersion = "2.7.0", scheme = SCHEME_QUEUE, title = "Hazelcast Queue",
             syntax = "hazelcast-queue:cacheName", category = { Category.CACHE, Category.CLUSTERING, Category.MESSAGING },
             headersClass = HazelcastConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.POJO, javaType = "com.hazelcast.collection.ItemEvent", byOption = {
        @BodyType.When(option = "queueConsumerMode", value = "poll", carriers = Carrier.UNKNOWN) },
                                   description = "In Listen mode, the default, the Hazelcast ItemEvent of an item "
                                                 + "added to or removed from the queue, not the item: ${body.item} "
                                                 + "is the item, and the CamelHazelcastListenerAction header is "
                                                 + "added or removed. In Poll mode the consumer takes the items off "
                                                 + "the queue, and the body is the item itself, without headers"),
           producerIn = @BodyType(value = {
                   Carrier.TEXT, Carrier.BYTES, Carrier.FILE, Carrier.VALUE, Carrier.MAP, Carrier.LIST, Carrier.POJO },
                                  description = "The item that add, the default, put and offer add to the queue, "
                                                + "and that removeValue removes (without a body it removes the "
                                                + "head): an object Hazelcast can serialize, so a stream or no body "
                                                + "fails. A Collection for removeAll and retainAll. The other "
                                                + "operations do not use the body"),
           producerOut = @BodyType(value = Carrier.UNCHANGED, byOption = {
                   @BodyType.When(option = "defaultOperation", value = "poll", carriers = Carrier.UNKNOWN),
                   @BodyType.When(option = "defaultOperation", value = "peek", carriers = Carrier.UNKNOWN),
                   @BodyType.When(option = "defaultOperation", value = "take", carriers = Carrier.UNKNOWN),
                   @BodyType.When(option = "defaultOperation", value = "remainingCapacity", carriers = Carrier.VALUE,
                                  javaType = "java.lang.Integer"),
                   @BodyType.When(option = "defaultOperation", value = "drainTo", carriers = Carrier.VALUE,
                                  javaType = "java.lang.Integer") },
                                   description = "Unchanged for add, the default, and the other operations that "
                                                 + "change the queue. poll and peek give the head of the queue, or "
                                                 + "null when it is empty, and take waits for one; remainingCapacity "
                                                 + "gives an Integer, and drainTo the number of items it moved into "
                                                 + "the Collection of the CamelHazelcastDrainToCollection header. "
                                                 + "The CamelHazelcastOperationType header overrides the "
                                                 + "defaultOperation option"))
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

    public ExecutorService createExecutor(Object source) {
        return getCamelContext().getExecutorServiceManager().newFixedThreadPool(source, "QueueConsumer",
                configuration.getPoolSize());
    }

}
