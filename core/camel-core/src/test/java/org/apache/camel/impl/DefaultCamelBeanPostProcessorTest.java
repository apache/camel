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
package org.apache.camel.impl;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Consume;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.junit.Before;
import org.junit.Test;

public class DefaultCamelBeanPostProcessorTest extends ContextTestSupport {

    private CamelBeanPostProcessor postProcessor;

    @Test
    public void testPostProcessor() throws Exception {
        FooService foo = new FooService();
        foo.setFooEndpoint("seda:input");
        foo.setBarEndpoint("mock:result");

        postProcessor.postProcessBeforeInitialization(foo, "foo");
        postProcessor.postProcessAfterInitialization(foo, "foo");

        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("seda:input", "Hello World");
        assertMockEndpointsSatisfied();

        // should register the beans in the registry via @BindRegistry
        Object bean = context.getRegistry().lookupByName("myCoolBean");
        assertNotNull(bean);
        assertIsInstanceOf(MySerialBean.class, bean);
        bean = context.getRegistry().lookupByName("FooService");
        assertNotNull(bean);
        assertIsInstanceOf(FooService.class, bean);
        bean = context.getRegistry().lookupByName("doSomething");
        assertNotNull(bean);
        assertIsInstanceOf(FooBar.class, bean);
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        postProcessor = context.adapt(ExtendedCamelContext.class).getBeanPostProcessor();
    }

    @BindToRegistry
    public class FooService {

        private String fooEndpoint;
        private String barEndpoint;
        @Produce
        private ProducerTemplate bar;
        @BindToRegistry("myCoolBean")
        private MySerialBean myBean = new MySerialBean();

        @BindToRegistry
        public FooBar doSomething() {
            return new FooBar();
        }

        public String getFooEndpoint() {
            return fooEndpoint;
        }

        public void setFooEndpoint(String fooEndpoint) {
            this.fooEndpoint = fooEndpoint;
        }

        public String getBarEndpoint() {
            return barEndpoint;
        }

        public void setBarEndpoint(String barEndpoint) {
            this.barEndpoint = barEndpoint;
        }

        @Consume
        public void onFoo(String input) {
            bar.sendBody(input);
        }
    }
}
