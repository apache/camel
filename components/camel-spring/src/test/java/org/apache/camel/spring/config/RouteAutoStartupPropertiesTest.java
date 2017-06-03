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
package org.apache.camel.spring.config;

import junit.framework.TestCase;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.util.IOHelper;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class RouteAutoStartupPropertiesTest extends TestCase {

    private AbstractXmlApplicationContext ac;

    public void testAutoStartupFalse() throws Exception {
        ac = new ClassPathXmlApplicationContext("org/apache/camel/spring/config/RouteAutoStartupFalseTest.xml");

        SpringCamelContext camel = ac.getBeansOfType(SpringCamelContext.class).values().iterator().next();

        assertEquals(false, camel.getRouteStatus("foo").isStarted());

        // now starting route manually
        camel.startRoute("foo");
        assertEquals(true, camel.getRouteStatus("foo").isStarted());

        // and now we can send a message to the route and see that it works
        MockEndpoint mock = camel.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);

        ProducerTemplate template = camel.createProducerTemplate();
        template.start();
        template.sendBody("direct:start", "Hello World");
        template.stop();

        mock.assertIsSatisfied();
    }

    public void testAutoStartupTrue() throws Exception {
        ac = new ClassPathXmlApplicationContext("org/apache/camel/spring/config/RouteAutoStartupTrueTest.xml");

        SpringCamelContext camel = ac.getBeansOfType(SpringCamelContext.class).values().iterator().next();

        assertEquals(true, camel.getRouteStatus("bar").isStarted());

        // and now we can send a message to the route and see that it works
        MockEndpoint mock = camel.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);

        ProducerTemplate template = camel.createProducerTemplate();
        template.start();
        template.sendBody("direct:start", "Hello World");
        template.stop();

        mock.assertIsSatisfied();
    }

    @Override
    protected void tearDown() throws Exception {
        IOHelper.close(ac);
        super.tearDown();
    }
}
