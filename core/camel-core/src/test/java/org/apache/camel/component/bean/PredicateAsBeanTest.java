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
package org.apache.camel.component.bean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.BeanRouteTest;
import org.apache.camel.spi.Registry;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PredicateAsBeanTest extends ContextTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(BeanRouteTest.class);
    protected MyPredicate myPredicate = new MyPredicate();

    @Test
    public void testSendMessage() throws Exception {
        String expectedBody = "Wobble";

        template.sendBodyAndHeader("direct:in", expectedBody, "foo", "bar");

        assertEquals("bean body: " + myPredicate, expectedBody, myPredicate.body);
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry answer = super.createRegistry();
        answer.bind("myPredicate", myPredicate);
        return answer;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:in").to("bean:myPredicate");
            }
        };
    }

    public static class MyPredicate implements Predicate {
        public String body;

        @Override
        public boolean matches(Exchange exchange) {
            LOG.info("matches(exchange) called with: " + exchange);
            body = exchange.getIn().getBody(String.class);
            return (null != body) && (body.equals("Wobble"));
        }

    }
}
