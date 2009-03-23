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
import org.apache.camel.Processor;
import org.apache.camel.RollbackExchangeException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision$
 */
public class RollbackTest extends ContextTestSupport {

    public void testOk() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.requestBody("direct:start", "ok");

        assertMockEndpointsSatisfied();
    }

    public void testRollback() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:dead");
        mock.expectedMessageCount(1);

        getMockEndpoint("mock:rollback").expectedMessageCount(2);

        try {
            template.requestBody("direct:start", "bad");
            fail("Should have thrown a RollbackExchangeException");
        } catch (RuntimeCamelException e) {
            assertTrue(e.getCause() instanceof RollbackExchangeException);
        }

        assertMockEndpointsSatisfied();
    }

    public void testRollbackWithExchange() throws Exception {
        getMockEndpoint("mock:dead").expectedMessageCount(1);

        MockEndpoint mock = getMockEndpoint("mock:rollback");
        mock.expectedMessageCount(2);

        Exchange out = template.request("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("bad");
            }
        });
        assertMockEndpointsSatisfied();

        assertNotNull(out.getException());
        assertIsInstanceOf(RollbackExchangeException.class, out.getException());
        assertEquals("Should be marked as rollback", true, out.isRollbackOnly());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(1).delay(0));

                from("direct:start")
                    .choice()
                        .when(body().isNotEqualTo("ok"))
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                assertFalse("Rollback flag should have been cleared on redelivery", exchange.isRollbackOnly());
                            }
                        })
                        .to("mock:rollback")
                        .rollback()
                    .otherwise()
                        .to("mock:result")
                    .end();
            }
        };
    }
}
