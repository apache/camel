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
package org.apache.camel.spring.config;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.util.IOHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CamelContextAutoStartupTest extends Assert {

    private AbstractXmlApplicationContext ac;

    @Test
    public void testAutoStartupFalse() throws Exception {
        ac = new ClassPathXmlApplicationContext("org/apache/camel/spring/config/CamelContextAutoStartupTestFalse.xml");

        SpringCamelContext camel = ac.getBeansOfType(SpringCamelContext.class).values().iterator().next();
        assertNotNull(camel.getName());
        assertEquals(true, camel.isStarted());
        assertEquals(Boolean.FALSE, camel.isAutoStartup());
        assertEquals(1, camel.getRoutes().size());

        assertEquals(false, camel.getRouteController().getRouteStatus("foo").isStarted());

        // now starting route manually
        camel.startRoute("foo");

        assertEquals(Boolean.FALSE, camel.isAutoStartup());
        assertEquals(1, camel.getRoutes().size());
        assertEquals(true, camel.getRouteController().getRouteStatus("foo").isStarted());

        // and now we can send a message to the route and see that it works
        MockEndpoint mock = camel.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);

        ProducerTemplate template = camel.createProducerTemplate();
        template.start();
        template.sendBody("direct:start", "Hello World");
        template.stop();

        mock.assertIsSatisfied();
    }

    @Test
    public void testAutoStartupTrue() throws Exception {
        ac = new ClassPathXmlApplicationContext("org/apache/camel/spring/config/CamelContextAutoStartupTestTrue.xml");

        SpringCamelContext camel = ac.getBeansOfType(SpringCamelContext.class).values().iterator().next();
        assertNotNull(camel.getName());
        assertEquals(true, camel.isStarted());
        assertEquals(Boolean.TRUE, camel.isAutoStartup());
        assertEquals(1, camel.getRoutes().size());

        // send a message to the route and see that it works
        MockEndpoint mock = camel.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);

        ProducerTemplate template = camel.createProducerTemplate();
        template.start();
        template.sendBody("direct:start", "Hello World");
        template.stop();

        mock.assertIsSatisfied();
    }

    @After
    public void tearDown() throws Exception {
        IOHelper.close(ac);

    }
}
