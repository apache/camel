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
package org.apache.camel.spring.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.spring.SpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringCustomPredicateTest extends SpringTestSupport {

    @Test
    public void testFilterMyPredicate() throws InterruptedException {
        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello Camel", "Secret Agent");
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello Camel", "Hello World", "Secret Agent");

        template.sendBody("direct:start", "Hello Camel");
        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Secret Agent");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/processor/SpringCustomPredicateTest.xml");
    }

    public static class MyPredicate implements Predicate {

        @Override
        public boolean matches(Exchange exchange) {
            String body = exchange.getIn().getBody(String.class);
            if (body.contains("Camel")) {
                return true;
            } else if (body.startsWith("Secret")) {
                return true;
            }

            return false;
        }
    }
}
