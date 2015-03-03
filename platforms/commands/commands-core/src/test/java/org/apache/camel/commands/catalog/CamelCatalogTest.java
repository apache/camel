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

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CamelCatalogTest {

    private static final Logger LOG = LoggerFactory.getLogger(CamelCatalogTest.class);

    @Test
    public void testFindComponentNames() {
        CamelCatalog catalog = new DefaultCamelCatalog();
        List<String> names = catalog.findComponentNames();

        assertNotNull("The names should not be null", names);

        LOG.info("Found {} names", names.size());
        assertTrue("Should find some components", names.size() > 0);
    }

    @Test
    public void testFindComponentNamesFilter() {
        CamelCatalog catalog = new DefaultCamelCatalog();
        List<String> names = catalog.findComponentNames("testing");

        assertNotNull("The names should not be null", names);

        LOG.info("Found {} names", names.size());
        assertTrue("Should find some testing components", names.size() > 0);
    }

    @Test
    public void testFindComponentNamesFilterWildcard() {
        CamelCatalog catalog = new DefaultCamelCatalog();
        List<String> names = catalog.findComponentNames("t*");

        assertNotNull("The names should not be null", names);

        LOG.info("Found {} names", names.size());
        assertTrue("Should find some t* components", names.size() > 0);
    }

    @Test
    public void testFindComponentNamesFilterTwo() {
        CamelCatalog catalog = new DefaultCamelCatalog();
        List<String> names = catalog.findComponentNames("transformation");

        assertNotNull("The names should not be null", names);

        LOG.info("Found {} names", names.size());
        assertTrue("Should find some transformation components", names.size() > 0);
    }

    @Test
    public void testFindComponentNamesFilterNoMatch() {
        CamelCatalog catalog = new DefaultCamelCatalog();
        List<String> names = catalog.findComponentNames("cannotmatchme");

        assertNotNull("The names should not be null", names);

        assertTrue("Should not match any components", names.size() == 0);
    }

    @Test
    public void testCoreComponentJson() {
        CamelCatalog catalog = new DefaultCamelCatalog();
        String json = catalog.componentJSonSchema("bean");

        assertNotNull("Should find the json information about the bean component", json);
        LOG.info(json);

        assertTrue("Should find bean component", json.contains("bean"));
    }

    @Test
    public void testFtpComponentJson() {
        CamelCatalog catalog = new DefaultCamelCatalog();
        String json = catalog.componentJSonSchema("ftp");

        assertNotNull("Should find the json information about the ftp component", json);
        LOG.info(json);

        assertTrue("Should find ftp component", json.contains("ftp"));
    }

    @Test
    public void testLabels() {
        CamelCatalog catalog = new DefaultCamelCatalog();
        Set<String> labels = catalog.findComponentLabels();

        assertNotNull("Should component labels", labels);

        assertTrue("Should find labels", labels.size() > 0);
        assertTrue("Should find core label", labels.contains("core"));
        assertTrue("Should find testing label", labels.contains("testing"));
        assertTrue("Should find rest label", labels.contains("rest"));
    }

}
