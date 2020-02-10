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
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.dataformat.UniVocityAbstractDataFormat;
import org.apache.camel.model.dataformat.UniVocityHeader;

public class UniVocityAbstractDataFormatReifier<T extends UniVocityAbstractDataFormat> extends DataFormatReifier<T> {

    public UniVocityAbstractDataFormatReifier(CamelContext camelContext, DataFormatDefinition definition) {
        super(camelContext, (T)definition);
    }

    @Override
    protected void prepareDataFormatConfig(Map<String, Object> properties) {
        properties.put("nullValue", definition.getNullValue());
        properties.put("skipEmptyLines", definition.getSkipEmptyLines());
        properties.put("ignoreTrailingWhitespaces", definition.getIgnoreTrailingWhitespaces());
        properties.put("ignoreLeadingWhitespaces", definition.getIgnoreLeadingWhitespaces());
        properties.put("headersDisabled", definition.getHeadersDisabled());
        properties.put("headers", getValidHeaderNames());
        properties.put("headerExtractionEnabled", definition.getHeaderExtractionEnabled());
        properties.put("numberOfRecordsToRead", definition.getNumberOfRecordsToRead());
        properties.put("emptyValue", definition.getEmptyValue());
        properties.put("lineSeparator", definition.getLineSeparator());
        properties.put("normalizedLineSeparator", definition.getNormalizedLineSeparator());
        properties.put("comment", definition.getComment());
        properties.put("lazyLoad", definition.getLazyLoad());
        properties.put("asMap", definition.getAsMap());
    }

    /**
     * Gets only the headers with non-null and non-empty names. It returns
     * {@code null} if there's no such headers.
     *
     * @return The headers with non-null and non-empty names
     */
    private String[] getValidHeaderNames() {
        if (definition.getHeaders() == null) {
            return null;
        }
        List<String> names = new ArrayList<>(definition.getHeaders().size());
        for (UniVocityHeader header : definition.getHeaders()) {
            if (header.getName() != null && !header.getName().isEmpty()) {
                names.add(header.getName());
            }
        }
        return names.isEmpty() ? null : names.toArray(new String[0]);
    }
}
