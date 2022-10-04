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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

/**
 * Unit test for using JMS as DLQ and to preserve the Exchange using transferExchange=true option
 */
public class JmsDeadLetterQueueUsingTransferExchangeTest extends AbstractJMSTest {

    protected String getUri() {
        return "activemq:queue:JmsDeadLetterQueueUsingTransferExchangeTest?transferExchange=true";
    }

    @Test
    public void testOk() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testKabom() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:JmsDeadLetterQueueUsingTransferExchangeTest.dead");
        mock.expectedBodiesReceived("Kabom");

        template.sendBody("direct:start", "Kabom");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel(getUri()).disableRedelivery());

                from("direct:start").process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    if ("Kabom".equals(body)) {
                        throw new IllegalArgumentException("Kabom");
                    }
                }).to("mock:result");

                from(getUri()).to("mock:JmsDeadLetterQueueUsingTransferExchangeTest.dead");
            }
        };
    }

}
