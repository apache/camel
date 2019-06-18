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

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.dataformat.CBORDataFormat;
import org.apache.camel.spi.DataFormat;

public class CBORDataFormatReifier extends DataFormatReifier<CBORDataFormat> {

    public CBORDataFormatReifier(DataFormatDefinition definition) {
        super((CBORDataFormat) definition);
    }

    @Override
    protected DataFormat doCreateDataFormat(CamelContext camelContext) {
        if (definition.getUnmarshalType() == null && definition.getUnmarshalTypeName() != null) {
            try {
                definition.setUnmarshalType(camelContext.getClassResolver().resolveMandatoryClass(definition.getUnmarshalTypeName()));
            } catch (ClassNotFoundException e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }
        if (definition.getCollectionType() == null && definition.getCollectionTypeName() != null) {
            try {
                definition.setCollectionType(camelContext.getClassResolver().resolveMandatoryClass(definition.getCollectionTypeName()));
            } catch (ClassNotFoundException e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }

        return super.doCreateDataFormat(camelContext);
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        if (definition.getObjectMapper() != null) {
            // must be a reference value
            String ref = definition.getObjectMapper().startsWith("#") ? definition.getObjectMapper() : "#" + definition.getObjectMapper();
            setProperty(camelContext, dataFormat, "xmlMapper", ref);
        }
        if (definition.getUnmarshalType() != null) {
            setProperty(camelContext, dataFormat, "unmarshalType", definition.getUnmarshalType());
        }
        if (definition.getCollectionTypeName() != null) {
            setProperty(camelContext, dataFormat, "collectionType", definition.getCollectionTypeName());
        }
        if (definition.getUseList() != null) {
            setProperty(camelContext, dataFormat, "useList", definition.getUseList());
        }
        if (definition.getAllowUnmarshallType() != null) {
            setProperty(camelContext, dataFormat, "allowUnmarshallType", definition.getAllowUnmarshallType());
        }
        if (definition.getPrettyPrint() != null) {
            setProperty(camelContext, dataFormat, "prettyPrint", definition.getPrettyPrint());
        }
        if (definition.getAllowJmsType() != null) {
            setProperty(camelContext, dataFormat, "allowJmsType", definition.getAllowJmsType());
        }
        if (definition.getEnableFeatures() != null) {
            setProperty(camelContext, dataFormat, "enableFeatures", definition.getEnableFeatures());
        }
        if (definition.getDisableFeatures() != null) {
            setProperty(camelContext, dataFormat, "disableFeatures", definition.getDisableFeatures());
        }
    }

}
