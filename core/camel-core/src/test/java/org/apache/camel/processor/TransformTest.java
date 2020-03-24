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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

public class TransformTest extends ContextTestSupport {
    protected MockEndpoint resultEndpoint;

    @Test
    public void testSendingAMessageUsingMulticastReceivesItsOwnExchange() throws Exception {
        resultEndpoint.expectedBodiesReceived("Hello World!");

        sendBody("direct:start", "Hello");

        resultEndpoint.assertIsSatisfied();
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        resultEndpoint = getMockEndpoint("mock:result");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                /*tag::example[]*/from("direct:start").process(new Processor() {
                    public void process(Exchange exchange) {
                        Message in = exchange.getIn();
                        in.setBody(in.getBody(String.class) + " World!");
                    }
                }).to("mock:result"); /*end::example[]*/
            }
        };
    }
}
