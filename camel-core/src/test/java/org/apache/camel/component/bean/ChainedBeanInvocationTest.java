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

import javax.naming.Context;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.util.jndi.JndiContext;
import org.easymock.EasyMock;

public class ChainedBeanInvocationTest extends ContextTestSupport {
    protected MyBean beanMock;

    @Override
    protected void setUp() throws Exception {
        beanMock = EasyMock.createStrictMock(MyBean.class);
        super.setUp();
    }

    @Override
    protected Context createJndiContext() throws Exception {
        JndiContext context = new JndiContext();
        context.bind("myBean", beanMock);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("bean:myBean?methodName=a")
                    .bean(beanMock, "b")
                    .beanRef("myBean", "c");
                from("direct:start2")
                    .to("bean:myBean?methodName=a")
                    .to("bean:myBean")
                    .bean(beanMock, "b")
                    .bean(beanMock)
                    .beanRef("myBean", "c")
                    .beanRef("myBean");
            }
        };
    }

    public void testNormalInvocation() throws Throwable {
        beanMock.a();
        beanMock.b();
        beanMock.c();
        EasyMock.replay(beanMock);
        Exchange result = template.send("direct:start", new DefaultExchange(context));
        if (result.getException() != null) {
            throw result.getException();
        }
        EasyMock.verify(beanMock);
    }

    public void testNoMethodSpecified() throws Throwable {
        beanMock.a();
        EasyMock.replay(beanMock);
        Exchange result = template.send("direct:start2", new DefaultExchange(context));
        assertNotNull(result.getException());
        assertEquals(result.getException().getClass(), IllegalStateException.class);
        EasyMock.verify(beanMock);
    }

    public void testMethodHeaderSet() throws Exception {
        beanMock.a();        
        beanMock.d();        
        beanMock.b();        
        beanMock.d();        
        beanMock.c();        
        beanMock.d();        
        EasyMock.replay(beanMock);
        template.sendBodyAndHeader("direct:start2", "test", BeanProcessor.METHOD_NAME, "d");
        EasyMock.verify(beanMock);
    }

    public interface MyBean {
        void a();
        void b();
        void c();
        void d();
    }
}
