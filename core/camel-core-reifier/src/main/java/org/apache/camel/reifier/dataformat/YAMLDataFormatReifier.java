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
import org.apache.camel.model.dataformat.YAMLDataFormat;
import org.apache.camel.model.dataformat.YAMLLibrary;

public class YAMLDataFormatReifier extends DataFormatReifier<YAMLDataFormat> {

    public YAMLDataFormatReifier(CamelContext camelContext, DataFormatDefinition definition) {
        super(camelContext, (YAMLDataFormat) definition);
    }

    @Override
    protected void prepareDataFormatConfig(Map<String, Object> properties) {
        if (definition.getLibrary() == YAMLLibrary.SnakeYAML) {
            configureSnakeDataFormat(properties);
        }
    }

    protected void configureSnakeDataFormat(Map<String, Object> properties) {
        properties.put("unmarshalType", or(definition.getUnmarshalType(), definition.getUnmarshalTypeName()));
        properties.put("classLoader", definition.getClassLoader());
        properties.put("useApplicationContextClassLoader", definition.getUseApplicationContextClassLoader());
        properties.put("prettyFlow", definition.getPrettyFlow());
        properties.put("allowAnyType", definition.getAllowAnyType());
        properties.put("typeFilters", definition.getTypeFilter());
        properties.put("constructor", definition.getConstructor());
        properties.put("representer", definition.getRepresenter());
        properties.put("dumperOptions", definition.getDumperOptions());
        properties.put("resolver", definition.getResolver());
        properties.put("maxAliasesForCollections", definition.getMaxAliasesForCollections());
        properties.put("allowRecursiveKeys", definition.getAllowRecursiveKeys());
    }
}
