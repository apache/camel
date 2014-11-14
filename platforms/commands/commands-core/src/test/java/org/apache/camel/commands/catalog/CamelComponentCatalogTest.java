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
package org.apache.camel.commands.catalog;

import java.util.List;
import java.util.Set;

import org.apache.camel.catalog.CamelComponentCatalog;
import org.apache.camel.catalog.DefaultCamelComponentCatalog;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CamelComponentCatalogTest {

    private static final Logger LOG = LoggerFactory.getLogger(CamelComponentCatalogTest.class);

    @Test
    public void testFindComponentNames() {
        CamelComponentCatalog catalog = new DefaultCamelComponentCatalog();
        List<String> names = catalog.findComponentNames();

        assertNotNull(names);

        LOG.info("Found {} names", names.size());
        assertTrue("Should find some components", names.size() > 0);
    }

    @Test
    public void testFindComponentNamesFilter() {
        CamelComponentCatalog catalog = new DefaultCamelComponentCatalog();
        List<String> names = catalog.findComponentNames("testing");

        assertNotNull(names);

        LOG.info("Found {} names", names.size());
        assertTrue("Should find some testing components", names.size() > 0);
    }

    @Test
    public void testFindComponentNamesFilterWildcard() {
        CamelComponentCatalog catalog = new DefaultCamelComponentCatalog();
        List<String> names = catalog.findComponentNames("t*");

        assertNotNull(names);

        LOG.info("Found {} names", names.size());
        assertTrue("Should find some t* components", names.size() > 0);
    }

    @Test
    public void testFindComponentNamesFilterTwo() {
        CamelComponentCatalog catalog = new DefaultCamelComponentCatalog();
        List<String> names = catalog.findComponentNames("transformation");

        assertNotNull(names);

        LOG.info("Found {} names", names.size());
        assertTrue("Should find some transformation components", names.size() > 0);
    }

    @Test
    public void testFindComponentNamesFilterNoMatch() {
        CamelComponentCatalog catalog = new DefaultCamelComponentCatalog();
        List<String> names = catalog.findComponentNames("cannotmatchme");

        assertNotNull(names);

        assertTrue("Should not match any components", names.size() == 0);
    }

    @Test
    public void testCoreComponentJson() {
        CamelComponentCatalog catalog = new DefaultCamelComponentCatalog();
        String json = catalog.componentJSonSchema("bean");

        assertNotNull(json);
        LOG.info(json);

        assertTrue("Should find bean component", json.contains("bean"));
    }

    @Test
    public void testFtpComponentJson() {
        CamelComponentCatalog catalog = new DefaultCamelComponentCatalog();
        String json = catalog.componentJSonSchema("ftp");

        assertNotNull(json);
        LOG.info(json);

        assertTrue("Should find ftp component", json.contains("ftp"));
    }

    @Test
    public void testLabels() {
        CamelComponentCatalog catalog = new DefaultCamelComponentCatalog();
        Set<String> labels = catalog.findLabels();

        assertNotNull(labels);

        assertTrue("Should find labels", labels.size() > 0);
        assertTrue("Should find core label", labels.contains("core"));
        assertTrue("Should find testing label", labels.contains("testing"));
        assertTrue("Should find rest label", labels.contains("rest"));
    }

}
