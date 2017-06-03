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
public class FilterNotMatchedTest extends ContextTestSupport {

    public void testSendMatchingMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).exchangeProperty(Exchange.FILTER_MATCHED).isEqualTo(true);

        getMockEndpoint("mock:end").message(0).exchangeProperty(Exchange.FILTER_MATCHED).isNotNull();
        getMockEndpoint("mock:end").message(0).exchangeProperty(Exchange.FILTER_MATCHED).isEqualTo(true);

        template.sendBodyAndHeader("direct:start", "<matched/>", "foo", "bar");

        assertMockEndpointsSatisfied();
    }

    public void testSendNotMatchingMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        getMockEndpoint("mock:end").message(0).exchangeProperty(Exchange.FILTER_MATCHED).isNotNull();
        getMockEndpoint("mock:end").message(0).exchangeProperty(Exchange.FILTER_MATCHED).isEqualTo(false);

        template.sendBodyAndHeader("direct:start", "<notMatched/>", "foo", "notMatchedHeaderValue");

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .filter(header("foo").isEqualTo("bar"))
                        .to("mock:result")
                    .end()
                    .to("mock:end");
            }
        };
    }

}
