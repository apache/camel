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
package org.apache.camel.maven;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class CamelSpringNamespaceTest {

    private CamelSpringNamespace camelSpringNamespace = new CamelSpringNamespace();

    @Test
    public void testSchemaNamespace() throws Exception {
        assertEquals(Constants.XML_SCHEMA_NAMESPACE_URI,
                camelSpringNamespace.getNamespaceURI(Constants.XML_SCHEMA_NAMESPACE_PREFIX));
        assertNull(camelSpringNamespace.getNamespaceURI("unregisterdPrefix"));
    }

    @Test
    public void testGetPrefix() throws Exception {
        try {
            camelSpringNamespace.getPrefix(Constants.XML_SCHEMA_NAMESPACE_URI);
            fail("UnsupportedOperationException expected");
        } catch (UnsupportedOperationException e) {
            // Expected.
        }
    }

    @Test
    public void testGetPrefixes() throws Exception {
        try {
            camelSpringNamespace.getPrefixes(Constants.XML_SCHEMA_NAMESPACE_URI);
            fail("UnsupportedOperationException expected");
        } catch (UnsupportedOperationException e) {
            // Expected.
        }
    }
}