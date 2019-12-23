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
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 *
 */
public class RetryWhileSimpleExpressionIssueTest extends ContextTestSupport {

    @Test
    public void testRetryWhileSimple() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(1);
        getMockEndpoint("mock:error").message(0).body().isInstanceOf(MyCoolDude.class);

        MyCoolDude dude = new MyCoolDude();
        template.sendBody("direct:start", dude);

        assertMockEndpointsSatisfied();

        assertEquals(3 + 1, dude.getCounter());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(IllegalArgumentException.class).retryWhile(simple("${body.areWeCool} == 'no'")).redeliveryDelay(0).handled(true).to("mock:error");

                from("direct:start").throwException(new IllegalArgumentException("Forced"));
            }
        };
    }

    public static class MyCoolDude {

        private int counter;

        public String areWeCool() {
            if (counter++ < 3) {
                return "no";
            } else {
                return "yes";
            }
        }

        public int getCounter() {
            return counter;
        }
    }
}
