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
package org.apache.camel.main.xml;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.main.Main;
import org.junit.Assert;
import org.junit.Test;

public class MainXmlTest extends Assert {

    @Test
    public void testMainRoutesCollector() throws Exception {
        // will load XML from target/classes when testing
        doTestMain("org/apache/camel/main/xml/camel-dummy.xml,org/apache/camel/main/xml/camel-scan.xml");
    }

    @Test
    public void testMainRoutesCollectorScan() throws Exception {
        // will load XML from target/classes when testing
        doTestMain("org/apache/camel/main/xml/camel-*.xml");
    }

    @Test
    public void testMainRoutesCollectorScanWildcardDirClasspathPath() throws Exception {
        // will load XML from target/classes when testing
        doTestMain("org/apache/camel/main/**/*.xml");
    }

    @Test
    public void testMainRoutesCollectorScanClasspathPrefix() throws Exception {
        // will load XML from target/classes when testing
        doTestMain("classpath:org/apache/camel/main/xml/camel-*.xml");
    }

    @Test
    public void testMainRoutesCollectorScanInJar() throws Exception {
        // will load XML from camel-core test JAR when testing
        doTestMain("org/apache/camel/model/scan-*.xml");
    }

    @Test
    public void testMainRoutesCollectorScanInDir() throws Exception {
        doTestMain("file:src/test/resources/org/apache/camel/main/xml/camel-*.xml");
    }

    @Test
    public void testMainRoutesCollectorScanWildcardDirFilePath() throws Exception {
        doTestMain("file:src/test/resources/**/*.xml");
    }

    @Test
    public void testMainRoutesCollectorFile() throws Exception {
        doTestMain("file:src/test/resources/org/apache/camel/main/xml/camel-dummy.xml,file:src/test/resources/org/apache/camel/main/xml/camel-scan.xml,");
    }

    @Test
    public void testMainRoutesCollectorScanInJarAndDir() throws Exception {
        doTestMain("classpath:org/apache/camel/main/xml/*dummy.xml,file:src/test/resources/org/apache/camel/main/xml/*scan.xml");
    }

    protected void doTestMain(String xmlRoutes) throws Exception {
        Main main = new Main();
        main.configure().withXmlRoutes(xmlRoutes);
        main.start();

        CamelContext camelContext = main.getCamelContext();
        assertNotNull(camelContext);
        assertEquals(2, camelContext.getRoutes().size());

        MockEndpoint endpoint = camelContext.getEndpoint("mock:scan", MockEndpoint.class);
        endpoint.expectedBodiesReceived("Hello World");
        MockEndpoint endpoint2 = camelContext.getEndpoint("mock:dummy", MockEndpoint.class);
        endpoint2.expectedBodiesReceived("Bye World");

        main.getCamelTemplate().sendBody("direct:scan", "Hello World");
        main.getCamelTemplate().sendBody("direct:dummy", "Bye World");

        endpoint.assertIsSatisfied();
        endpoint2.assertIsSatisfied();

        main.stop();
    }

}
