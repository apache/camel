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

import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.hazelcast.HazelcastCommand;
import org.apache.camel.component.hazelcast.HazelcastDefaultComponent;
import org.apache.camel.component.hazelcast.HazelcastDefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * The hazelcast-seda component is used to access <a href="http://www.hazelcast.com/">Hazelcast</a> {@link BlockingQueue}.
 */
@UriEndpoint(firstVersion = "2.7.0", scheme = "hazelcast-seda", title = "Hazelcast SEDA", syntax = "hazelcast-seda:cacheName", label = "cache,datagrid")
public class HazelcastSedaEndpoint extends HazelcastDefaultEndpoint {

    private final BlockingQueue<Object> queue;
    private final HazelcastSedaConfiguration configuration;

    public HazelcastSedaEndpoint(final HazelcastInstance hazelcastInstance, final String uri, final HazelcastDefaultComponent component, final HazelcastSedaConfiguration configuration) {
        super(hazelcastInstance, uri, component);
        this.queue = hazelcastInstance.getQueue(configuration.getQueueName());
        this.configuration = configuration;
        if (ObjectHelper.isEmpty(configuration.getQueueName())) {
            throw new IllegalArgumentException("Queue name is missing.");
        }
        setCommand(HazelcastCommand.seda);
    }

    public Producer createProducer() throws Exception {
        return new HazelcastSedaProducer(this, getQueue());
    }

    public Consumer createConsumer(final Processor processor) throws Exception {
        HazelcastSedaConsumer answer = new HazelcastSedaConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    public BlockingQueue<Object> getQueue() {
        return queue;
    }

    public HazelcastSedaConfiguration getConfiguration() {
        return configuration;
    }

    public boolean isSingleton() {
        return true;
    }

}
