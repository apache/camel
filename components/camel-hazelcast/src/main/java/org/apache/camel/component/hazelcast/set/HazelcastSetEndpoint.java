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
package org.apache.camel.component.hazelcast.set;

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

import static org.apache.camel.component.hazelcast.HazelcastConstants.SCHEME_SET;

/**
 * Perform operations on <a href="http://www.hazelcast.com/">Hazelcast</a> distributed set.
 */
@UriEndpoint(firstVersion = "2.7.0", scheme = SCHEME_SET, title = "Hazelcast Set", syntax = "hazelcast-set:cacheName",
             category = { Category.CACHE, Category.CLUSTERING }, headersClass = HazelcastConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.POJO, javaType = "com.hazelcast.collection.ItemEvent",
                                   description = "The Hazelcast ItemEvent of an item added to or removed from the "
                                                 + "set, not the item: ${body.item} is the item, and the "
                                                 + "CamelHazelcastListenerAction header is added or removed"),
           producerIn = @BodyType(value = {
                   Carrier.TEXT, Carrier.BYTES, Carrier.FILE, Carrier.VALUE, Carrier.MAP, Carrier.LIST, Carrier.POJO },
                                  description = "The item that add adds and removeValue removes: an object "
                                                + "Hazelcast can serialize, so a stream or no body fails. A "
                                                + "Collection for addAll, removeAll and retainAll. clear and getAll "
                                                + "do not use the body"),
           producerOut = @BodyType(value = Carrier.UNCHANGED, byOption = {
                   @BodyType.When(option = "defaultOperation", value = "getAll", carriers = Carrier.LIST) },
                                   description = "The operations that change the set keep the body. getAll gives "
                                                 + "the Hazelcast set itself: a live distributed Set, not a copy. "
                                                 + "The operation comes from the CamelHazelcastOperationType header, "
                                                 + "or else the defaultOperation option: without either the producer "
                                                 + "fails"))
public class HazelcastSetEndpoint extends HazelcastDefaultEndpoint {

    public HazelcastSetEndpoint(HazelcastInstance hazelcastInstance, String endpointUri, Component component,
                                String cacheName) {
        super(hazelcastInstance, endpointUri, component, cacheName);
        setCommand(HazelcastCommand.set);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        HazelcastSetConsumer answer = new HazelcastSetConsumer(hazelcastInstance, this, processor, cacheName);
        configureConsumer(answer);
        return answer;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new HazelcastSetProducer(hazelcastInstance, this, cacheName);
    }

}
