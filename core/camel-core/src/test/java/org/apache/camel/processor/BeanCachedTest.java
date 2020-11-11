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
package org.apache.camel.processor;

import javax.naming.Context;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultRegistry;
import org.apache.camel.support.jndi.JndiBeanRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Deprecated
public class BeanCachedTest extends ContextTestSupport {

    private Context context;

    private Registry registry;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:noCache").to("bean:something?cache=false");
                from("direct:cached").to("bean:something?cache=true");

            }
        };
    }

    @Override
    protected Registry createRegistry() throws Exception {
        context = createJndiContext();
        context.bind("something", new MyBean());
        registry = new DefaultRegistry(new JndiBeanRepository(context));
        return registry;
    }

    @Test
    public void testFreshBeanInContext() throws Exception {
        // Just make sure the bean processor doesn't work if the cached is false
        MyBean originalInstance = registry.lookupByNameAndType("something", MyBean.class);
        template.sendBody("direct:noCache", null);
        context.unbind("something");
        context.bind("something", new MyBean());
        // Make sure we can get the object from the registry
        assertNotSame(registry.lookupByName("something"), originalInstance);
        template.sendBody("direct:noCache", null);
    }

    @Test
    public void testBeanWithCached() throws Exception {
        // Just make sure the bean processor doesn't work if the cached is false
        MyBean originalInstance = registry.lookupByNameAndType("something", MyBean.class);
        template.sendBody("direct:cached", null);
        context.unbind("something");
        context.bind("something", new MyBean());
        // Make sure we can get the object from the registry
        assertNotSame(registry.lookupByName("something"), originalInstance);
        try {
            template.sendBody("direct:cached", null);
            fail("The IllegalStateException is expected");
        } catch (CamelExecutionException ex) {
            boolean b = ex.getCause() instanceof IllegalStateException;
            assertTrue(b, "IllegalStateException is expected!");
            assertEquals("This bean is not supported to be invoked again!", ex.getCause().getMessage());
        }
    }

    public static class MyBean {
        private boolean invoked;

        public void doSomething(Exchange exchange) throws Exception {
            if (invoked) {
                throw new IllegalStateException("This bean is not supported to be invoked again!");
            } else {
                invoked = true;
            }

        }
    }

}
