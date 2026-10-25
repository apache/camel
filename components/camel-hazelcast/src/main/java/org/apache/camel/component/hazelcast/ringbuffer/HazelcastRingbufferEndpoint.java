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
package org.apache.camel.component.hazelcast.ringbuffer;

import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.hazelcast.HazelcastCommand;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.component.hazelcast.HazelcastDefaultEndpoint;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.UriEndpoint;

import static org.apache.camel.component.hazelcast.HazelcastConstants.SCHEME_RING_BUFFER;

/**
 * Perform operations on <a href="http://www.hazelcast.com/">Hazelcast</a> distributed ringbuffer.
 */
@UriEndpoint(firstVersion = "2.16.0", scheme = SCHEME_RING_BUFFER, title = "Hazelcast Ringbuffer",
             syntax = "hazelcast-ringbuffer:cacheName", producerOnly = true, category = { Category.CACHE, Category.CLUSTERING },
             headersClass = HazelcastConstants.class)
@BodyTypes(producerIn = @BodyType(value = {
        Carrier.TEXT, Carrier.BYTES, Carrier.FILE, Carrier.VALUE, Carrier.MAP, Carrier.LIST, Carrier.POJO },
                                  description = "The item that add appends: an object Hazelcast can serialize, "
                                                + "so a stream or no body fails. The other operations do not use "
                                                + "the body"),
           producerOut = @BodyType(value = Carrier.UNKNOWN,
                                   byOption = {
                                           @BodyType.When(option = "defaultOperation", value = "add", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Long"),
                                           @BodyType.When(option = "defaultOperation", value = "capacity",
                                                          carriers = Carrier.VALUE, javaType = "java.lang.Long"),
                                           @BodyType.When(option = "defaultOperation", value = "remainingCapacity",
                                                          carriers = Carrier.VALUE, javaType = "java.lang.Long") },
                                   description = "By the operation, which the CamelHazelcastOperationType header "
                                                 + "sets, or else the defaultOperation option (without either the "
                                                 + "producer fails): the sequence of the added item as a Long for "
                                                 + "add, a Long for capacity and remainingCapacity, and the item at "
                                                 + "the head (readOnceHead) or the tail (readOnceTail) of the "
                                                 + "ringbuffer"))
public class HazelcastRingbufferEndpoint extends HazelcastDefaultEndpoint {

    public HazelcastRingbufferEndpoint(HazelcastInstance hazelcastInstance, String uri, Component component,
                                       final String cacheName) {
        super(hazelcastInstance, uri, component, cacheName);
        setCommand(HazelcastCommand.ringbuffer);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot send messages to this endpoint: " + getEndpointUri());
    }

    @Override
    public Producer createProducer() throws Exception {
        return new HazelcastRingbufferProducer(hazelcastInstance, this, cacheName);
    }

}
