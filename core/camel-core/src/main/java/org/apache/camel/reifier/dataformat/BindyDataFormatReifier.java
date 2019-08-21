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
import org.apache.camel.model.dataformat.BindyDataFormat;
import org.apache.camel.model.dataformat.BindyType;
import org.apache.camel.spi.DataFormat;

public class BindyDataFormatReifier extends DataFormatReifier<BindyDataFormat> {

    public BindyDataFormatReifier(DataFormatDefinition definition) {
        super((BindyDataFormat)definition);
    }

    @Override
    protected DataFormat doCreateDataFormat(CamelContext camelContext) {
        if (definition.getClassTypeAsString() == null && definition.getClassType() == null) {
            throw new IllegalArgumentException("Either packages or classType must be specified");
        }

        if (definition.getType() == BindyType.Csv) {
            definition.setDataFormatName("bindy-csv");
        } else if (definition.getType() == BindyType.Fixed) {
            definition.setDataFormatName("bindy-fixed");
        } else {
            definition.setDataFormatName("bindy-kvp");
        }

        if (definition.getClassType() == null && definition.getClassTypeAsString() != null) {
            try {
                definition.setClassType(camelContext.getClassResolver().resolveMandatoryClass(definition.getClassTypeAsString()));
            } catch (ClassNotFoundException e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }
        return super.doCreateDataFormat(camelContext);
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        setProperty(camelContext, dataFormat, "locale", definition.getLocale());
        setProperty(camelContext, dataFormat, "classType", definition.getClassType());
        if (definition.getUnwrapSingleInstance() != null) {
            setProperty(camelContext, dataFormat, "unwrapSingleInstance", definition.getUnwrapSingleInstance());
        }
    }

}
