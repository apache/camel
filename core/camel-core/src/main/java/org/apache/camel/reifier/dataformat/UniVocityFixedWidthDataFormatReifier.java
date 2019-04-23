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
import org.apache.camel.model.dataformat.UniVocityFixedWidthDataFormat;
import org.apache.camel.spi.DataFormat;

public class UniVocityFixedWidthDataFormatReifier extends UniVocityAbstractDataFormatReifier<UniVocityFixedWidthDataFormat> {

    public UniVocityFixedWidthDataFormatReifier(DataFormatDefinition definition) {
        super(definition);
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        super.configureDataFormat(dataFormat, camelContext);

        if (definition.getHeaders() != null) {
            int[] lengths = new int[definition.getHeaders().size()];
            for (int i = 0; i < lengths.length; i++) {
                Integer length = definition.getHeaders().get(i).getLength();
                if (length == null) {
                    throw new IllegalArgumentException("The length of all headers must be defined.");
                }
                lengths[i] = length;
            }
            setProperty(camelContext, dataFormat, "fieldLengths", lengths);
        }
        if (definition.getSkipTrailingCharsUntilNewline() != null) {
            setProperty(camelContext, dataFormat, "skipTrailingCharsUntilNewline", definition.getSkipTrailingCharsUntilNewline());
        }
        if (definition.getRecordEndsOnNewline() != null) {
            setProperty(camelContext, dataFormat, "recordEndsOnNewline", definition.getRecordEndsOnNewline());
        }
        if (definition.getPadding() != null) {
            setProperty(camelContext, dataFormat, "padding", singleCharOf("padding", definition.getPadding()));
        }
    }

}
