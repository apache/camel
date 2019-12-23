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
import org.junit.Test;

public class ExceptionCamel4022Test extends ContextTestSupport {

    @Test
    public void testExceptionWithFatalException() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:intermediate").expectedMessageCount(0);
        getMockEndpoint("mock:onexception").expectedMessageCount(0);
        getMockEndpoint("mock:dlc").expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "<body/>");
            fail("Should throw an exception");
        } catch (Exception e) {
            IllegalArgumentException cause = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Damn Again", cause.getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    public static class MyExceptionThrower implements Processor {

        private String msg;

        public MyExceptionThrower(String msg) {
            this.msg = msg;
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            throw new IllegalArgumentException(msg);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // DLC
                errorHandler(deadLetterChannel("mock:dlc").redeliveryDelay(0).maximumRedeliveries(3));

                // onException that does NOT handle the exception
                onException(Exception.class).logStackTrace(false).process(new MyExceptionThrower("Damn Again")).to("mock:onexception");

                // route
                from("direct:start").to("mock:a").to("direct:intermediate").to("mock:result2");

                // 2nd route
                from("direct:intermediate").to("mock:b").setBody(constant("<some-value/>")).process(new MyExceptionThrower("Damn")).to("mock:intermediate");
            }
        };
    }
}
