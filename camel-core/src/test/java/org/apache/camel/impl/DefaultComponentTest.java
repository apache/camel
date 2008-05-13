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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;

/**
 * Unit test for helper methods on the DefaultComponent.
 */
public class DefaultComponentTest extends ContextTestSupport {

    private final class MyComponent extends DefaultComponent<DefaultExchange> {

        private MyComponent(CamelContext context) {
            super(context);
        }

        protected Endpoint<DefaultExchange> createEndpoint(String uri, String remaining, Map parameters)
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

}
