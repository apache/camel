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
package org.apache.camel.reifier.dataformat;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.dataformat.AvroDataFormat;
import org.apache.camel.model.dataformat.AvroLibrary;

public class AvroDataFormatReifier extends DataFormatReifier<AvroDataFormat> {

    public AvroDataFormatReifier(CamelContext camelContext, DataFormatDefinition definition) {
        super(camelContext, (AvroDataFormat) definition);
    }

    @Override
    protected void prepareDataFormatConfig(Map<String, Object> properties) {
        if (definition.getLibrary() == AvroLibrary.ApacheAvro) {
            if (definition.getInstanceClassName() == null) {
                if (definition.getUnmarshalType() != null) {
                    properties.put("instanceClassName", definition.getUnmarshalType().getName());
                } else if (definition.getUnmarshalTypeName() != null) {
                    properties.put("instanceClassName", definition.getUnmarshalTypeName());
                }
            } else {
                properties.put("instanceClassName", definition.getInstanceClassName());
            }
            properties.put("schema", definition.getSchema());
        } else if (definition.getLibrary() == AvroLibrary.Jackson) {
            properties.put("objectMapper", asRef(definition.getObjectMapper()));
            if (definition.getUseDefaultObjectMapper() == null) {
                // default true
                properties.put("useDefaultObjectMapper", "true");
            } else {
                properties.put("useDefaultObjectMapper", definition.getUseDefaultObjectMapper());
            }
            properties.put("autoDiscoverObjectMapper", definition.getAutoDiscoverObjectMapper());
            properties.put("unmarshalType", or(
                    or(definition.getUnmarshalType(), definition.getUnmarshalTypeName()), definition.getInstanceClassName()));
            properties.put("jsonView", or(definition.getJsonView(), definition.getJsonViewTypeName()));
            properties.put("include", definition.getInclude());
            properties.put("allowJmsType", definition.getAllowJmsType());
            properties.put("collectionType", or(definition.getCollectionType(), definition.getCollectionTypeName()));
            properties.put("useList", definition.getUseList());
            properties.put("moduleClassNames", definition.getModuleClassNames());
            properties.put("moduleRefs", definition.getModuleRefs());
            properties.put("enableFeatures", definition.getEnableFeatures());
            properties.put("disableFeatures", definition.getDisableFeatures());
            properties.put("allowUnmarshallType", definition.getAllowUnmarshallType());
            properties.put("schemaResolver", asRef(definition.getSchemaResolver()));
            properties.put("autoDiscoverSchemaResolver", definition.getAutoDiscoverSchemaResolver());
        }
    }

}
