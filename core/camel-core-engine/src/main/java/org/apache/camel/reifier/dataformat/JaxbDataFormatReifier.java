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
import org.apache.camel.model.dataformat.JaxbDataFormat;

public class JaxbDataFormatReifier extends DataFormatReifier<JaxbDataFormat> {

    public JaxbDataFormatReifier(CamelContext camelContext, DataFormatDefinition definition) {
        super(camelContext, (JaxbDataFormat)definition);
    }

    @Override
    protected void prepareDataFormatConfig(Map<String, Object> properties) {
        properties.put("prettyPrint", definition.getPrettyPrint());
        properties.put("objectFactory", definition.getObjectFactory());
        properties.put("ignoreJAXBElement", definition.getIgnoreJAXBElement());
        properties.put("mustBeJAXBElement", definition.getMustBeJAXBElement());
        properties.put("filterNonXmlChars", definition.getFilterNonXmlChars());
        properties.put("fragment", definition.getFragment());
        properties.put("contextPath", definition.getContextPath());
        properties.put("partClass", definition.getPartClass());
        properties.put("partNamespace", definition.getPartNamespace());
        properties.put("encoding", definition.getEncoding());
        properties.put("namespacePrefix", asRef(definition.getNamespacePrefixRef()));
        properties.put("schema", definition.getSchema());
        properties.put("schemaSeverityLevel", definition.getSchemaSeverityLevel());
        properties.put("xmlStreamWriterWrapper", definition.getXmlStreamWriterWrapper());
        properties.put("schemaLocation", definition.getSchemaLocation());
        properties.put("noNamespaceSchemaLocation", definition.getNoNamespaceSchemaLocation());
        properties.put("jaxbProviderProperties", definition.getJaxbProviderProperties());
    }

}
