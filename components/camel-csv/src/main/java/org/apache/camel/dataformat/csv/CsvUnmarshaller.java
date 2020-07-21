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
package org.apache.camel.dataformat.csv;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * This class unmarshal CSV into lists or maps depending on the configuration.
 */
abstract class CsvUnmarshaller {
    protected final CSVFormat format;
    protected final CsvRecordConverter<?> converter;

    private CsvUnmarshaller(CSVFormat format, CsvDataFormat dataFormat) {
        this.format = format;
        this.converter = extractConverter(dataFormat);
    }

    public static CsvUnmarshaller create(CSVFormat format, CsvDataFormat dataFormat) {
        // If we want to use maps, thus the header must be either fixed or automatic
        if ((dataFormat.isUseMaps() || dataFormat.isUseOrderedMaps()) && format.getHeader() == null) {
            format = format.withHeader();
        }
        // If we want to skip the header record it must automatic otherwise it's not working
        if (format.getSkipHeaderRecord() && format.getHeader() == null) {
            format = format.withHeader();
        }

        if (dataFormat.isLazyLoad()) {
            return new StreamCsvUnmarshaller(format, dataFormat);
        }
        return new BulkCsvUnmarshaller(format, dataFormat);
    }

    /**
     * Unmarshal the CSV
     *
     * @param exchange    Exchange (used for accessing type converter)
     * @param inputStream Input CSV stream
     * @return Unmarshalled CSV
     * @throws IOException if the stream cannot be read properly
     */
    public abstract Object unmarshal(Exchange exchange, InputStream inputStream) throws IOException;

    private static CsvRecordConverter<?> extractConverter(CsvDataFormat dataFormat) {
        if (dataFormat.getRecordConverter() != null) {
            return dataFormat.getRecordConverter();
        } else if (dataFormat.isUseOrderedMaps()) {
            return CsvRecordConverters.orderedMapConverter();
        } else if (dataFormat.isUseMaps()) {
            return CsvRecordConverters.mapConverter();
        } else {
            return CsvRecordConverters.listConverter();
        }
    }

    //region Implementations

    /**
     * This class reads all the CSV into one big list.
     */
    private static final class BulkCsvUnmarshaller extends CsvUnmarshaller {
        private BulkCsvUnmarshaller(CSVFormat format, CsvDataFormat dataFormat) {
            super(format, dataFormat);
        }

        @Override
        public Object unmarshal(Exchange exchange, InputStream inputStream) throws IOException {
            CSVParser parser = new CSVParser(new InputStreamReader(inputStream, ExchangeHelper.getCharsetName(exchange)), format);
            try {
                return asList(parser.iterator(), converter);
            } finally {
                IOHelper.close(parser);
            }
        }

        private <T> List<T> asList(Iterator<CSVRecord> iterator, CsvRecordConverter<T> converter) {
            List<T> answer = new ArrayList<>();
            while (iterator.hasNext()) {
                answer.add(converter.convertRecord(iterator.next()));
            }
            return answer;
        }
    }

    /**
     * This class streams the content of the CSV
     */
    @SuppressWarnings("unchecked")
    private static final class StreamCsvUnmarshaller extends CsvUnmarshaller {

        private StreamCsvUnmarshaller(CSVFormat format, CsvDataFormat dataFormat) {
            super(format, dataFormat);
        }

        @Override
        public Object unmarshal(Exchange exchange, InputStream inputStream) throws IOException {
            Reader reader = null;
            try {
                reader = new InputStreamReader(inputStream, ExchangeHelper.getCharsetName(exchange));
                CSVParser parser = new CSVParser(reader, format);
                CsvIterator answer = new CsvIterator(parser, converter);
                // add to UoW so we can close the iterator so it can release any resources
                exchange.adapt(ExtendedExchange.class).addOnCompletion(new CsvUnmarshalOnCompletion(answer));
                return answer;
            } catch (Exception e) {
                IOHelper.close(reader);
                throw e;
            }
        }
    }

    /**
     * This class converts the CSV iterator into the proper result type.
     *
     * @param <T> Converted type
     */
    private static final class CsvIterator<T> implements Iterator<T>, Closeable {
        private final CSVParser parser;
        private final Iterator<CSVRecord> iterator;
        private final CsvRecordConverter<T> converter;

        private CsvIterator(CSVParser parser, CsvRecordConverter<T> converter) {
            this.parser = parser;
            this.iterator = parser.iterator();
            this.converter = converter;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public T next() {
            return converter.convertRecord(iterator.next());
        }

        @Override
        public void remove() {
            iterator.remove();
        }

        @Override
        public void close() throws IOException {
            if (!parser.isClosed()) {
                parser.close();
            }
        }
    }
    //endregion
}
