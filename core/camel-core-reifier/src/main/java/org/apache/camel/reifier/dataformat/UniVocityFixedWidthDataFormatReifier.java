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
import org.apache.camel.model.dataformat.UniVocityFixedWidthDataFormat;
import org.apache.camel.model.dataformat.UniVocityHeader;

public class UniVocityFixedWidthDataFormatReifier extends UniVocityAbstractDataFormatReifier<UniVocityFixedWidthDataFormat> {

    public UniVocityFixedWidthDataFormatReifier(CamelContext camelContext, DataFormatDefinition definition) {
        super(camelContext, definition);
    }

    @Override
    protected void prepareDataFormatConfig(Map<String, Object> properties) {
        super.prepareDataFormatConfig(properties);
        properties.put("fieldLengths", getFieldLengths());
        properties.put("skipTrailingCharsUntilNewline", definition.getSkipTrailingCharsUntilNewline());
        properties.put("recordEndsOnNewline", definition.getRecordEndsOnNewline());
        properties.put("padding", definition.getPadding());
    }

    private int[] getFieldLengths() {
        if (definition.getHeaders() != null) {
            int i = 0;
            int[] arr = new int[definition.getHeaders().size()];
            for (UniVocityHeader header : definition.getHeaders()) {
                String len = header.getLength();
                int num = Integer.parseInt(len);
                arr[i++] = num;
            }
            return arr;
        } else {
            return null;
        }
    }

}
