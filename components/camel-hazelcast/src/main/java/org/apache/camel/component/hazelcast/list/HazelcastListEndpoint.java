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
package org.apache.camel.component.hazelcast.list;

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

import static org.apache.camel.component.hazelcast.HazelcastConstants.SCHEME_LIST;

/**
 * Perform operations on <a href="http://www.hazelcast.com/">Hazelcast</a> distributed list.
 */
@UriEndpoint(firstVersion = "2.7.0", scheme = SCHEME_LIST, title = "Hazelcast List", syntax = "hazelcast-list:cacheName",
             category = { Category.CACHE, Category.CLUSTERING }, headersClass = HazelcastConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.POJO, javaType = "com.hazelcast.collection.ItemEvent",
                                   description = "The Hazelcast ItemEvent of an item added to or removed from the "
                                                 + "list, not the item: ${body.item} is the item, and the "
                                                 + "CamelHazelcastListenerAction header is added or removed"),
           producerIn = @BodyType(value = {
                   Carrier.TEXT, Carrier.BYTES, Carrier.FILE, Carrier.VALUE, Carrier.MAP, Carrier.LIST, Carrier.POJO },
                                  description = "The item that add and setValue store, at the index in the "
                                                + "CamelHazelcastObjectIndex header when set, and that removeValue "
                                                + "removes when the header is not set: an object Hazelcast can "
                                                + "serialize, so a stream or no body fails. A Collection for "
                                                + "addAll, removeAll and retainAll. The other operations do not use "
                                                + "the body"),
           producerOut = @BodyType(value = Carrier.UNKNOWN, byOption = {
                   @BodyType.When(option = "defaultOperation", value = "add", carriers = Carrier.UNCHANGED),
                   @BodyType.When(option = "defaultOperation", value = "setValue", carriers = Carrier.UNCHANGED),
                   @BodyType.When(option = "defaultOperation", value = "removeValue", carriers = Carrier.UNCHANGED),
                   @BodyType.When(option = "defaultOperation", value = "clear", carriers = Carrier.UNCHANGED),
                   @BodyType.When(option = "defaultOperation", value = "addAll", carriers = Carrier.UNCHANGED),
                   @BodyType.When(option = "defaultOperation", value = "removeAll", carriers = Carrier.UNCHANGED),
                   @BodyType.When(option = "defaultOperation", value = "retainAll", carriers = Carrier.UNCHANGED),
                   @BodyType.When(option = "defaultOperation", value = "getAll", carriers = Carrier.LIST) },
                                   description = "By the operation, which the CamelHazelcastOperationType header "
                                                 + "sets, or else the defaultOperation option (without either the "
                                                 + "producer fails): the item at the index in the "
                                                 + "CamelHazelcastObjectIndex header for get, which needs the "
                                                 + "header, and the Hazelcast list itself for getAll: a live "
                                                 + "distributed List, not a copy. The other operations keep the body"))
public class HazelcastListEndpoint extends HazelcastDefaultEndpoint {

    public HazelcastListEndpoint(HazelcastInstance hazelcastInstance, String endpointUri, Component component,
                                 String cacheName) {
        super(hazelcastInstance, endpointUri, component, cacheName);
        setCommand(HazelcastCommand.list);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        HazelcastListConsumer answer = new HazelcastListConsumer(hazelcastInstance, this, processor, cacheName);
        configureConsumer(answer);
        return answer;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new HazelcastListProducer(hazelcastInstance, this, cacheName);
    }

}
