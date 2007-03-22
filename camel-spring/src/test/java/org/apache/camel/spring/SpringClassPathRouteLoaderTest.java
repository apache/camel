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

import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.ApplicationContext;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.TestSupport;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Map;
import java.util.Set;

/**
 * @version $Revision$
 */
public class SpringClassPathRouteLoaderTest extends TestSupport {
    private static final transient Log log = LogFactory.getLog(SpringClassPathRouteLoaderTest.class);
    
    public void testLoadingRouteBuildresOnTheClassPathViaSpringXml() throws Exception {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/spring/findRouteBuildersOnClassPath.xml");

        CamelContext context = (CamelContext) applicationContext.getBean("camel");
        assertNotNull("No context found!", context);

        Map<Endpoint,Processor> map = context.getRoutes();
        log.debug("Found routes: " + map);

        Set<Map.Entry<Endpoint,Processor>> entries = map.entrySet();
        assertEquals("One Route should be found", 1, entries.size());

        for (Map.Entry<Endpoint, Processor> entry : entries) {
            Endpoint key = entry.getKey();
            Processor processor = entry.getValue();

            assertEndpointUri(key, "queue:test.a");
        }
    }
}
