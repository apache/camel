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
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.DataFormatModel.DataFormatOptionModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.junit.Test;

public class DataFormatComponentConfigurationAndDocumentationTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testFlatpackDefaultValue() throws Exception {
        CamelContext context = new DefaultCamelContext();
        String json = context.adapt(CatalogCamelContext.class).getEipParameterJsonSchema("flatpack");
        assertNotNull(json);

        DataFormatModel model = JsonMapper.generateDataFormatModel(json);
        assertEquals("flatpack", model.getName());

        Map<String, DataFormatOptionModel> options = model.getOptions().stream()
                .collect(Collectors.toMap(BaseOptionModel::getName, o -> o));

        assertEquals(10, options.size());
        BaseOptionModel found = options.get("textQualifier");
        assertNotNull(found);
        assertEquals("textQualifier", found.getName());
        assertEquals("attribute", found.getKind());
        assertEquals(false, found.isRequired());
        assertEquals("string", found.getType());
        assertEquals("java.lang.String", found.getJavaType());
        assertEquals(false, found.isDeprecated());
        assertEquals(false, found.isSecret());
        assertEquals("If the text is qualified with a character. Uses quote character by default.", found.getDescription());
    }

    @Test
    public void testUniVocityTsvEscapeChar() throws Exception {
        CamelContext context = new DefaultCamelContext();
        String json = context.adapt(CatalogCamelContext.class).getEipParameterJsonSchema("univocity-tsv");
        assertNotNull(json);

        DataFormatModel model = JsonMapper.generateDataFormatModel(json);
        assertEquals("univocity-tsv", model.getName());

        Map<String, DataFormatOptionModel> options = model.getOptions().stream()
                .collect(Collectors.toMap(BaseOptionModel::getName, o -> o));

        assertEquals(16, options.size());
        BaseOptionModel found = options.get("escapeChar");
        assertNotNull(found);
        assertEquals("escapeChar", found.getName());
        assertEquals("attribute", found.getKind());
        assertEquals(false, found.isRequired());
        assertEquals("string", found.getType());
        assertEquals("java.lang.String", found.getJavaType());
        assertEquals(false, found.isDeprecated());
        assertEquals(false, found.isSecret());
        assertEquals("\\", found.getDefaultValue());
        assertEquals("The escape character.", found.getDescription());
    }

}
