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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class SplitterCompletedPropertyTest extends ContextTestSupport {

    public void testSplitterCompleted() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedBodiesReceived("Hello World", "Bye World", "Bye Moon");
        mock.message(0).exchangeProperty(Exchange.SPLIT_COMPLETE).isEqualTo(false);
        mock.message(1).exchangeProperty(Exchange.SPLIT_COMPLETE).isEqualTo(false);
        mock.message(2).exchangeProperty(Exchange.SPLIT_COMPLETE).isEqualTo(true);

        template.sendBody("direct:start", "Hello World,Bye World,Bye Moon");

        assertMockEndpointsSatisfied();
    }

    public void testSplitterStreamingCompleted() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedBodiesReceived("Hello World", "Bye World", "Bye Moon");
        mock.message(0).exchangeProperty(Exchange.SPLIT_COMPLETE).isEqualTo(false);
        mock.message(1).exchangeProperty(Exchange.SPLIT_COMPLETE).isEqualTo(false);
        mock.message(2).exchangeProperty(Exchange.SPLIT_COMPLETE).isEqualTo(true);

        template.sendBody("direct:streaming", "Hello World,Bye World,Bye Moon");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .split(body().tokenize(","))
                        .to("mock:split");

                from("direct:streaming")
                    .split(body().tokenize(",")).streaming()
                        .to("mock:split");
            }
        };
    }

}