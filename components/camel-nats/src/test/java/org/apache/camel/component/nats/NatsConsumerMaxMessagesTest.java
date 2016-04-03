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

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Require a running Nats server")
public class NatsConsumerMaxMessagesTest extends CamelTestSupport {

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint mockResultEndpoint;

    @Test
    public void testMaxConsumer() throws InterruptedException, IOException {
        mockResultEndpoint.expectedBodiesReceived("{Subject=test;Reply=null;Payload=<test>}", "{Subject=test;Reply=null;Payload=<test1>}", 
            "{Subject=test;Reply=null;Payload=<test2>}", "{Subject=test;Reply=null;Payload=<test3>}", "{Subject=test;Reply=null;Payload=<test4>}");
        mockResultEndpoint.setExpectedMessageCount(5);
        template.sendBody("direct:send", "test");
        template.sendBody("direct:send", "test1");
        template.sendBody("direct:send", "test2");
        template.sendBody("direct:send", "test3");
        template.sendBody("direct:send", "test4");
        template.sendBody("direct:send", "test5");
        template.sendBody("direct:send", "test6");
        template.sendBody("direct:send", "test7");
        template.sendBody("direct:send", "test8");
        template.sendBody("direct:send", "test9");
        template.sendBody("direct:send", "test10");

        mockResultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:send").to("nats://localhost:4222?topic=test");
                from("nats://localhost:4222?topic=test&maxMessages=5").to(mockResultEndpoint);
            }
        };
    }
}
