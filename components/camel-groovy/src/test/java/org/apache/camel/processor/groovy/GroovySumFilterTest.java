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
package org.apache.camel.processor.groovy;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class GroovySumFilterTest extends CamelSpringTestSupport {

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/processor/groovy/groovySumFilter.xml");
    }

    @Test
    public void testSendMatchingMessage() throws Exception {
        getMockEndpoint("mock:high").expectedMessageCount(1);
        getMockEndpoint("mock:low").expectedMessageCount(0);

        List<Order> orders = new ArrayList<>();
        orders.add(new Order("Camel in Action", 50));
        orders.add(new Order("ActiveMQ in Action", 40));
        orders.add(new Order("Spring in Action", 60));

        template.sendBody("direct:start", orders);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendNotMatchingMessage() throws Exception {
        getMockEndpoint("mock:high").expectedMessageCount(0);
        getMockEndpoint("mock:low").expectedMessageCount(1);

        List<Order> orders = new ArrayList<>();
        orders.add(new Order("Camel in Action", 50));
        orders.add(new Order("ActiveMQ in Action", 40));

        template.sendBody("direct:start", orders);

        assertMockEndpointsSatisfied();
    }

}