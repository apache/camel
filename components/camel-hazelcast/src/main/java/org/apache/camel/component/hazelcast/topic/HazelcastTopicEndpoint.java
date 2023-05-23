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
package org.apache.camel.component.hazelcast.topic;

import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.hazelcast.HazelcastCommand;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.component.hazelcast.HazelcastDefaultEndpoint;
import org.apache.camel.component.hazelcast.HazelcastOperation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

import static org.apache.camel.component.hazelcast.HazelcastConstants.SCHEME_TOPIC;

/**
 * Send and receive messages to/from <a href="http://www.hazelcast.com/">Hazelcast</a> distributed topic.
 */
@UriEndpoint(firstVersion = "2.15.0", scheme = SCHEME_TOPIC, title = "Hazelcast Topic",
             syntax = "hazelcast-topic:cacheName", category = { Category.CACHE, Category.CLUSTERING },
             headersClass = HazelcastConstants.class)
public class HazelcastTopicEndpoint extends HazelcastDefaultEndpoint implements MultipleConsumersSupport {

    @UriParam
    private final HazelcastTopicConfiguration configuration;

    public HazelcastTopicEndpoint(HazelcastInstance hazelcastInstance, String endpointUri, Component component,
                                  String cacheName, final HazelcastTopicConfiguration configuration) {
        super(hazelcastInstance, endpointUri, component, cacheName);
        this.configuration = configuration;
        setCommand(HazelcastCommand.topic);
        setDefaultOperation(HazelcastOperation.PUBLISH);
    }

    public HazelcastTopicConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        HazelcastTopicConsumer answer
                = new HazelcastTopicConsumer(hazelcastInstance, this, processor, cacheName, configuration.isReliable());
        configureConsumer(answer);
        return answer;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new HazelcastTopicProducer(hazelcastInstance, this, cacheName, configuration.isReliable());
    }

    @Override
    public boolean isMultipleConsumersSupported() {
        return true;
    }
}
