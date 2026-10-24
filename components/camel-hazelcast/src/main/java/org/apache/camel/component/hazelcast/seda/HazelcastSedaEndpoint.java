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
package org.apache.camel.component.hazelcast.seda;

import java.util.concurrent.BlockingQueue;

import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.hazelcast.HazelcastCommand;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.component.hazelcast.HazelcastDefaultComponent;
import org.apache.camel.component.hazelcast.HazelcastDefaultEndpoint;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.hazelcast.HazelcastConstants.SCHEME_SEDA;

/**
 * Asynchronously send/receive Exchanges between Camel routes running on potentially distinct JVMs/hosts backed by
 * Hazelcast {@link BlockingQueue}.
 */
@UriEndpoint(firstVersion = "2.7.0", scheme = SCHEME_SEDA, title = "Hazelcast SEDA", syntax = "hazelcast-seda:cacheName",
             category = { Category.CACHE, Category.CLUSTERING }, headersClass = HazelcastConstants.class)
@BodyTypes(consumerOut = @BodyType(value = {
        Carrier.TEXT, Carrier.BYTES, Carrier.FILE, Carrier.VALUE, Carrier.MAP, Carrier.LIST, Carrier.POJO },
                                   description = "The body the producer put on the Hazelcast queue, as Hazelcast "
                                                 + "deserializes it: a body that is not Serializable, such as a "
                                                 + "stream, arrives as bytes. With transferExchange=true on the "
                                                 + "producer the headers and exchange properties come along"),
           producerIn = @BodyType(value = {
                   Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.VALUE, Carrier.MAP, Carrier.LIST,
                   Carrier.POJO },
                                  description = "A Serializable body goes on the queue as is, and another one "
                                                + "converted to bytes, so a stream works, but a POJO that does not "
                                                + "convert to bytes fails, as does no body"),
           producerOut = @BodyType(value = Carrier.UNCHANGED,
                                   description = "The producer puts the message on the queue and does not wait for "
                                                 + "the consumer, also for a message that expects a reply (InOut)"))
public class HazelcastSedaEndpoint extends HazelcastDefaultEndpoint {

    private final BlockingQueue<Object> queue;

    @UriParam
    private final HazelcastSedaConfiguration configuration;

    public HazelcastSedaEndpoint(final HazelcastInstance hazelcastInstance, final String uri,
                                 final HazelcastDefaultComponent component, final HazelcastSedaConfiguration configuration) {
        super(hazelcastInstance, uri, component);
        this.queue = hazelcastInstance.getQueue(configuration.getQueueName());
        this.configuration = configuration;
        if (ObjectHelper.isEmpty(configuration.getQueueName())) {
            throw new IllegalArgumentException("Queue name is missing.");
        }
        setCommand(HazelcastCommand.seda);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new HazelcastSedaProducer(this, getQueue());
    }

    @Override
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

}
