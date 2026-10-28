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
package org.apache.camel.component.hazelcast.map;

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

import static org.apache.camel.component.hazelcast.HazelcastConstants.SCHEME_MAP;

/**
 * Perform operations on <a href="http://www.hazelcast.com/">Hazelcast</a> distributed map.
 */
@UriEndpoint(firstVersion = "2.7.0", scheme = SCHEME_MAP, title = "Hazelcast Map", syntax = "hazelcast-map:cacheName",
             category = { Category.CACHE, Category.CLUSTERING }, headersClass = HazelcastConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.UNKNOWN,
                                   description = "The value of the entry for an added or updated event, as any "
                                                 + "member of the cluster stored it, and no body for removed and "
                                                 + "evicted: the key is in the CamelHazelcastObjectId header and the "
                                                 + "event in CamelHazelcastListenerAction"),
           producerIn = @BodyType(value = {
                   Carrier.TEXT, Carrier.BYTES, Carrier.FILE, Carrier.VALUE, Carrier.MAP, Carrier.LIST, Carrier.POJO },
                                  description = "The value that put, putIfAbsent and update store under the key "
                                                + "in the CamelHazelcastObjectId header, and that containsValue "
                                                + "looks for: an object Hazelcast can serialize, so a stream fails "
                                                + "(convert it to bytes or text first), and no body fails. The "
                                                + "other operations do not use the body"),
           producerOut = @BodyType(value = Carrier.UNKNOWN, byOption = {
                   @BodyType.When(option = "defaultOperation", value = "put", carriers = Carrier.UNCHANGED),
                   @BodyType.When(option = "defaultOperation", value = "putIfAbsent", carriers = Carrier.UNCHANGED),
                   @BodyType.When(option = "defaultOperation", value = "update", carriers = Carrier.UNCHANGED),
                   @BodyType.When(option = "defaultOperation", value = "delete", carriers = Carrier.UNCHANGED),
                   @BodyType.When(option = "defaultOperation", value = "evict", carriers = Carrier.UNCHANGED),
                   @BodyType.When(option = "defaultOperation", value = "evictAll", carriers = Carrier.UNCHANGED),
                   @BodyType.When(option = "defaultOperation", value = "clear", carriers = Carrier.UNCHANGED),
                   @BodyType.When(option = "defaultOperation", value = "containsKey", carriers = Carrier.VALUE,
                                  javaType = "java.lang.Boolean"),
                   @BodyType.When(option = "defaultOperation", value = "containsValue", carriers = Carrier.VALUE,
                                  javaType = "java.lang.Boolean"),
                   @BodyType.When(option = "defaultOperation", value = "getAll", carriers = Carrier.MAP),
                   @BodyType.When(option = "defaultOperation", value = "getKeys", carriers = Carrier.LIST),
                   @BodyType.When(option = "defaultOperation", value = "query", carriers = Carrier.LIST) },
                                   description = "By the operation, which the CamelHazelcastOperationType header "
                                                 + "sets, or else the defaultOperation option (without either the "
                                                 + "producer fails): the value of the key for get, or null when the "
                                                 + "key is not in the map, a Map of the keys in the Set of the "
                                                 + "CamelHazelcastObjectId header to their values for getAll, the "
                                                 + "keys for getKeys and the values that match the SQL predicate of "
                                                 + "the CamelHazelcastQuery header for query as a Collection, and a "
                                                 + "Boolean for containsKey and containsValue. The other operations "
                                                 + "keep the body"))
public class HazelcastMapEndpoint extends HazelcastDefaultEndpoint {

    public HazelcastMapEndpoint(HazelcastInstance hazelcastInstance, String uri, String cacheName,
                                HazelcastDefaultComponent component) {
        super(hazelcastInstance, uri, component, cacheName);
        setCommand(HazelcastCommand.map);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        HazelcastMapConsumer answer = new HazelcastMapConsumer(hazelcastInstance, this, processor, cacheName);
        configureConsumer(answer);
        return answer;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new HazelcastMapProducer(hazelcastInstance, this, cacheName);
    }

}
