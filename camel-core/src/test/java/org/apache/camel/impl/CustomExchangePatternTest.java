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
package org.apache.camel.impl;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

import java.util.List;

/**
 * @version $Revision: 1.1 $
 */
public class CustomExchangePatternTest extends ContextTestSupport {
    protected MockEndpoint resultEndpoint;

    public void testInOut() throws Exception {
        final ExchangePattern expectedPattern = ExchangePattern.InOut;

        template.send("direct:start", expectedPattern, new Processor() {
            public void process(Exchange exchange) throws Exception {
                assertEquals("MEP", expectedPattern, exchange.getPattern());
                exchange.getIn().setBody("<hello>world!</hello>");
            }
        });

        resultEndpoint.assertIsSatisfied();
        assertReceivedExpectedPattern(expectedPattern);
    }

    public void testInOnly() throws Exception {
        ExchangePattern expectedPattern = ExchangePattern.InOnly;

        template.send("direct:start", expectedPattern, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("<hello>world!</hello>");
            }
        });

        resultEndpoint.assertIsSatisfied();
        assertReceivedExpectedPattern(expectedPattern);
    }

    public void testInOutViaUri() throws Exception {
        final ExchangePattern expectedPattern = ExchangePattern.InOut;

        template.send("direct:start?exchangePattern=InOut", new Processor() {
            public void process(Exchange exchange) throws Exception {
                assertEquals("MEP", expectedPattern, exchange.getPattern());
                exchange.getIn().setBody("<hello>world!</hello>");
            }
        });

        resultEndpoint.assertIsSatisfied();
        assertReceivedExpectedPattern(expectedPattern);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);
    }

    protected void assertReceivedExpectedPattern(ExchangePattern expectedPattern) {
        List<Exchange> list = resultEndpoint.getReceivedExchanges();
        Exchange exchange = list.get(0);
        assertEquals("MEP", expectedPattern, exchange.getPattern());
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").to("mock:result");
                from("direct:start?exchangePattern=InOut").to("mock:result");
            }
        };
    }
}