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
package org.apache.camel.spring.processor;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import static org.apache.camel.spring.processor.SpringTestHelper.createSpringCamelContext;

/**
 * @version 
 */
public class SpringComplexBlockWithEndTest extends ContextTestSupport {

    public void testHello() throws Exception {
        getMockEndpoint("mock:hello").expectedMessageCount(1);
        getMockEndpoint("mock:bye").expectedMessageCount(0);
        getMockEndpoint("mock:otherwise").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testBye() throws Exception {
        getMockEndpoint("mock:hello").expectedMessageCount(0);
        getMockEndpoint("mock:bye").expectedMessageCount(1);
        getMockEndpoint("mock:otherwise").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();
    }

    public void testOther() throws Exception {
        getMockEndpoint("mock:hello").expectedMessageCount(0);
        getMockEndpoint("mock:bye").expectedMessageCount(0);
        getMockEndpoint("mock:otherwise").expectedMessageCount(1);
        getMockEndpoint("mock:trapped").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedBodiesReceived("Cowboys");
        getMockEndpoint("mock:split").expectedBodiesReceived("Hi The good", "Hi The ugly");

        template.sendBody("direct:start", "The good,The bad,The ugly");

        assertMockEndpointsSatisfied();
    }

    protected CamelContext createCamelContext() throws Exception {
        return createSpringCamelContext(this, "org/apache/camel/spring/processor/SpringComplexBlockWithEndTest.xml");
    }

    public static class SplitAggregate implements AggregationStrategy {

        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            newExchange.getOut().setBody("Cowboys");
            return newExchange;
        }

    }

}