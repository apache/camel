/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.spring;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.TestSupport;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version $Revision$
 */
public class CamelContextFactoryBeanTest extends TestSupport {
    private static final transient Log log = LogFactory.getLog(CamelContextFactoryBeanTest.class);
    
    public void testClassPathRouteLoading() throws Exception {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/spring/camel_context_factory_bean_test.xml");

        CamelContext context = (CamelContext) applicationContext.getBean("camel");
        assertNotNull("No context found!", context);

        List<Route> routes = context.getRoutes();
        log.debug("Found routes: " + routes);

        assertEquals("One Route should be found", 1, routes.size());

        for (Route route : routes) {
            Endpoint key = route.getEndpoint();
            Processor processor = route.getProcessor();

            assertEndpointUri(key, "queue:test.a");
        }
    }
    
    public void testXMLRouteLoading() throws Exception {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/spring/camel_context_factory_bean_test.xml");

        CamelContext context = (CamelContext) applicationContext.getBean("camel2");
        assertNotNull("No context found!", context);

        List<Route> routes = context.getRoutes();
        log.debug("Found routes: " + routes);

        assertEquals("One Route should be found", 1, routes.size());

        for (Route route : routes) {
            Endpoint key = route.getEndpoint();
            Processor processor = route.getProcessor();
            assertEndpointUri(key, "queue:test.c");
        }
    }

}
