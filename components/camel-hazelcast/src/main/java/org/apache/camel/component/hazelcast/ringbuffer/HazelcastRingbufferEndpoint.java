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
import org.apache.camel.spi.UriEndpoint;

import static org.apache.camel.component.hazelcast.HazelcastConstants.SCHEME_RING_BUFFER;

/**
 * Perform operations on <a href="http://www.hazelcast.com/">Hazelcast</a> distributed ringbuffer.
 */
@UriEndpoint(firstVersion = "2.16.0", scheme = SCHEME_RING_BUFFER, title = "Hazelcast Ringbuffer",
             syntax = "hazelcast-ringbuffer:cacheName", producerOnly = true, category = { Category.CACHE, Category.CLUSTERING },
             headersClass = HazelcastConstants.class)
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
