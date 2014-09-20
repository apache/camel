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
package org.apache.camel.impl;

import java.util.Map;

import junit.framework.TestCase;
import org.apache.camel.CamelContext;

public class GetRegistryAsTypeTest extends TestCase {

    public void testDefault() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.start();

        JndiRegistry jndi = context.getRegistry(JndiRegistry.class);
        assertNotNull(jndi);

        assertNull(context.getRegistry(Map.class));
        assertNull(context.getRegistry(SimpleRegistry.class));

        context.stop();
    }

    public void testSimple() throws Exception {
        CamelContext context = new DefaultCamelContext(new SimpleRegistry());
        context.start();

        SimpleRegistry simple = context.getRegistry(SimpleRegistry.class);
        assertNotNull(simple);

        // simple extends Map
        assertNotNull(context.getRegistry(Map.class));
        assertNull(context.getRegistry(JndiRegistry.class));

        context.stop();
    }

    public void testComposite() throws Exception {
        CompositeRegistry cr = new CompositeRegistry();
        cr.addRegistry(new SimpleRegistry());
        cr.addRegistry(new JndiRegistry());

        CamelContext context = new DefaultCamelContext(cr);
        context.start();

        CompositeRegistry comp = context.getRegistry(CompositeRegistry.class);
        assertNotNull(comp);
        SimpleRegistry simple = context.getRegistry(SimpleRegistry.class);
        assertNotNull(simple);
        JndiRegistry jndi = context.getRegistry(JndiRegistry.class);
        assertNotNull(jndi);

        context.stop();
    }
}
