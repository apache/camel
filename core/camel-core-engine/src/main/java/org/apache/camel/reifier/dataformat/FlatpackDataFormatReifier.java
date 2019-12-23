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
import org.apache.camel.model.dataformat.FlatpackDataFormat;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

public class FlatpackDataFormatReifier extends DataFormatReifier<FlatpackDataFormat> {

    public FlatpackDataFormatReifier(DataFormatDefinition definition) {
        super((FlatpackDataFormat)definition);
    }

    @Override
    protected DataFormat doCreateDataFormat(CamelContext camelContext) {
        DataFormat flatpack = super.doCreateDataFormat(camelContext);

        if (ObjectHelper.isNotEmpty(definition.getParserFactoryRef())) {
            Object parserFactory = CamelContextHelper.mandatoryLookup(camelContext, definition.getParserFactoryRef());
            setProperty(camelContext, flatpack, "parserFactory", parserFactory);
        }

        return flatpack;
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        if (ObjectHelper.isNotEmpty(definition.getDefinition())) {
            setProperty(camelContext, dataFormat, "definition", definition.getDefinition());
        }
        if (definition.getFixed() != null) {
            setProperty(camelContext, dataFormat, "fixed", definition.getFixed());
        }
        if (definition.getIgnoreFirstRecord() != null) {
            setProperty(camelContext, dataFormat, "ignoreFirstRecord", definition.getIgnoreFirstRecord());
        }
        if (ObjectHelper.isNotEmpty(definition.getTextQualifier())) {
            if (definition.getTextQualifier().length() > 1) {
                throw new IllegalArgumentException("Text qualifier must be one character long!");
            }
            setProperty(camelContext, dataFormat, "textQualifier", definition.getTextQualifier().charAt(0));
        }
        if (ObjectHelper.isNotEmpty(definition.getDelimiter())) {
            if (definition.getDelimiter().length() > 1) {
                throw new IllegalArgumentException("Delimiter must be one character long!");
            }
            setProperty(camelContext, dataFormat, "delimiter", definition.getDelimiter().charAt(0));
        }
        if (definition.getAllowShortLines() != null) {
            setProperty(camelContext, dataFormat, "allowShortLines", definition.getAllowShortLines());
        }
        if (definition.getIgnoreExtraColumns() != null) {
            setProperty(camelContext, dataFormat, "ignoreExtraColumns", definition.getIgnoreExtraColumns());
        }
    }

}
