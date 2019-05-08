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
import javax.xml.namespace.QName;

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.dataformat.JaxbDataFormat;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

public class JaxbDataFormatReifier extends DataFormatReifier<JaxbDataFormat> {

    public JaxbDataFormatReifier(DataFormatDefinition definition) {
        super((JaxbDataFormat) definition);
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        Boolean answer = ObjectHelper.toBoolean(definition.getPrettyPrint());
        if (answer != null && !answer) {
            setProperty(camelContext, dataFormat, "prettyPrint", Boolean.FALSE);
        } else { // the default value is true
            setProperty(camelContext, dataFormat, "prettyPrint", Boolean.TRUE);
        }
        answer = ObjectHelper.toBoolean(definition.getObjectFactory());
        if (answer != null && !answer) {
            setProperty(camelContext, dataFormat, "objectFactory", Boolean.FALSE);
        } else { // the default value is true
            setProperty(camelContext, dataFormat, "objectFactory", Boolean.TRUE);
        }
        answer = ObjectHelper.toBoolean(definition.getIgnoreJAXBElement());
        if (answer != null && !answer) {
            setProperty(camelContext, dataFormat, "ignoreJAXBElement", Boolean.FALSE);
        } else { // the default value is true
            setProperty(camelContext, dataFormat, "ignoreJAXBElement", Boolean.TRUE);
        }
        answer = ObjectHelper.toBoolean(definition.getMustBeJAXBElement());
        if (answer != null && answer) {
            setProperty(camelContext, dataFormat, "mustBeJAXBElement", Boolean.TRUE);
        } else { // the default value is false
            setProperty(camelContext, dataFormat, "mustBeJAXBElement", Boolean.FALSE);
        }
        answer = ObjectHelper.toBoolean(definition.getFilterNonXmlChars());
        if (answer != null && answer) {
            setProperty(camelContext, dataFormat, "filterNonXmlChars", Boolean.TRUE);
        } else { // the default value is false
            setProperty(camelContext, dataFormat, "filterNonXmlChars", Boolean.FALSE);
        }
        answer = ObjectHelper.toBoolean(definition.getFragment());
        if (answer != null && answer) {
            setProperty(camelContext, dataFormat, "fragment", Boolean.TRUE);
        } else { // the default value is false
            setProperty(camelContext, dataFormat, "fragment", Boolean.FALSE);
        }

        setProperty(camelContext, dataFormat, "contextPath", definition.getContextPath());
        if (definition.getPartClass() != null) {
            setProperty(camelContext, dataFormat, "partClass", definition.getPartClass());
        }
        if (definition.getPartNamespace() != null) {
            setProperty(camelContext, dataFormat, "partNamespace", QName.valueOf(definition.getPartNamespace()));
        }
        if (definition.getEncoding() != null) {
            setProperty(camelContext, dataFormat, "encoding", definition.getEncoding());
        }
        if (definition.getNamespacePrefixRef() != null) {
            setProperty(camelContext, dataFormat, "namespacePrefixRef", definition.getNamespacePrefixRef());
        }
        if (definition.getSchema() != null) {
            setProperty(camelContext, dataFormat, "schema", definition.getSchema());
        }
        if (definition.getSchemaSeverityLevel() != null) {
            setProperty(camelContext, dataFormat, "schemaSeverityLevel", definition.getSchemaSeverityLevel());
        }
        if (definition.getXmlStreamWriterWrapper() != null) {
            setProperty(camelContext, dataFormat, "xmlStreamWriterWrapper", definition.getXmlStreamWriterWrapper());
        }
        if (definition.getSchemaLocation() != null) {
            setProperty(camelContext, dataFormat, "schemaLocation", definition.getSchemaLocation());
        }
        if (definition.getNoNamespaceSchemaLocation() != null) {
            setProperty(camelContext, dataFormat, "noNamespaceSchemaLocation", definition.getNoNamespaceSchemaLocation());
        }
        if (definition.getJaxbProviderProperties() != null) {
            Map map = CamelContextHelper.mandatoryLookup(camelContext, definition.getJaxbProviderProperties(), Map.class);
            setProperty(camelContext, dataFormat, "jaxbProviderProperties", map);
        }
    }

}
