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

import java.util.List;

import junit.framework.TestCase;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.component.bean.BeanComponent;
import org.apache.camel.util.CamelContextHelper;

/**
 * @version $Revision$
 */
public class DefaultCamelContextTest extends TestCase {

    public void testAutoCreateComponentsOn() {
        DefaultCamelContext ctx = new DefaultCamelContext();
        Component component = ctx.getComponent("bean");
        assertNotNull(component);
        assertEquals(component.getClass(), BeanComponent.class);
    }

    public void testAutoCreateComponentsOff() {
        DefaultCamelContext ctx = new DefaultCamelContext();
        ctx.setAutoCreateComponents(false);
        Component component = ctx.getComponent("bean");
        assertNull(component);
    }

    public void testGetComponents() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        Component component = ctx.getComponent("bean");
        assertNotNull(component);

        List<String> list = ctx.getComponentNames();
        assertEquals(1, list.size());
        assertEquals("bean", list.get(0));
    }

    public void testGetEndpoint() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        Endpoint endpoint = ctx.getEndpoint("log:foo");
        assertNotNull(endpoint);
    }

    public void testGetEndpointNotFound() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        try {
            ctx.getEndpoint("xxx:foo");
            fail("Should have thrown a ResolveEndpointFailedException");
        } catch (ResolveEndpointFailedException e) {
            assertTrue(e.getMessage().contains("No component found with scheme: xxx"));
        }
    }

    public void testGetEndpointNoScheme() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        try {
            CamelContextHelper.getMandatoryEndpoint(ctx, "log.foo");
            fail("Should have thrown a NoSuchEndpointException");
        } catch (NoSuchEndpointException e) {
            // expected
        }
    }

}
