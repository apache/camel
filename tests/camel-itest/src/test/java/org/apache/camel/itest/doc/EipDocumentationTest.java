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
package org.apache.camel.itest.doc;

import org.apache.camel.CamelContext;
import org.apache.camel.CatalogCamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "enable.documentation.itests", matches = "true")
public class EipDocumentationTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(EipDocumentationTest.class);

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testDocumentation() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            String json = ((CatalogCamelContext) context).getEipParameterJsonSchema("from");
            LOG.info(json);
            assertNotNull(json, "Should have found json for from");

            assertTrue(json.contains("\"name\": \"from\""));
            assertTrue(json.contains("\"uri\": { \"kind\": \"attribute\""));
            assertTrue(json.contains("\"ref\": { \"kind\": \"attribute\""));
        }
    }

    @Test
    void testSplitDocumentation() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            String json = ((CatalogCamelContext) context).getEipParameterJsonSchema("split");
            LOG.info(json);
            assertNotNull("Should have found json for split", json);

            assertTrue(json.contains("\"name\": \"split\""));
            // there should be javadoc included
            assertTrue(json.contains("If enabled then processing each splitted messages occurs concurrently."));
            // and it support outputs
            assertTrue(json.contains(
                    "\"outputs\": { \"kind\": \"element\", \"displayName\": \"Outputs\", \"required\": true, \"type\": \"array\", \"javaType\""));
        }
    }

    @Test
    void testSimpleDocumentation() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            String json = ((CatalogCamelContext) context).getEipParameterJsonSchema("simple");
            LOG.info(json);
            assertNotNull("Should have found json for simple", json);

            assertTrue(json.contains("\"label\": \"language,core,java\""));
            assertTrue(json.contains("\"name\": \"simple\""));
        }
    }

    @Test
    void testFailOverDocumentation() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            String json = ((CatalogCamelContext) context).getEipParameterJsonSchema("failover");
            LOG.info(json);
            assertNotNull("Should have found json for failover", json);

            assertTrue(json.contains("\"name\": \"failover\""));
            assertTrue(json.contains(
                    "\"exception\": { \"kind\": \"element\", \"displayName\": \"Exception\", \"required\": false, \"type\": \"array\""
                                     + ", \"javaType\": \"java.util.List<java.lang.String>\", \"deprecated\": false"));
        }
    }

    @Test
    void testNotFound() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            String json = ((CatalogCamelContext) context).getEipParameterJsonSchema("unknown");
            assertNull(json, "Should not have found json for unknown");
        }
    }
}
