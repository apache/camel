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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version $Revision$
 */
public class CamelContextAutoStartupTest extends TestCase {

    private static final transient Log LOG = LogFactory.getLog(CamelContextAutoStartupTest.class);

    public void testAutoStartupFalse() throws Exception {
        ApplicationContext ac = new ClassPathXmlApplicationContext("org/apache/camel/spring/config/CamelContextAutoStartupTestFalse.xml");

        SpringCamelContext camel = (SpringCamelContext) ac.getBean("myCamel");
        assertEquals("myCamel", camel.getName());
        assertEquals(false, camel.isStarted());
        assertEquals(false, camel.isAutoStartup());
        assertEquals(0, camel.getRoutes().size());

        // now start Camel
        LOG.info("******** now starting Camel manually *********");
        camel.start();

        // now its started
        assertEquals(true, camel.isStarted());
        // but auto startup is still fasle
        assertEquals(false, camel.isAutoStartup());
        // but now we have one route
        assertEquals(1, camel.getRoutes().size());

        // and now we can send a message to the route and see that it works
        MockEndpoint mock = camel.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);

        camel.createProducerTemplate().sendBody("direct:start", "Hello World");

        mock.assertIsSatisfied();
    }

    public void testAutoStartupTrue() throws Exception {
        ApplicationContext ac = new ClassPathXmlApplicationContext("org/apache/camel/spring/config/CamelContextAutoStartupTestTrue.xml");

        SpringCamelContext camel = (SpringCamelContext) ac.getBean("myCamel");
        assertEquals("myCamel", camel.getName());
        assertEquals(true, camel.isStarted());
        assertEquals(true, camel.isAutoStartup());
        assertEquals(1, camel.getRoutes().size());

        // send a message to the route and see that it works
        MockEndpoint mock = camel.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);

        camel.createProducerTemplate().sendBody("direct:start", "Hello World");

        mock.assertIsSatisfied();
    }

}
