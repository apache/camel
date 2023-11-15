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

import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.CatalogCamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.DataFormatModel.DataFormatOptionModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnabledIfSystemProperty(named = "enable.documentation.itests", matches = "true")
public class DataFormatComponentConfigurationAndDocumentationTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testFlatpackDefaultValue() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            String json = ((CatalogCamelContext) context).getEipParameterJsonSchema("flatpack");
            assertNotNull(json);

            DataFormatModel model = JsonMapper.generateDataFormatModel(json);
            assertEquals("flatpack", model.getName());

            Map<String, DataFormatOptionModel> options
                    = model.getOptions().stream().collect(Collectors.toMap(BaseOptionModel::getName, o -> o));

            assertEquals(10, options.size());
            BaseOptionModel found = options.get("textQualifier");
            assertNotNull(found);
            assertEquals("textQualifier", found.getName());
            assertEquals("attribute", found.getKind());
            assertFalse(found.isRequired());
            assertEquals("string", found.getType());
            assertEquals("java.lang.String", found.getJavaType());
            assertFalse(found.isDeprecated());
            assertFalse(found.isSecret());
            assertEquals("If the text is qualified with a character. Uses quote character by default.", found.getDescription());
        }
    }

    @Test
    void testUniVocityTsvEscapeChar() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            String json = ((CatalogCamelContext) context).getEipParameterJsonSchema("univocity-tsv");
            assertNotNull(json);

            DataFormatModel model = JsonMapper.generateDataFormatModel(json);
            assertEquals("univocity-tsv", model.getName());

            Map<String, DataFormatOptionModel> options
                    = model.getOptions().stream().collect(Collectors.toMap(BaseOptionModel::getName, o -> o));

            assertEquals(16, options.size());
            BaseOptionModel found = options.get("escapeChar");
            assertNotNull(found);
            assertEquals("escapeChar", found.getName());
            assertEquals("attribute", found.getKind());
            assertFalse(found.isRequired());
            assertEquals("string", found.getType());
            assertEquals("java.lang.String", found.getJavaType());
            assertFalse(found.isDeprecated());
            assertFalse(found.isSecret());
            assertEquals("\\", found.getDefaultValue());
            assertEquals("The escape character.", found.getDescription());
        }
    }

}
