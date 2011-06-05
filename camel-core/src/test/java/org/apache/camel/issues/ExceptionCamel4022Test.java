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

/**
 * @version 
 */
public class ExceptionCamel4022Test extends ContextTestSupport {

    public void testExceptionWithFatalException() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:intermediate").expectedMessageCount(0);
        getMockEndpoint("mock:onexception").expectedMessageCount(0);
        // TODO: CAMEL-4022 should not go into DLC
        //getMockEndpoint("mock:dlc").expectedMessageCount(0);

        try {
            // TODO: if sending to direct:intermediate then it works as expected
            template.sendBody("direct:start", "<body/>");
            // TODO: should fail
            // fail("Should throw an exception");
        } catch (Exception e) {
            IllegalArgumentException cause = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Forced by unit test", cause.getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    public static class MyExceptionThrower implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            throw new IllegalArgumentException("Forced by unit test");
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        final Processor exceptionThrower = new MyExceptionThrower();

        return new RouteBuilder() {
            public void configure() {
                // DLC
                errorHandler(deadLetterChannel("mock:dlc").redeliveryDelay(0).maximumRedeliveries(3));

                // onException that does NOT handle the exception
                onException(IllegalArgumentException.class)
                    .process(exceptionThrower)
                    .to("mock:onexception");

                // route
                from("direct:start")
                    .to("direct:intermediate")
                    .to("mock:result2");

                from("direct:intermediate")
                    .setBody(constant("<some-value/>"))
                    .process(exceptionThrower)
                    .to("mock:intermediate");
            }
        };
    }
}

