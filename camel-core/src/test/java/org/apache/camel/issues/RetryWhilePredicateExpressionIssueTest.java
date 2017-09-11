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
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;

import static org.apache.camel.builder.PredicateBuilder.and;
import static org.apache.camel.builder.PredicateBuilder.isNotNull;

/**
 *
 */
public class RetryWhilePredicateExpressionIssueTest extends ContextTestSupport {

    public void testRetryWhilePredicate() throws Exception {
        MyCoolDude dude = new MyCoolDude();
        template.sendBodyAndHeader("direct:start", dude, "foo", 123);

        assertEquals(3 + 1, dude.getCounter());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(IllegalArgumentException.class)
                    .handled(true)
                    .redeliveryDelay(0)
                    .retryWhile(new Predicate() {
                        @Override
                        public boolean matches(Exchange exchange) {
                            Predicate predicate = and(simple("${body.areWeCool} == 'no'"), isNotNull(header("foo")));
                            boolean answer = predicate.matches(exchange);
                            return answer;
                        }
                    });

                from("direct:start")
                    .throwException(new IllegalArgumentException("Forced"));
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
