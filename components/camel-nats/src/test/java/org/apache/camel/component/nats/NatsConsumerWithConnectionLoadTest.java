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
package org.apache.camel.component.nats;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.Options.Builder;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

public class NatsConsumerWithConnectionLoadTest extends NatsTestSupport {
    
    @EndpointInject(uri = "mock:result")
    protected MockEndpoint mockResultEndpoint;
    
    @EndpointInject(uri = "mock:result1")
    protected MockEndpoint mockResultEndpoint1;
    
    private Connection connection;

    @Test
    public void testLoadConsumer() throws InterruptedException, IOException, TimeoutException {
        mockResultEndpoint.setExpectedMessageCount(100);
        mockResultEndpoint1.setExpectedMessageCount(0);
        Options options = new Options.Builder().server("nats://" + getNatsUrl()).build();
        Connection connection = Nats.connect(options);

        for (int i = 0; i < 100; i++) {
            connection.publish("test", ("test" + i).getBytes());
        }

        mockResultEndpoint.assertIsSatisfied();
        mockResultEndpoint1.assertIsSatisfied();
    }
    
    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        
        Builder options = new Options.Builder();
        options.server("nats://" + getNatsUrl());
        connection = Nats.connect(options.build());
        registry.bind("connection", connection);
        
        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("nats://thisismytest?topic=test&connection=#connection").to(mockResultEndpoint);
                from("nats://thisismytest?topic=test1&connection=#connection").to(mockResultEndpoint1);
            }
        };
    }

}
