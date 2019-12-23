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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeException;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class CharlesSplitUseBeanAsErrorHandlerIssueTest extends ContextTestSupport {

    private static String logged;

    @Test
    public void testSplitBeanErrorHandlerOK() throws Exception {
        MockEndpoint split = getMockEndpoint("mock:split");
        MockEndpoint ile = getMockEndpoint("mock:ile");
        MockEndpoint exception = getMockEndpoint("mock:exception");

        split.expectedBodiesReceived("A", "B", "C");
        ile.expectedMessageCount(0);
        exception.expectedMessageCount(0);

        template.sendBody("direct:start", "A,B,C");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSplitBeanErrorHandlerHandled() throws Exception {
        MockEndpoint split = getMockEndpoint("mock:split");
        MockEndpoint ile = getMockEndpoint("mock:ile");
        MockEndpoint exception = getMockEndpoint("mock:exception");

        split.expectedBodiesReceived("A", "B", "C");
        ile.expectedBodiesReceived("Handled Forced Cause by Damn ILE");
        exception.expectedMessageCount(0);

        template.sendBody("direct:start", "A,B,Forced,C");

        assertMockEndpointsSatisfied();

        assertEquals("Forced", logged);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").split(body().tokenize(",")).stopOnException().doTry().process(new MyProcessor()).to("mock:split").doCatch(IllegalArgumentException.class)
                    .bean(new MyLoggerBean()).bean(new MyErrorHandlerBean()).to("mock:ile").doCatch(Exception.class).to("mock:exception").rollback().end();
            }
        };
    }

    public static class MyLoggerBean {

        public void logError(String body) {
            logged = body;
        }

    }

    public static class MyErrorHandlerBean {

        public String handleError(String body, @ExchangeException Exception e) {
            return "Handled " + body + " Cause by " + e.getMessage();
        }

    }

    public static class MyProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            String body = exchange.getIn().getBody(String.class);
            if ("Forced".equals(body)) {
                throw new IllegalArgumentException("Damn ILE");
            } else if ("Kaboom".equals(body)) {
                throw new Exception("Kaboom");
            }
        }
    }

}
