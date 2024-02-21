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
package org.apache.camel.main;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for PropertyBindingSupport
 */
public class PropertyBindingSupportRootArrayReflectionTest {

    @Test
    public void testRootArray() throws Exception {
        CamelContext context = new DefaultCamelContext();

        BeanIntrospection bi = PluginHelper.getBeanIntrospection(context);
        bi.setExtendedStatistics(true);

        context.start();

        MyOtherFoo target = new MyOtherFoo();

        PropertyBindingSupport.build()
                .withCamelContext(context)
                .withTarget(target)
                .withProperty("bars[0]", "#class:" + MyOtherBar.class.getName())
                .withProperty("bars[0].names[0]", "a")
                .withProperty("bars[0].names[1]", "b")
                .withRemoveParameters(false).bind();

        assertEquals(1, target.getBars().size());
        assertEquals(2, target.getBars().get(0).getNames().size());
        assertEquals("a", target.getBars().get(0).getNames().get(0));
        assertEquals("b", target.getBars().get(0).getNames().get(1));

        // no configurers so should use reflection
        assertEquals(9, bi.getInvokedCounter());

        context.stop();
    }

    @Test
    public void testNestedArray() throws Exception {
        CamelContext context = new DefaultCamelContext();

        BeanIntrospection bi = PluginHelper.getBeanIntrospection(context);
        bi.setExtendedStatistics(true);

        context.start();

        MyRoot target = new MyRoot();

        PropertyBindingSupport.build()
                .withCamelContext(context)
                .withTarget(target)
                .withProperty("name", "Donald")
                .withProperty("foo.bars[0]", "#class:" + MyOtherBar.class.getName())
                .withProperty("foo.bars[0].names[0]", "a")
                .withProperty("foo.bars[0].names[1]", "b")
                .withRemoveParameters(false).bind();

        assertEquals("Donald", target.getName());
        assertEquals(1, target.getFoo().getBars().size());
        assertEquals(2, target.getFoo().getBars().get(0).getNames().size());
        assertEquals("a", target.getFoo().getBars().get(0).getNames().get(0));
        assertEquals("b", target.getFoo().getBars().get(0).getNames().get(1));

        // no configurers so should use reflection
        assertEquals(15, bi.getInvokedCounter());

        context.stop();
    }

    @Test
    public void testNestedArrayAutodetect() throws Exception {
        CamelContext context = new DefaultCamelContext();

        BeanIntrospection bi = PluginHelper.getBeanIntrospection(context);
        bi.setExtendedStatistics(true);

        context.start();

        MyRoot target = new MyRoot();

        PropertyBindingSupport.build()
                .withCamelContext(context)
                .withTarget(target)
                .withProperty("name", "Donald")
                // should autodetect what type bars[] and names[] contains
                .withProperty("foo.bars[0].names[0]", "a")
                .withProperty("foo.bars[0].names[1]", "b")
                .withRemoveParameters(false).bind();

        assertEquals("Donald", target.getName());
        assertEquals(1, target.getFoo().getBars().size());
        assertEquals(2, target.getFoo().getBars().get(0).getNames().size());
        assertEquals("a", target.getFoo().getBars().get(0).getNames().get(0));
        assertEquals("b", target.getFoo().getBars().get(0).getNames().get(1));

        // no configurers so should use reflection
        assertTrue(bi.getInvokedCounter() > 0);

        context.stop();
    }

    public static class MyRoot {

        private String name;
        private MyOtherFoo foo;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public MyOtherFoo getFoo() {
            return foo;
        }

        public void setFoo(MyOtherFoo foo) {
            this.foo = foo;
        }
    }

}
