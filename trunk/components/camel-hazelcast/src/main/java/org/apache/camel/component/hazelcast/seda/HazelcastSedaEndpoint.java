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

import java.util.concurrent.BlockingQueue;

import com.hazelcast.core.Hazelcast;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.hazelcast.HazelcastComponent;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Hazelcast SEDA {@link Endpoint} implementation.
 */
public class HazelcastSedaEndpoint extends DefaultEndpoint {

    private final BlockingQueue queue;
    private final HazelcastSedaConfiguration configuration;

    public HazelcastSedaEndpoint(final String uri, final HazelcastComponent component, final HazelcastSedaConfiguration configuration) {
        super(uri, component);
        this.queue = Hazelcast.getQueue(configuration.getQueueName());
        this.configuration = configuration;
        if (ObjectHelper.isEmpty(configuration.getQueueName())) {
            throw new IllegalArgumentException("Queue name is missing.");
        }
    }

    public Producer createProducer() throws Exception {
        return new HazelcastSedaProducer(this, getQueue());
    }

    public Consumer createConsumer(final Processor processor) throws Exception {
        return new HazelcastSedaConsumer(this, processor);
    }

    public BlockingQueue getQueue() {
        return queue;
    }

    public HazelcastSedaConfiguration getConfiguration() {
        return configuration;
    }

    public boolean isSingleton() {
        return true;
    }

}
