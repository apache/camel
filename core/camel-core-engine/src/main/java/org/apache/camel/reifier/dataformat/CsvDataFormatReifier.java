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
import org.apache.camel.model.dataformat.CsvDataFormat;

public class CsvDataFormatReifier extends DataFormatReifier<CsvDataFormat> {

    public CsvDataFormatReifier(CamelContext camelContext, DataFormatDefinition definition) {
        super(camelContext, (CsvDataFormat)definition);
    }

    @Override
    protected void prepareDataFormatConfig(Map<String, Object> properties) {
        properties.put("format", asRef(definition.getFormatRef()));
        properties.put("formatName", definition.getFormatName());
        properties.put("commentMarkerDisabled", definition.getCommentMarkerDisabled());
        properties.put("commentMarker", definition.getCommentMarker());
        properties.put("delimiter", definition.getDelimiter());
        properties.put("escapeDisabled", definition.getEscapeDisabled());
        properties.put("escape", definition.getEscape());
        properties.put("headerDisabled", definition.getHeaderDisabled());
        properties.put("header", definition.getHeader());
        properties.put("allowMissingColumnNames", definition.getAllowMissingColumnNames());
        properties.put("ignoreEmptyLines", definition.getIgnoreEmptyLines());
        properties.put("ignoreSurroundingSpaces", definition.getIgnoreSurroundingSpaces());
        properties.put("nullStringDisabled", definition.getNullStringDisabled());
        properties.put("nullString", definition.getNullString());
        properties.put("quoteDisabled", definition.getQuoteDisabled());
        properties.put("quote", definition.getQuote());
        properties.put("recordSeparatorDisabled", definition.getRecordSeparatorDisabled());
        properties.put("recordSeparator", definition.getRecordSeparator());
        properties.put("skipHeaderRecord", definition.getSkipHeaderRecord());
        properties.put("quoteMode", definition.getQuoteMode());
        properties.put("trim", definition.getTrim());
        properties.put("ignoreHeaderCase", definition.getIgnoreHeaderCase());
        properties.put("trailingDelimiter", definition.getTrailingDelimiter());
        properties.put("lazyLoad", definition.getLazyLoad());
        properties.put("useMaps", definition.getUseMaps());
        properties.put("useOrderedMaps", definition.getUseOrderedMaps());
        properties.put("recordConverter", asRef(definition.getRecordConverterRef()));
        properties.put("marshallerFactory", asRef(definition.getMarshallerFactoryRef()));
    }

}
