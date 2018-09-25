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
package org.apache.camel.spring.config;

import org.apache.camel.spring.SpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class SpringProduceInjectedSingletonBeanTest extends SpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/config/SpringProduceInjectedSingletonBeanTest.xml");
    }

    @Test
    public void testProduceInjectedOnce() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World", "Bye World");

        MyProduceBean bean = context.getRegistry().lookupByNameAndType("myProducerBean", MyProduceBean.class);

        bean.testDoSomething("Hello World");
        bean.testDoSomething("Bye World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testProduceInjectedTwice() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World", "Bye World");

        MyProduceBean bean = context.getRegistry().lookupByNameAndType("myProducerBean", MyProduceBean.class);

        bean.testDoSomething("Hello World");

        MyProduceBean bean2 = context.getRegistry().lookupByNameAndType("myProducerBean", MyProduceBean.class);
        bean2.testDoSomething("Bye World");

        assertMockEndpointsSatisfied();
    }

}