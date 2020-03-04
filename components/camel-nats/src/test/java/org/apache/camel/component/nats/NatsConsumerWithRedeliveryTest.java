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

import org.apache.camel.EndpointInject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class NatsConsumerWithRedeliveryTest extends NatsTestSupport {

    private static final int REDELIVERY_COUNT = 2;

    @EndpointInject("mock:result")
    protected MockEndpoint mockResultEndpoint;

    @EndpointInject("mock:exception")
    private MockEndpoint exception;

    @Test
    public void testConsumer() throws Exception {
        mockResultEndpoint.setExpectedMessageCount(1);
        mockResultEndpoint.setAssertPeriod(1000);

        template.requestBody("direct:send", "test");
        template.requestBody("direct:send", "golang");

        exception.setExpectedMessageCount(1);

        exception.assertIsSatisfied();

        mockResultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).maximumRedeliveries(REDELIVERY_COUNT).retryAttemptedLogLevel(LoggingLevel.INFO).retriesExhaustedLogLevel(LoggingLevel.ERROR)
                    .redeliveryDelay(10).to("mock:exception").handled(true);

                from("direct:send").to("nats:test?flushConnection=true");

                from("nats:test?flushConnection=true").choice().when(exchange -> {
                    String s = exchange.getMessage().getBody(String.class);
                    if (s.contains("test")) {
                        return true;
                    }
                    return false;
                }).throwException(RuntimeCamelException.class, "Test for this").end().to(mockResultEndpoint);
            }
        };
    }
}
