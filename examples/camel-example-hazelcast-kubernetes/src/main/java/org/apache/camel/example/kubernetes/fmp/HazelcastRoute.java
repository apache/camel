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
package org.apache.camel.example.kubernetes.fmp;

import java.util.UUID;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.GroupConfig;
import com.hazelcast.config.SSLConfig;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.component.hazelcast.HazelcastOperation;
import org.apache.camel.component.hazelcast.topic.HazelcastTopicComponent;

public class HazelcastRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        // setup hazelcast
        ClientConfig config = new ClientConfig();
        config.getNetworkConfig().addAddress("hazelcast");
        config.getNetworkConfig().setSSLConfig(new SSLConfig().setEnabled(false));
        config.setGroupConfig(new GroupConfig("someGroup"));
        HazelcastInstance instance = HazelcastClient.newHazelcastClient(config);

        // setup camel hazelcast
        HazelcastTopicComponent hazelcast = new HazelcastTopicComponent();
        hazelcast.setHazelcastInstance(instance);
        getContext().addComponent("hazelcast-topic", hazelcast);

        from("timer:foo?period=5000")
            .log("Producer side: Sending data to Hazelcast topic..")
            .process(new Processor() {
                @Override
                public void process(Exchange exchange) throws Exception {
                    exchange.getIn().setHeader(HazelcastConstants.OPERATION, HazelcastOperation.PUBLISH);
                    String payload = "Test " + UUID.randomUUID();
                    exchange.getIn().setBody(payload);
                }
            })
            .to("hazelcast-topic:foo");

        from("hazelcast-topic:foo")
            .log("Consumer side: Detected following action: $simple{in.header.CamelHazelcastListenerAction}");
    }

} 
