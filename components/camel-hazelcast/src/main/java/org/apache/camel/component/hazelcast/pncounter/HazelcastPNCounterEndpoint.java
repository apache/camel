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
package org.apache.camel.component.hazelcast.pncounter;

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

import static org.apache.camel.component.hazelcast.HazelcastConstants.SCHEME_PNCOUNTER;

/**
 * Increment, decrement, get, etc. operations on a Hazelcast PN Counter (CRDT counter).
 */
@UriEndpoint(firstVersion = "4.19.0", scheme = SCHEME_PNCOUNTER, title = "Hazelcast PN Counter",
             syntax = "hazelcast-pncounter:cacheName", producerOnly = true,
             category = { Category.CACHE, Category.CLUSTERING },
             headersClass = HazelcastConstants.class)
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  description = "Not used, except by getAndAdd, which adds the body, a number or "
                                                + "the text of one, to the counter: no body fails"),
           producerOut = @BodyType(value = Carrier.VALUE, javaType = "java.lang.Long", byOption = {
                   @BodyType.When(option = "defaultOperation", value = "destroy", carriers = Carrier.UNCHANGED) },
                                   description = "The value of the counter as a Long: after the change for "
                                                 + "increment and decrement, before it for getAndAdd, and the "
                                                 + "current value for get; destroy keeps the body. The operation "
                                                 + "comes from the CamelHazelcastOperationType header, or else the "
                                                 + "defaultOperation option: without either the producer fails"))
public class HazelcastPNCounterEndpoint extends HazelcastDefaultEndpoint {

    public HazelcastPNCounterEndpoint(HazelcastInstance hazelcastInstance, String uri, Component component,
                                      final String cacheName) {
        super(hazelcastInstance, uri, component, cacheName);
        setCommand(HazelcastCommand.pncounter);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot send messages to this endpoint: " + getEndpointUri());
    }

    @Override
    public Producer createProducer() throws Exception {
        return new HazelcastPNCounterProducer(hazelcastInstance, this, cacheName);
    }

}
