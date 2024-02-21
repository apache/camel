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
package org.apache.camel.dsl.xml.io;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.main.Main;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class XmlMainTemplatedRouteTest {

    @Test
    void testMainRoutesCollector() throws Exception {
        // will load XML from target/classes when testing
        doTestMain("org/apache/camel/main/xml/camel-template.xml,org/apache/camel/main/xml/camel-my-templated-route.xml");
    }

    @Test
    void testMainRoutesCollectorScan() throws Exception {
        // will load XML from target/classes when testing
        doTestMain("org/apache/camel/main/xml/camel-template*.xml,org/apache/camel/main/xml/camel-my-templated-route*.xml");
    }

    @Test
    void testMainRoutesCollectorScanTwo() throws Exception {
        // will load XML from target/classes when testing
        doTestMain("org/apache/camel/main/xml/camel-t*.xml,org/apache/camel/main/xml/camel-my-t*.xml");
    }

    @Test
    void testMainRoutesCollectorScanWildcardDirClasspathPath() throws Exception {
        // will load XML from target/classes when testing
        doTestMain("org/apache/camel/main/**/camel-t*.xml,org/apache/camel/main/**/camel-my-t*.xml");
    }

    @Test
    void testMainRoutesCollectorScanClasspathPrefix() throws Exception {
        // will load XML from target/classes when testing
        doTestMain("classpath:org/apache/camel/main/xml/camel-t*.xml,classpath:org/apache/camel/main/**/camel-my-t*.xml");
    }

    protected void doTestMain(String includes) throws Exception {
        Main main = new Main();
        main.configure().withRoutesIncludePattern(includes);
        main.start();

        CamelContext camelContext = main.getCamelContext();
        assertNotNull(camelContext);
        assertEquals(1, camelContext.getRoutes().size());

        MockEndpoint endpoint = camelContext.getEndpoint("mock:barVal", MockEndpoint.class);
        endpoint.expectedBodiesReceived("Hello World");

        main.getCamelTemplate().sendBody("direct:fooVal", "Hello World");

        endpoint.assertIsSatisfied();

        main.stop();
    }
}
