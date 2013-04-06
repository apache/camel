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
package org.apache.camel.spring.interceptor;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.TestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class ContainerWideInterceptorTest extends TestSupport {

    private CamelContext camel1;
    private CamelContext camel2;
    private ApplicationContext ac;
    private ContainerWideInterceptor myInterceptor;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ac = new ClassPathXmlApplicationContext("/org/apache/camel/spring/interceptor/ContainerWideInterceptorTest.xml");
        camel1 = ac.getBean("camel1", CamelContext.class);
        camel2 = ac.getBean("camel2", CamelContext.class);
        myInterceptor = ac.getBean("myInterceptor", ContainerWideInterceptor.class);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        camel2.stop();
        camel1.stop();
    }

    public void testOne() throws Exception {
        int start = myInterceptor.getCount();

        MockEndpoint result = camel1.getEndpoint("mock:result", MockEndpoint.class);
        result.expectedBodiesReceived("Hello World");

        ProducerTemplate template = camel1.createProducerTemplate();
        template.start();
        template.sendBody("direct:one", "Hello World");
        template.stop();

        result.assertIsSatisfied();

        // lets see if the counter is +1 since last (has 1 step in the route)
        int delta = myInterceptor.getCount() - start;
        assertEquals("Should have been counted +1", 1, delta);
    }

    public void testTwo() throws Exception {
        int start = myInterceptor.getCount();

        MockEndpoint result = camel2.getEndpoint("mock:result", MockEndpoint.class);
        result.expectedBodiesReceived("Bye World");

        ProducerTemplate template = camel2.createProducerTemplate();
        template.start();
        template.sendBody("direct:two", "Bye World");
        template.stop();

        result.assertIsSatisfied();

        // lets see if the counter is +2 since last (has 2 steps in the route)
        int delta = myInterceptor.getCount() - start;
        assertEquals("Should have been counted +2", 2, delta);
    }

}
