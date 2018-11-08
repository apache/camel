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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Expression;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

/**
 * @version 
 */
public class BeanRegistryBeanTest extends ContextTestSupport {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("foo", new MyFooBean());
        jndi.bind("static", MyFooBean.class);
        return jndi;
    }

    @Test
    public void testNoBean() {
        RegistryBean rb = new RegistryBean(context, "bar");
        try {
            rb.getBean();
            fail("Should have thrown exception");
        } catch (NoSuchBeanException e) {
            assertEquals("bar", e.getName());
        }
    }

    @Test
    public void testBean() {
        RegistryBean rb = new RegistryBean(context, "foo");
        Object bean = rb.getBean();
        assertIsInstanceOf(MyFooBean.class, bean);

        assertNotNull(rb.getContext());
        assertEquals("foo", rb.getName());
        assertNotNull(rb.getParameterMappingStrategy());
        assertNotNull(rb.getRegistry());
    }

    @Test
    public void testParameterMappingStrategy() {
        RegistryBean rb = new RegistryBean(context, "foo");
        ParameterMappingStrategy myStrategy = new ParameterMappingStrategy() {
            public Expression getDefaultParameterTypeExpression(Class<?> parameterType) {
                return null;
            }
        };
        rb.setParameterMappingStrategy(myStrategy);

        Object bean = rb.getBean();
        assertIsInstanceOf(MyFooBean.class, bean);

        assertNotNull(rb.getContext());
        assertEquals("foo", rb.getName());
        assertEquals(myStrategy, rb.getParameterMappingStrategy());
        assertNotNull(rb.getRegistry());
    }

    @Test
    public void testLookupClass() throws Exception {
        RegistryBean rb = new RegistryBean(context, "static");

        Object bean = rb.getBean();
        MyFooBean foo = assertIsInstanceOf(MyFooBean.class, bean);
        assertEquals("foofoo", foo.echo("foo"));
    }

    @Test
    public void testLookupFQNClass() throws Exception {
        RegistryBean rb = new RegistryBean(context, "org.apache.camel.component.bean.MyDummyBean");

        Object bean = rb.getBean();
        MyDummyBean dummy = assertIsInstanceOf(MyDummyBean.class, bean);
        assertEquals("Hello World", dummy.hello("World"));
    }

    public static class MyFooBean {

        public String echo(String s) {
            return s + s;
        }

    }

    
}
