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
import org.apache.camel.model.dataformat.JibxDataFormat;
import org.apache.camel.spi.DataFormat;

public class JibxDataFormatReifier extends DataFormatReifier<JibxDataFormat> {

    public JibxDataFormatReifier(DataFormatDefinition definition) {
        super((JibxDataFormat) definition);
    }

    @Override
    protected DataFormat doCreateDataFormat(CamelContext camelContext) {
        if (definition.getUnmarshallClass() == null && definition.getUnmarshallTypeName() != null) {
            try {
                definition.setUnmarshallClass(camelContext.getClassResolver().resolveMandatoryClass(definition.getUnmarshallTypeName()));
            } catch (ClassNotFoundException e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }

        return super.createDataFormat(camelContext);
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        if (definition.getUnmarshallClass() != null) {
            setProperty(camelContext, dataFormat, "unmarshallClass", definition.getUnmarshallClass());
        }
        if (definition.getBindingName() != null) {
            setProperty(camelContext, dataFormat, "bindingName", definition.getBindingName());
        }
    }

}
