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
package org.apache.camel.dataformat.univocity;

import java.io.Writer;

import com.univocity.parsers.tsv.TsvFormat;
import com.univocity.parsers.tsv.TsvParser;
import com.univocity.parsers.tsv.TsvParserSettings;
import com.univocity.parsers.tsv.TsvWriter;
import com.univocity.parsers.tsv.TsvWriterSettings;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.annotations.Dataformat;

/**
 * This class is the data format that uses the TSV uniVocity parser.
 */
@Dataformat("univocityTsv")
@BodyTypes(marshalIn = @BodyType(value = { Carrier.MAP, Carrier.LIST },
                                 description = "One row as a Map, or the rows as a List (or an Iterator) of Maps. Rows that are"
                                               + " Lists, as unmarshal gives without asMap, are not accepted"),
           marshalOut = @BodyType(value = Carrier.STREAM, format = "text",
                                  description = "The TSV in the stream cache, or a byte array when stream caching"
                                                + " is off"),
           unmarshalIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE }, format = "text"),
           unmarshalOut = @BodyType(value = Carrier.LIST,
                                    byOption = @BodyType.When(option = "lazyLoad", value = "true",
                                                              carriers = Carrier.ITERATOR),
                                    description = "A List of the rows, each a List of its values (the first line too, unless"
                                                  + " headerExtractionEnabled); with asMap, a Map of the header names and"
                                                  + " the values"))
public class UniVocityTsvDataFormat
        extends
        AbstractUniVocityDataFormat<TsvFormat, TsvWriterSettings, TsvWriter, TsvParserSettings, TsvParser, UniVocityTsvDataFormat> {

    private Character escapeChar;

    public Character getEscapeChar() {
        return escapeChar;
    }

    public void setEscapeChar(Character escapeChar) {
        this.escapeChar = escapeChar;
    }

    @Override
    protected TsvWriterSettings createWriterSettings() {
        return new TsvWriterSettings();
    }

    @Override
    protected TsvWriter createWriter(Writer writer, TsvWriterSettings settings) {
        return new TsvWriter(writer, settings);
    }

    @Override
    protected TsvParserSettings createParserSettings() {
        return new TsvParserSettings();
    }

    @Override
    protected TsvParser createParser(TsvParserSettings settings) {
        return new TsvParser(settings);
    }

    @Override
    protected void configureFormat(TsvFormat format) {
        super.configureFormat(format);

        if (escapeChar != null) {
            format.setEscapeChar(escapeChar);
        }
    }

    @Override
    public String getDataFormatName() {
        return "univocityTsv";
    }
}
