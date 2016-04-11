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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Service;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;

/**
 * @version 
 */
public class BeanLifecycleTest extends ContextTestSupport {

    private MyBean statefulInstance;
    private MyBean statefulInstanceInRegistry;
    private MyBean statefulInstanceInRegistryNoCache;

    @Override
    protected void setUp() throws Exception {
        statefulInstance = new MyBean();
        statefulInstanceInRegistry = new MyBean();
        statefulInstanceInRegistryNoCache = new MyBean();

        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        assertEquals("stopped", statefulInstance.getStatus());
        assertEquals("stopped", statefulInstanceInRegistry.getStatus());
        assertNull(statefulInstanceInRegistryNoCache.getStatus());
        assertEquals(2, MyStatefulBean.INSTANCES.get());
    }

    public void testBeanLifecycle() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        assertEquals("started", statefulInstance.getStatus());
        assertEquals("started", statefulInstanceInRegistry.getStatus());
        assertNull(statefulInstanceInRegistryNoCache.getStatus());
        assertEquals(2, MyStatefulBean.INSTANCES.get());

        template.sendBody("direct:foo", null);

        mock.assertIsSatisfied();
    }


    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("statefulInstanceInRegistry", statefulInstanceInRegistry);
        jndi.bind("statefulInstanceInRegistryNoCache", statefulInstanceInRegistryNoCache);
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").routeId("foo")
                    .bean(statefulInstance, "doSomething")
                    .bean(MyStatefulBean.class, "doSomething")
                    .bean(MyStatefulBean.class.getName(), "doSomething", true)
                    .bean(MyStatelessBean.class.getName(), "doSomething", false)
                    .to("bean:statefulInstanceInRegistry?method=doSomething&cache=true")
                    .to("bean:statefulInstanceInRegistryNoCache?method=doSomething&cache=false")
                   .to("mock:result");
            }
        };
    }

    public static class MyBean implements Service {
        private String status;

        public String getStatus() {
            return status;
        }

        public void doSomething(Exchange exchange) {
            // noop
        }

        @Override
        public void start() throws Exception {
            status = "started";
        }

        @Override
        public void stop() throws Exception {
            status = "stopped";
        }
    }

    public static class MyStatelessBean implements Service {

        public void doSomething(Exchange exchange) {
            // noop
        }

        @Override
        public void start() throws Exception {
            fail("Should not be invoked");
        }

        @Override
        public void stop() throws Exception {
            fail("Should not be invoked");
        }
    }

    public static class MyStatefulBean implements Service {
        private static final AtomicInteger INSTANCES = new AtomicInteger(0);

        public MyStatefulBean() {
            INSTANCES.incrementAndGet();
        }

        public void doSomething(Exchange exchange) {
            // noop
        }

        @Override
        public void start() throws Exception {
        }

        @Override
        public void stop() throws Exception {
        }
    }
}
