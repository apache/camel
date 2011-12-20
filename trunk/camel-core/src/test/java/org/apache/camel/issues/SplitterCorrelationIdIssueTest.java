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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class SplitterCorrelationIdIssueTest extends ContextTestSupport {

    public void testSplitCorrelationId() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedMessageCount(3);

        Exchange exchange = template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("A,B,C");
            }
        });

        assertMockEndpointsSatisfied();

        // match that all exchange id is unique
        String parent = exchange.getExchangeId();
        String split1 = mock.getReceivedExchanges().get(0).getExchangeId();
        String split2 = mock.getReceivedExchanges().get(1).getExchangeId();
        String split3 = mock.getReceivedExchanges().get(2).getExchangeId();
        assertNotSame(parent, split1);
        assertNotSame(parent, split2);
        assertNotSame(parent, split3);
        assertNotSame(split1, split2);
        assertNotSame(split2, split3);
        assertNotSame(split3, split1);

        // match correlation id from split -> parent
        String corr1 = mock.getReceivedExchanges().get(0).getProperty(Exchange.CORRELATION_ID, String.class);
        String corr2 = mock.getReceivedExchanges().get(1).getProperty(Exchange.CORRELATION_ID, String.class);
        String corr3 = mock.getReceivedExchanges().get(2).getProperty(Exchange.CORRELATION_ID, String.class);
        assertEquals(parent, corr1);
        assertEquals(parent, corr2);
        assertEquals(parent, corr3);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .split(body().tokenize(","))
                        .to("mock:split");
            }
        };
    }
}
