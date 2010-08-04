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

import javax.naming.Context;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.TimeoutAwareAggregationStrategy;
import org.apache.camel.util.jndi.JndiContext;

/**
 * @version $Revision$
 */
public class BeanRecipientListTimeoutTest extends ContextTestSupport {

    public void testBeanRecipientListParallelTimeout() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        // A will timeout so we only get B and C
        mock.expectedBodiesReceived("BC");

        template.sendBody("direct:start", "Hello");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected Context createJndiContext() throws Exception {
        JndiContext answer = new JndiContext();
        answer.bind("myBean", new MyBean());
        answer.bind("myStrategy", new MyAggregationStrategy());
        return answer;
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").beanRef("myBean", "route").to("mock:result");

                from("direct:a").delay(3000).setBody(constant("A"));

                from("direct:b").setBody(constant("B"));

                from("direct:c").delay(500).setBody(constant("C"));
            }
        };
    }

    public static class MyBean {

        @org.apache.camel.RecipientList(strategyRef = "myStrategy", parallelProcessing = true, timeout = 2000)
        public String[] route(String body) {
            return new String[] {"direct:a", "direct:b", "direct:c"};
        }
    }

    private class MyAggregationStrategy implements TimeoutAwareAggregationStrategy {

        public void timeout(Exchange oldExchange, int index, int total, long timeout) {
            assertEquals(2000, timeout);
            assertEquals(3, total);
            assertEquals(0, index);
        }

        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }

            String body = oldExchange.getIn().getBody(String.class);
            oldExchange.getIn().setBody(body + newExchange.getIn().getBody(String.class));
            return oldExchange;
        }
    }

}