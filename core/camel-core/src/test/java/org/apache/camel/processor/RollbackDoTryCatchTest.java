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
import org.apache.camel.Processor;
import org.apache.camel.RollbackExchangeException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class RollbackDoTryCatchTest extends ContextTestSupport {

    @Test
    public void testOk() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:rollback").expectedMessageCount(0);
        getMockEndpoint("mock:doCatch").expectedMessageCount(0);

        template.requestBody("direct:start", "ok");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRollback() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:rollback").expectedMessageCount(1);
        getMockEndpoint("mock:doCatch").expectedMessageCount(1);

        try {
            template.requestBody("direct:start", "bad");
            fail("Should have thrown a RollbackExchangeException");
        } catch (RuntimeCamelException e) {
            boolean b = e.getCause() instanceof RollbackExchangeException;
            assertTrue(b);
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRollbackWithExchange() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:rollback").expectedMessageCount(1);
        getMockEndpoint("mock:doCatch").expectedMessageCount(1);

        Exchange out = template.request("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("bad");
            }
        });
        assertMockEndpointsSatisfied();

        assertNotNull(out.getException());
        assertIsInstanceOf(RollbackExchangeException.class, out.getException());
        assertTrue(out.isRollbackOnly(), "Should be marked as rollback");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .doTry()
                        .to("mock:doTry")
                        .to("direct:rollback")
                    .doCatch(Throwable.class)
                        .log("doCatch")
                        .to("mock:doCatch")
                    .end()
                    .to("mock:result");

                from("direct:rollback")
                        .choice().when(body().isNotEqualTo("ok")).to("mock:rollback")
                        .rollback("That do not work");

            }
        };
    }
}
