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
package org.apache.camel.component.jms;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JmsInOutFixedReplyQueueTimeoutTest extends AbstractJMSTest {

    protected final String componentName = "activemq";

    @Test
    public void testOk() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye Camel");

        String out = template.requestBody("direct:JmsInOutFixedReplyQueueTimeoutTest", "Camel", String.class);
        assertEquals("Bye Camel", out);

        assertMockEndpointsSatisfied(30, TimeUnit.SECONDS);
    }

    @Test
    public void testTimeout() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        Exception ex = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:JmsInOutFixedReplyQueueTimeoutTest", "World", String.class),
                "Should have thrown exception");

        assertIsInstanceOf(ExchangeTimedOutException.class, ex.getCause());
        assertMockEndpointsSatisfied(30, TimeUnit.SECONDS);
    }

    @Override
    public String getComponentName() {
        return componentName;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:JmsInOutFixedReplyQueueTimeoutTest")
                        .to(ExchangePattern.InOut,
                                "activemq:queue:JmsInOutFixedReplyQueueTimeoutTest?replyTo=queue:JmsInOutFixedReplyQueueTimeoutTestReply&requestTimeout=2000")
                        .to("mock:result");

                from("activemq:queue:JmsInOutFixedReplyQueueTimeoutTest")
                        .choice()
                            .when(body().isEqualTo("World"))
                                .log("Sleeping for 4 sec to force a timeout")
                                .delay(Duration.ofSeconds(4).toMillis()).
                            endChoice().end()
                        .transform(body().prepend("Bye ")).to("log:reply");
            }
        };
    }

}
