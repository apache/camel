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
import org.apache.camel.main.Main;
import org.apache.camel.model.ModelCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MainXmlTemplateTest {

    @Test
    public void testMainRoutesCollector() throws Exception {
        // will load XML from target/classes when testing
        doTestMain("org/apache/camel/main/xml/camel-template.xml");
    }

    @Test
    public void testMainRoutesCollectorScan() throws Exception {
        // will load XML from target/classes when testing
        doTestMain("org/apache/camel/main/xml/camel-template*.xml");
    }

    @Test
    public void testMainRoutesCollectorScanTwo() throws Exception {
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

    protected void doTestMain(String xmlRoutes) throws Exception {
        Main main = new Main();
        main.configure().withXmlRouteTemplates(xmlRoutes);
        main.start();

        CamelContext camelContext = main.getCamelContext();
        assertNotNull(camelContext);
        assertEquals(0, camelContext.getRoutes().size());
        assertEquals(1, camelContext.adapt(ModelCamelContext.class).getRouteTemplateDefinitions().size());

        main.stop();
    }

}
