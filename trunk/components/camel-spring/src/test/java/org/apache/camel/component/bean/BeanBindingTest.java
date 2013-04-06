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
package org.apache.camel.component.bean;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringTestSupport;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Bean binding unit test.
 */
public class BeanBindingTest extends SpringTestSupport {

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/bean/beanBindingTest.xml");
    }

    public void testBeanBindingUsingBeanExpression() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(2);
        result.expectedBodiesReceived("Bye Claus", "Bye James");
        result.message(0).header("count").isEqualTo(1);
        result.message(1).header("count").isEqualTo(2);

        template.sendBody("direct:startBeanExpression", "Claus");
        template.sendBody("direct:startBeanExpression", "James");

        assertMockEndpointsSatisfied();
    }

    public void testBeanBindingUsingConstantExpression() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(2);
        result.expectedBodiesReceived("Bye Claus", "Bye James");
        result.message(0).header("count").isEqualTo(5);
        result.message(1).header("count").isEqualTo(5);

        template.sendBody("direct:startConstantExpression", "Claus");
        template.sendBody("direct:startConstantExpression", "James");

        assertMockEndpointsSatisfied();
    }

    public void testBeanBindingUsingHeaderExpression() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(2);
        result.expectedBodiesReceived("Bye Claus", "Bye James");
        result.message(0).header("count").isEqualTo(1);
        result.message(1).header("count").isEqualTo(2);

        template.sendBodyAndHeader("direct:startHeaderExpression", "Claus", "number", "1");
        template.sendBodyAndHeader("direct:startHeaderExpression", "James", "number", "2");

        assertMockEndpointsSatisfied();
    }

    public void testBeanBindingUsingManyExpression() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(2);
        result.expectedBodiesReceived("Bye Claus", "Bye James");
        result.message(0).header("count").isEqualTo(5 * 3);
        result.message(1).header("count").isEqualTo(5 * 4);

        template.sendBodyAndHeader("direct:startMany", "Claus", "number", "3");
        template.sendBodyAndHeader("direct:startMany", "James", "number", "4");

        assertMockEndpointsSatisfied();
    }

}
