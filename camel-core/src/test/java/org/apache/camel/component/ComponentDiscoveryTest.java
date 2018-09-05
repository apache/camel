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
package org.apache.camel.component;

import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.CamelContextHelper;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Test we can auto discover components on the classpath
 */
public class ComponentDiscoveryTest {
    private static final Logger LOG = LoggerFactory.getLogger(ComponentDiscoveryTest.class);

    @Test
    public void testComponentDiscovery() throws Exception {
        CamelContext context = new DefaultCamelContext();

        SortedMap<String, Properties> map = CamelContextHelper.findComponents(context);
        assertNotNull("Should never return null", map);
        assertTrue("Component map should never be empty", !map.isEmpty());
        
        String[] expectedComponentNames = {"file", "vm"};
        for (String expectedName : expectedComponentNames) {
            Properties properties = map.get(expectedName);
            assertTrue("Component map contain component: " + expectedName, properties != null);
        }

        Set<Map.Entry<String, Properties>> entries = map.entrySet();
        for (Map.Entry<String, Properties> entry : entries) {
            LOG.info("Found component {} with properties: {}", entry.getKey(), entry.getValue());
        }
    }

}
