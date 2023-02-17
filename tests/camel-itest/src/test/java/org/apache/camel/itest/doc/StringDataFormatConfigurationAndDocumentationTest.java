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
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "enable.documentation.itests", matches = "true")
public class StringDataFormatConfigurationAndDocumentationTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(EipDocumentationTest.class);

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testDataFormatJsonSchema() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            String json = ((CatalogCamelContext) context).getDataFormatParameterJsonSchema("string");
            assertNotNull(json, "Should have found some auto-generated JSON");
            LOG.info(json);

            assertTrue(json.contains("\"name\": \"string\""));
            assertTrue(json.contains("\"modelName\": \"string\""));
            assertTrue(json.contains(
                    "\"charset\": { \"kind\": \"attribute\", \"displayName\": \"Charset\", \"required\": false, \"type\": \"string\""));
        }
    }

}
