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
package org.apache.camel.component.seda;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

/**
 * Test for the virtualThreadPerTask mode of SEDA consumer
 */
public class ThreadPerTaskSedaConsumerTest extends ContextTestSupport {

    @Test
    public void testVirtualThreadPerTask() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(10);

        for (int i = 0; i < 10; i++) {
            template.sendBody("seda:test?virtualThreadPerTask=true", "Message " + i);
        }

        mock.assertIsSatisfied();
    }

    @Test
    public void testVirtualThreadPerTaskWithConcurrencyLimit() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:limited");
        mock.expectedMessageCount(5);

        for (int i = 0; i < 5; i++) {
            template.sendBody("seda:limited?virtualThreadPerTask=true&concurrentConsumers=2", "Message " + i);
        }

        mock.assertIsSatisfied();
    }

    @Test
    public void testVirtualThreadPerTaskHighThroughput() throws Exception {
        int messageCount = 100;
        MockEndpoint mock = getMockEndpoint("mock:throughput");
        mock.expectedMessageCount(messageCount);

        for (int i = 0; i < messageCount; i++) {
            template.sendBody("seda:throughput?virtualThreadPerTask=true", "Message " + i);
        }

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:test?virtualThreadPerTask=true")
                        .to("mock:result");

                from("seda:limited?virtualThreadPerTask=true&concurrentConsumers=2")
                        .to("mock:limited");

                from("seda:throughput?virtualThreadPerTask=true")
                        .to("mock:throughput");
            }
        };
    }
}
