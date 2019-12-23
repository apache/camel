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
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.dataformat.XStreamDataFormat;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

public class XStreamDataFormatReifier extends DataFormatReifier<XStreamDataFormat> {

    public XStreamDataFormatReifier(DataFormatDefinition definition) {
        super((XStreamDataFormat)definition);
    }

    @Override
    protected DataFormat doCreateDataFormat(CamelContext camelContext) {
        if ("json".equals(definition.getDriver())) {
            definition.setDataFormatName("json-xstream");
        }
        DataFormat answer = super.doCreateDataFormat(camelContext);
        // need to lookup the reference for the xstreamDriver
        if (ObjectHelper.isNotEmpty(definition.getDriverRef())) {
            setProperty(camelContext, answer, "xstreamDriver", CamelContextHelper.mandatoryLookup(camelContext, definition.getDriverRef()));
        }
        return answer;
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        if (definition.getPermissions() != null) {
            setProperty(camelContext, dataFormat, "permissions", definition.getPermissions());
        }
        if (definition.getEncoding() != null) {
            setProperty(camelContext, dataFormat, "encoding", definition.getEncoding());
        }
        if (definition.getConverters() != null) {
            setProperty(camelContext, dataFormat, "converters", definition.getConverters());
        }
        if (definition.getAliases() != null) {
            setProperty(camelContext, dataFormat, "aliases", definition.getAliases());
        }
        if (definition.getOmitFields() != null) {
            setProperty(camelContext, dataFormat, "omitFields", definition.getOmitFields());
        }
        if (definition.getImplicitCollections() != null) {
            setProperty(camelContext, dataFormat, "implicitCollections", definition.getImplicitCollections());
        }
        if (definition.getMode() != null) {
            setProperty(camelContext, dataFormat, "mode", definition.getMode());
        }
    }

}
