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

public class CamelModelCatalogTest {

    private static final Logger LOG = LoggerFactory.getLogger(CamelModelCatalogTest.class);

    @Test
    public void testFindModelNames() {
        CamelCatalog catalog = new DefaultCamelCatalog();
        List<String> names = catalog.findModelNames();

        assertNotNull(names);

        LOG.info("Found {} names", names.size());
        assertTrue("Should find some models", names.size() > 0);
    }

    @Test
    public void testFindModelNamesFilter() {
        CamelCatalog catalog = new DefaultCamelCatalog();
        List<String> names = catalog.findModelNames("transformation");

        assertNotNull(names);

        LOG.info("Found {} names", names.size());
        assertTrue("Should find some transformation models", names.size() > 0);
    }

    @Test
    public void testFindModelNamesFilterWildcard() {
        CamelCatalog catalog = new DefaultCamelCatalog();
        List<String> names = catalog.findModelNames("t*");

        assertNotNull(names);

        LOG.info("Found {} names", names.size());
        assertTrue("Should find some t* models", names.size() > 0);
    }

    @Test
    public void testFindComponentNamesFilterNoMatch() {
        CamelCatalog catalog = new DefaultCamelCatalog();
        List<String> names = catalog.findModelNames("cannotmatchme");

        assertNotNull(names);

        assertTrue("Should not match any models", names.size() == 0);
    }

    @Test
    public void testCoreComponentJson() {
        CamelCatalog catalog = new DefaultCamelCatalog();
        String json = catalog.modelJSonSchema("split");

        assertNotNull(json);
        LOG.info(json);

        assertTrue("Should find to split", json.contains("split"));
    }

    @Test
    public void testLabels() {
        CamelCatalog catalog = new DefaultCamelCatalog();
        Set<String> labels = catalog.findModelLabels();

        assertNotNull(labels);

        assertTrue("Should find labels", labels.size() > 0);
        assertTrue("Should find transformation label", labels.contains("transformation"));
    }

}
