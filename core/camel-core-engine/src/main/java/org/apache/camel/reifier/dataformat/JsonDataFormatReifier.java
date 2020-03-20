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
import org.apache.camel.model.dataformat.JsonDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;

public class JsonDataFormatReifier extends DataFormatReifier<JsonDataFormat> {

    public JsonDataFormatReifier(CamelContext camelContext, DataFormatDefinition definition) {
        super(camelContext, (JsonDataFormat)definition);
    }

    @Override
    protected void prepareDataFormatConfig(Map<String, Object> properties) {
        properties.put("objectMapper", asRef(definition.getObjectMapper()));
        if (definition.getLibrary() == JsonLibrary.Jackson) {
            if (definition.getUseDefaultObjectMapper() == null) {
                // default true
                properties.put("useDefaultObjectMapper", "true");
            } else {
                properties.put("useDefaultObjectMapper", definition.getUseDefaultObjectMapper());
            }
        }
        properties.put("unmarshalType", or(definition.getUnmarshalType(), definition.getUnmarshalTypeName()));
        properties.put("prettyPrint", definition.getPrettyPrint());
        properties.put("jsonView", definition.getJsonView());
        properties.put("include", definition.getInclude());
        properties.put("allowJmsType", definition.getAllowJmsType());
        properties.put("collectionType", or(definition.getCollectionType(), definition.getCollectionTypeName()));
        properties.put("useList", definition.getUseList());
        properties.put("enableJaxbAnnotationModule", definition.getEnableJaxbAnnotationModule());
        properties.put("moduleClassNames", definition.getModuleClassNames());
        properties.put("moduleRefs", definition.getModuleRefs());
        properties.put("enableFeatures", definition.getEnableFeatures());
        properties.put("disableFeatures", definition.getDisableFeatures());
        properties.put("allowUnmarshallType", definition.getAllowUnmarshallType());
        if (definition.getLibrary() == JsonLibrary.XStream) {
            properties.put("dropRootNode", definition.getDropRootNode());
        }
        if (definition.getLibrary() == JsonLibrary.XStream && definition.getPermissions() == null) {
            // if we have the unmarshal type, but no permission set, then use it to be allowed
            String type = definition.getUnmarshalTypeName();
            if (type == null && definition.getUnmarshalType() != null) {
                type = definition.getUnmarshalType().getName();
            }
            properties.put("permissions", type);
            // xstream has no unmarshalType option
            properties.remove("unmarshalType");
        }
    }

}
