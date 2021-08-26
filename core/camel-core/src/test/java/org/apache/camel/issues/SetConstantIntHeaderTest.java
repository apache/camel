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
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A user reported an issue where an header set in a Processor was using constant(500) was not correctly converted to an
 * Integer.
 * 
 * @author klease
 *
 */
class SetConstantIntHeaderTest extends ContextTestSupport {

    @Test
    void testSetHeaderInProcessor() throws InterruptedException {
        // This works before the fix because the method on the mock evaluates the expression in the header value
        getMockEndpoint("mock:result").expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, Integer.valueOf(500));
        template.sendBody("direct:start", "SetHeader");
        assertMockEndpointsSatisfied();
    }

    @Test
    void testSetHeaderInProcessorNoEval() throws InterruptedException {

        template.sendBody("direct:start", "SetHeader");
        // This fails before the fix because getHeader() does not evaluate the expression
        Object resultCode = getMockEndpoint("mock:result").getExchanges().get(0).getMessage()
                .getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
        assertNotNull(resultCode);
        assertTrue(resultCode instanceof Integer);
        assertEquals(500, ((Integer) resultCode).intValue());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setTracing(true);

                from("direct:start")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE,
                                        constant(500));
                            }
                        })
                        .to("mock:result");
            }
        };
    }

}
