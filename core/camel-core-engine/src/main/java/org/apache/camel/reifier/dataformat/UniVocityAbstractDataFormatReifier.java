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

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.dataformat.UniVocityAbstractDataFormat;
import org.apache.camel.model.dataformat.UniVocityHeader;
import org.apache.camel.spi.DataFormat;

public class UniVocityAbstractDataFormatReifier<T extends UniVocityAbstractDataFormat> extends DataFormatReifier<T> {

    public UniVocityAbstractDataFormatReifier(DataFormatDefinition definition) {
        super((T)definition);
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        super.configureDataFormat(dataFormat, camelContext);

        if (definition.getNullValue() != null) {
            setProperty(camelContext, dataFormat, "nullValue", definition.getNullValue());
        }
        if (definition.getSkipEmptyLines() != null) {
            setProperty(camelContext, dataFormat, "skipEmptyLines", definition.getSkipEmptyLines());
        }
        if (definition.getIgnoreTrailingWhitespaces() != null) {
            setProperty(camelContext, dataFormat, "ignoreTrailingWhitespaces", definition.getIgnoreTrailingWhitespaces());
        }
        if (definition.getIgnoreLeadingWhitespaces() != null) {
            setProperty(camelContext, dataFormat, "ignoreLeadingWhitespaces", definition.getIgnoreLeadingWhitespaces());
        }
        if (definition.getHeadersDisabled() != null) {
            setProperty(camelContext, dataFormat, "headersDisabled", definition.getHeadersDisabled());
        }
        String[] validHeaderNames = getValidHeaderNames();
        if (validHeaderNames != null) {
            setProperty(camelContext, dataFormat, "headers", validHeaderNames);
        }
        if (definition.getHeaderExtractionEnabled() != null) {
            setProperty(camelContext, dataFormat, "headerExtractionEnabled", definition.getHeaderExtractionEnabled());
        }
        if (definition.getNumberOfRecordsToRead() != null) {
            setProperty(camelContext, dataFormat, "numberOfRecordsToRead", definition.getNumberOfRecordsToRead());
        }
        if (definition.getEmptyValue() != null) {
            setProperty(camelContext, dataFormat, "emptyValue", definition.getEmptyValue());
        }
        if (definition.getLineSeparator() != null) {
            setProperty(camelContext, dataFormat, "lineSeparator", definition.getLineSeparator());
        }
        if (definition.getNormalizedLineSeparator() != null) {
            setProperty(camelContext, dataFormat, "normalizedLineSeparator", singleCharOf("normalizedLineSeparator", definition.getNormalizedLineSeparator()));
        }
        if (definition.getComment() != null) {
            setProperty(camelContext, dataFormat, "comment", singleCharOf("comment", definition.getComment()));
        }
        if (definition.getLazyLoad() != null) {
            setProperty(camelContext, dataFormat, "lazyLoad", definition.getLazyLoad());
        }
        if (definition.getAsMap() != null) {
            setProperty(camelContext, dataFormat, "asMap", definition.getAsMap());
        }
    }

    protected static Character singleCharOf(String attributeName, String string) {
        if (string.length() != 1) {
            throw new IllegalArgumentException("Only one character must be defined for " + attributeName);
        }
        return string.charAt(0);
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
        return names.isEmpty() ? null : names.toArray(new String[names.size()]);
    }
}
