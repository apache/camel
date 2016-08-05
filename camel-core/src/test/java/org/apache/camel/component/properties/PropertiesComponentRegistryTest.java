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
package org.apache.camel.component.properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.bean.MyDummyBean;
import org.apache.camel.component.bean.MyFooBean;
import org.apache.camel.impl.JndiRegistry;

/**
 * @version 
 */
public class PropertiesComponentRegistryTest extends ContextTestSupport {

    private MyFooBean foo;
    private MyDummyBean bar;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        foo = new MyFooBean();
        bar = new MyDummyBean();

        JndiRegistry jndi = super.createRegistry();
        jndi.bind("foo", foo);
        jndi.bind("bar", bar);
        return jndi;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        PropertiesComponent pc = new PropertiesComponent();
        pc.setLocation("classpath:org/apache/camel/component/properties/cheese.properties");
        context.addComponent("properties", pc);

        return context;
    }

    public void testPropertiesComponentRegistryPlain() throws Exception {
        context.start();

        assertSame(foo, context.getRegistry().lookupByName("foo"));
        assertSame(bar, context.getRegistry().lookupByName("bar"));
        assertNull(context.getRegistry().lookupByName("unknown"));
    }

    public void testPropertiesComponentRegistryLookupName() throws Exception {
        context.start();

        assertSame(foo, context.getRegistry().lookupByName("{{bean.foo}}"));
        assertSame(bar, context.getRegistry().lookupByName("{{bean.bar}}"));

        try {
            context.getRegistry().lookupByName("{{bean.unknown}}");
            fail("Should have thrown exception");
        } catch (RuntimeCamelException e) {
            IllegalArgumentException cause = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Property with key [bean.unknown] not found in properties from text: {{bean.unknown}}", cause.getMessage());
        }
    }

    public void testPropertiesComponentRegistryLookupNameAndType() throws Exception {
        context.start();

        assertSame(foo, context.getRegistry().lookupByNameAndType("{{bean.foo}}", MyFooBean.class));
        assertSame(bar, context.getRegistry().lookupByNameAndType("{{bean.bar}}", MyDummyBean.class));

        try {
            context.getRegistry().lookupByNameAndType("{{bean.unknown}}", MyDummyBean.class);
            fail("Should have thrown exception");
        } catch (RuntimeCamelException e) {
            IllegalArgumentException cause = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Property with key [bean.unknown] not found in properties from text: {{bean.unknown}}", cause.getMessage());
        }
    }

}