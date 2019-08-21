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
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * Unit test for bean invocation that throws an exception
 */
public class BeanInvocationThrowsExceptionTest extends ContextTestSupport {

    // START SNIPPET: e1
    @Test
    public void testBeanInvocation() {
        int result = (Integer)template.sendBody("direct:input", ExchangePattern.InOut, "Hello London");
        assertEquals(1, result);
    }

    @Test
    public void testBeanInvocationFailure() {
        // must create an exchange to get the result as an exchange where we can
        // get the caused exception
        Exchange exchange = getMandatoryEndpoint("direct:input").createExchange(ExchangePattern.InOut);
        exchange.getIn().setBody("Hello Paris");

        Exchange out = template.send("direct:input", exchange);
        assertTrue("Should be failed", out.isFailed());
        assertTrue("Should be IllegalArgumentException", out.getException() instanceof IllegalArgumentException);
        assertEquals("Forced exception", out.getException().getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:input").bean(new ExceptionBean());
            }
        };
    }

    public static class ExceptionBean {
        public int doSomething(String request) throws Exception {
            if (request.equals("Hello London")) {
                return 1;
            } else {
                throw new IllegalArgumentException("Forced exception");
            }
        }
    }
    // END SNIPPET: e1

}
