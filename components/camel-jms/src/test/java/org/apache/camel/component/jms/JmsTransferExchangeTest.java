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

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class JmsTransferExchangeTest extends AbstractJMSTest {

    protected String getUri() {
        return "activemq:queue:JmsTransferExchangeTest?transferExchange=true";
    }

    @Test
    public void testBodyOnly() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived("JMSDestination", "queue://JmsTransferExchangeTest");

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testBodyAndHeaderOnly() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived("foo", "cheese");
        mock.expectedHeaderReceived("JMSDestination", "queue://JmsTransferExchangeTest");

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "cheese");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testSendExchange() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived("foo", "cheese");
        mock.expectedPropertyReceived("bar", 123);
        mock.expectedHeaderReceived("JMSDestination", "queue://JmsTransferExchangeTest");

        template.send("direct:start", exchange -> {
            exchange.getIn().setBody("Hello World");
            exchange.getIn().setHeader("foo", "cheese");
            exchange.setProperty("bar", 123);
        });

        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);
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
                from("direct:start").to(getUri());
                from(getUri()).to("mock:result");
            }
        };
    }

}
