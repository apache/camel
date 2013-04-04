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
package org.apache.camel.util;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;

/**
 * @version 
 */
public class CamelContextHelperTest extends ContextTestSupport {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("foo", new MyFooBean());
        return jndi;
    }

    public void testGetMandatoryEndpoint() {
        MockEndpoint mock = CamelContextHelper.getMandatoryEndpoint(context, "mock:foo", MockEndpoint.class);
        assertNotNull(mock);
    }

    public void testMandatoryConvertTo() {
        Integer num = CamelContextHelper.mandatoryConvertTo(context, Integer.class, "5");
        assertEquals(5, num.intValue());
    }

    public void testMandatoryConvertToNotPossible() {
        try {
            CamelContextHelper.mandatoryConvertTo(context, CamelContext.class, "5");
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testLookupBean() {
        Object foo = CamelContextHelper.lookup(context, "foo");
        assertNotNull(foo);
        assertIsInstanceOf(MyFooBean.class, foo);
    }

    public void testLookupBeanAndType() {
        MyFooBean foo = CamelContextHelper.lookup(context, "foo", MyFooBean.class);
        assertNotNull(foo);
    }

    public void testRouteStartupOrder() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").routeId("foo").startupOrder(222).to("mock:foo");
                from("direct:bar").routeId("bar").startupOrder(111).to("mock:bar");
            }
        });

        assertEquals(111, CamelContextHelper.getRouteStartupOrder(context, "bar"));
        assertEquals(222, CamelContextHelper.getRouteStartupOrder(context, "foo"));

        // no route with that name
        assertEquals(0, CamelContextHelper.getRouteStartupOrder(context, "zzz"));
    }

    public static class MyFooBean {

    }

}
