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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;

/**
 * Unit test for helper methods on the DefaultComponent.
 */
public class DefaultComponentTest extends ContextTestSupport {

    private final class MyComponent extends DefaultComponent {

        private MyComponent(CamelContext context) {
            super(context);
        }

        protected Endpoint createEndpoint(String uri, String remaining, Map parameters)
            throws Exception {
            return null;
        }
    }

    public void testGetAndRemoveParameterEmptyMap() {
        Map parameters = new HashMap();
        MyComponent my = new MyComponent(this.context);
        Integer value = my.getAndRemoveParameter(parameters, "size", Integer.class);
        assertNull(value);
    }

    public void testGetAndRemoveParameterEmptyMapDefault() {
        Map parameters = new HashMap();
        MyComponent my = new MyComponent(this.context);
        Integer value = my.getAndRemoveParameter(parameters, "size", Integer.class, 5);
        assertEquals(value.intValue(), 5);
    }

    public void testGetAndRemoveParameterEmptyMapDefaultIsNull() {
        Map parameters = new HashMap();
        MyComponent my = new MyComponent(this.context);
        Integer value = my.getAndRemoveParameter(parameters, "size", Integer.class, null);
        assertNull(value);
    }

    public void testGetAndRemoveParameterToInteger() {
        Map parameters = new HashMap();
        parameters.put("size", 200);
        MyComponent my = new MyComponent(this.context);
        Integer value = my.getAndRemoveParameter(parameters, "size", Integer.class);
        assertEquals(value.intValue(), 200);
    }

    public void testGetAndRemoveParameterToIntegerDefault() {
        Map parameters = new HashMap();
        parameters.put("size", 200);
        MyComponent my = new MyComponent(this.context);
        Integer value = my.getAndRemoveParameter(parameters, "level", Integer.class, 4);
        assertEquals(value.intValue(), 4);
    }

    public void testResolveAndRemoveReferenceParameter() {
        Map parameters = new HashMap();
        parameters.put("date", "#beginning");
        MyComponent my = new MyComponent(this.context);
        Date value = my.resolveAndRemoveReferenceParameter(parameters, "date", Date.class);
        assertEquals(new Date(0), value);
    }

    public void testResolveAndRemoveReferenceParameterNotInRegistryDefault() {
        Map parameters = new HashMap();
        parameters.put("date", "#somewhen");
        MyComponent my = new MyComponent(this.context);
        Date value = my.resolveAndRemoveReferenceParameter(parameters, "date", Date.class, new Date(1));
        assertEquals(new Date(1), value);
    }

    public void testResolveAndRemoveReferenceParameterNotInRegistryNull() {
        Map parameters = new HashMap();
        parameters.put("date", "#somewhen");
        MyComponent my = new MyComponent(this.context);
        Date value = my.resolveAndRemoveReferenceParameter(parameters, "date", Date.class);
        assertNull(value);
    }

    public void testResolveAndRemoveReferenceParameterNotInMapDefault() {
        Map parameters = new HashMap();
        parameters.put("date", "#beginning");
        MyComponent my = new MyComponent(this.context);
        Date value = my.resolveAndRemoveReferenceParameter(parameters, "wrong", Date.class, new Date(1));
        assertEquals(new Date(1), value);
    }

    public void testResolveAndRemoveReferenceParameterNotInMapNull() {
        Map parameters = new HashMap();
        parameters.put("date", "#beginning");
        MyComponent my = new MyComponent(this.context);
        Date value = my.resolveAndRemoveReferenceParameter(parameters, "wrong", Date.class);
        assertNull(value);
    }

    public void testResolveAndRemoveInvalidReferenceParameter() {
        Map parameters = new HashMap();
        parameters.put("date", "beginning");
        MyComponent my = new MyComponent(this.context);
        try {
            my.resolveAndRemoveReferenceParameter(parameters, "date", Date.class);
            fail("usage of invalid reference");
        } catch (IllegalArgumentException e) {
            // test passed
        }
    }

    public void testContextShouldBeSet() throws Exception {
        MyComponent my = new MyComponent(null);
        try {
            my.start();
            fail("Should have thrown a IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals("camelContext must be specified", e.getMessage());
        }
    }

    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndiRegistry = super.createRegistry();
        jndiRegistry.bind("beginning", new Date(0));
        return jndiRegistry;
    }

}
