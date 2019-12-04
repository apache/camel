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
package org.apache.camel.component.nats;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.Options.Builder;
import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class NatsConsumerWithConnectionLoadTest extends NatsTestSupport {
    
    @EndpointInject("mock:result")
    protected MockEndpoint mockResultEndpoint;
    
    @EndpointInject("mock:result1")
    protected MockEndpoint mockResultEndpoint1;
    
    private Connection connection;
    
    @BindToRegistry("connection")
    public Connection connection() throws Exception {
        Builder options = new Options.Builder();
        options.server("nats://" + getNatsBrokerUrl());
        connection = Nats.connect(options.build());
        return connection;
    }

    @Test
    public void testLoadConsumer() throws Exception {
        mockResultEndpoint.setExpectedMessageCount(100);
        mockResultEndpoint1.setExpectedMessageCount(0);
        Options options = new Options.Builder().server("nats://" + getNatsBrokerUrl()).build();
        Connection connection = Nats.connect(options);

        for (int i = 0; i < 100; i++) {
            connection.publish("test", ("test" + i).getBytes());
        }

        mockResultEndpoint.assertIsSatisfied();
        mockResultEndpoint1.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("nats:test?connection=#connection").to(mockResultEndpoint);
                from("nats:test1?connection=#connection").to(mockResultEndpoint1);
            }
        };
    }

}
