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
package org.apache.camel.component.sjms2.consumer;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms2.support.Jms2TestSupport;
import org.junit.Test;

public class InOnlyTopicSharedConsumerTest extends Jms2TestSupport {

    private static final String TEST_DESTINATION_NAME = "sjms2:topic:in.only.topic.consumer.test";

    @Override
    protected boolean useJmx() {
        return false;
    }

    @Test
    public void testSynchronous() throws Exception {
        final String expectedBody = "Hello World";
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello World");

        MockEndpoint mock2 = getMockEndpoint("mock:result2");
        mock2.expectedMessageCount(1);
        mock2.expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", expectedBody);

        mock.assertIsSatisfied();
        mock2.assertIsSatisfied();
    }

    /**
     * @see org.apache.camel.test.junit4.CamelTestSupport#createRouteBuilder()
     *
     * @return
     * @throws Exception
     */
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to(TEST_DESTINATION_NAME);
                from(TEST_DESTINATION_NAME)
                        .to("log:test.log.1?showBody=true", "mock:result");

                from(TEST_DESTINATION_NAME + "?subscriptionId=sharedTest&shared=true")
                        .to("log:test.log.1?showBody=true", "mock:result2");

                from(TEST_DESTINATION_NAME + "?subscriptionId=sharedTest&shared=true")
                        .to("log:test.log.1?showBody=true", "mock:result2");
            }
        };
    }
}