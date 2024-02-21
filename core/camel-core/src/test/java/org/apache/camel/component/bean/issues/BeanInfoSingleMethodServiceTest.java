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
package org.apache.camel.component.bean.issues;

import java.lang.reflect.Method;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.bean.BeanInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BeanInfoSingleMethodServiceTest extends ContextTestSupport {

    private final SingleMethodService<String, String> myService = new SingleMethodServiceImpl();

    @Test
    public void testBeanInfoSingleMethodRoute() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("You said Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testBeanInfoSingleMethod() throws Exception {
        BeanInfo beaninfo = new BeanInfo(context, SingleMethodService.class);
        assertEquals(1, beaninfo.getMethods().size());
        assertEquals("doSomething", beaninfo.getMethods().get(0).getMethod().getName());
    }

    @Test
    public void testBeanInfoSingleMethodImpl() throws Exception {
        BeanInfo beaninfo = new BeanInfo(context, SingleMethodServiceImpl.class);
        assertEquals(2, beaninfo.getMethods().size());
        assertEquals("doSomething", beaninfo.getMethods().get(0).getMethod().getName());
        assertEquals("hello", beaninfo.getMethods().get(1).getMethod().getName());

        Method method = beaninfo.getMethods().get(0).getMethod();
        Object out = method.invoke(myService, "Bye World");
        assertEquals("You said Bye World", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").bean(myService, "doSomething").to("mock:result");
            }
        };
    }
}
