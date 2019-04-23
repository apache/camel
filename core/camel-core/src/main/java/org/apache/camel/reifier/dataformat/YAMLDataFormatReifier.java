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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.dataformat.YAMLDataFormat;
import org.apache.camel.model.dataformat.YAMLLibrary;
import org.apache.camel.model.dataformat.YAMLTypeFilterDefinition;
import org.apache.camel.model.dataformat.YAMLTypeFilterType;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.ObjectHelper;

public class YAMLDataFormatReifier extends DataFormatReifier<YAMLDataFormat> {

    public YAMLDataFormatReifier(DataFormatDefinition definition) {
        super((YAMLDataFormat) definition);
    }

    @Override
    protected DataFormat doCreateDataFormat(CamelContext camelContext) {
        if (definition.getLibrary() == YAMLLibrary.SnakeYAML) {
            setProperty(camelContext, this, "dataFormatName", "yaml-snakeyaml");
        }

        return super.doCreateDataFormat(camelContext);
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        if (definition.getLibrary() == YAMLLibrary.SnakeYAML) {
            configureSnakeDataFormat(dataFormat, camelContext);
        }
    }

    protected void configureSnakeDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        Class<?> yamlUnmarshalType =  definition.getUnmarshalType();
        if (yamlUnmarshalType == null && definition.getUnmarshalTypeName() != null) {
            try {
                yamlUnmarshalType = camelContext.getClassResolver().resolveMandatoryClass(definition.getUnmarshalTypeName());
            } catch (ClassNotFoundException e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }

        setProperty(dataFormat, camelContext, "unmarshalType", yamlUnmarshalType);
        setProperty(dataFormat, camelContext, "classLoader", definition.getClassLoader());
        setProperty(dataFormat, camelContext, "useApplicationContextClassLoader", definition.isUseApplicationContextClassLoader());
        setProperty(dataFormat, camelContext, "prettyFlow", definition.isPrettyFlow());
        setProperty(dataFormat, camelContext, "allowAnyType", definition.isAllowAnyType());

        if (definition.getTypeFilters() != null && !definition.getTypeFilters().isEmpty()) {
            List<String> typeFilterDefinitions = new ArrayList<>(definition.getTypeFilters().size());
            for (YAMLTypeFilterDefinition definition : definition.getTypeFilters()) {
                String value = definition.getValue();

                if (!value.startsWith("type") && !value.startsWith("regexp")) {
                    YAMLTypeFilterType type = definition.getType();
                    if (type == null) {
                        type = YAMLTypeFilterType.type;
                    }

                    value = type.name() + ":" + value;
                }

                typeFilterDefinitions.add(value);
            }

            setProperty(dataFormat, camelContext, "typeFilterDefinitions", typeFilterDefinitions);
        }

        setPropertyRef(dataFormat, camelContext, "constructor", definition.getConstructor());
        setPropertyRef(dataFormat, camelContext, "representer", definition.getRepresenter());
        setPropertyRef(dataFormat, camelContext, "dumperOptions", definition.getDumperOptions());
        setPropertyRef(dataFormat, camelContext, "resolver", definition.getResolver());
    }

    protected void setProperty(DataFormat dataFormat, CamelContext camelContext, String propertyName, Object propertyValue) {
        if (ObjectHelper.isNotEmpty(propertyValue)) {
            setProperty(camelContext, dataFormat, propertyName, propertyValue);
        }
    }

    protected void setPropertyRef(DataFormat dataFormat, CamelContext camelContext, String propertyName, String propertyValue) {
        if (ObjectHelper.isNotEmpty(propertyValue)) {
            // must be a reference value
            String ref = propertyValue.startsWith("#") ? propertyValue : "#" + propertyValue;
            setProperty(camelContext, dataFormat, propertyName, ref);
        }
    }

}
