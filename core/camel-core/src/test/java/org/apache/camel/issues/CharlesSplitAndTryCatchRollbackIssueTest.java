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

import org.apache.camel.CamelExchangeException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RollbackExchangeException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class CharlesSplitAndTryCatchRollbackIssueTest extends ContextTestSupport {

    @Test
    public void testSplitWithTryCatchAndRollbackOK() throws Exception {
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
    public void testSplitWithTryCatchAndRollbackILE() throws Exception {
        MockEndpoint split = getMockEndpoint("mock:split");
        MockEndpoint ile = getMockEndpoint("mock:ile");
        MockEndpoint exception = getMockEndpoint("mock:exception");

        split.expectedBodiesReceived("A", "B", "C");
        ile.expectedMessageCount(1);
        exception.expectedMessageCount(0);

        template.sendBody("direct:start", "A,B,Forced,C");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSplitWithTryCatchAndRollbackException() throws Exception {
        MockEndpoint split = getMockEndpoint("mock:split");
        MockEndpoint ile = getMockEndpoint("mock:ile");
        MockEndpoint exception = getMockEndpoint("mock:exception");

        split.expectedBodiesReceived("A", "B");
        ile.expectedMessageCount(0);
        exception.expectedMessageCount(1);

        try {
            template.sendBody("direct:start", "A,B,Kaboom,C");
            fail("Should thrown an exception");
        } catch (CamelExecutionException e) {
            CamelExchangeException ee = assertIsInstanceOf(CamelExchangeException.class, e.getCause());
            assertTrue(ee.getMessage().startsWith("Multicast processing failed for number 2."));
            RollbackExchangeException re = assertIsInstanceOf(RollbackExchangeException.class, ee.getCause());
            assertTrue(re.getMessage().startsWith("Intended rollback"));
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSplitWithTryCatchAndRollbacILEAndException() throws Exception {
        MockEndpoint split = getMockEndpoint("mock:split");
        MockEndpoint ile = getMockEndpoint("mock:ile");
        MockEndpoint exception = getMockEndpoint("mock:exception");

        split.expectedBodiesReceived("A", "B");
        ile.expectedMessageCount(1);
        exception.expectedMessageCount(1);

        try {
            template.sendBody("direct:start", "A,Forced,B,Kaboom,C");
            fail("Should thrown an exception");
        } catch (CamelExecutionException e) {
            CamelExchangeException ee = assertIsInstanceOf(CamelExchangeException.class, e.getCause());
            assertTrue(ee.getMessage().startsWith("Multicast processing failed for number 3."));
            RollbackExchangeException re = assertIsInstanceOf(RollbackExchangeException.class, ee.getCause());
            assertTrue(re.getMessage().startsWith("Intended rollback"));
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").split(body().tokenize(",")).stopOnException().doTry().process(new MyProcessor()).to("mock:split").doCatch(IllegalArgumentException.class)
                    .to("mock:ile").doCatch(Exception.class).to("mock:exception").rollback().end();
            }
        };
    }

    public static class MyProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            String body = exchange.getIn().getBody(String.class);
            if ("Forced".equals(body)) {
                throw new IllegalArgumentException("Forced");
            } else if ("Kaboom".equals(body)) {
                throw new Exception("Kaboom");
            }
        }
    }
}
