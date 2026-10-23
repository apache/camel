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

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.util.IOHelper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/**
 * This class marshal data into a CSV format.
 */
public abstract class CsvMarshaller {
    private final CSVFormat format;

    protected CsvMarshaller(CSVFormat format) {
        this.format = format;
    }

    /**
     * Creates a new instance.
     *
     * @param  format     CSV format
     * @param  dataFormat Camel CSV data format
     * @return            New instance
     */
    public static CsvMarshaller create(CSVFormat format, CsvDataFormat dataFormat) {
        org.apache.camel.util.ObjectHelper.notNull(format, "CSV format");
        org.apache.camel.util.ObjectHelper.notNull(dataFormat, "CSV data format");
        // Get header columns before potential clearing
        String[] headers = format.getHeader();
        // If we don't want the header record, clear it
        if (format.getSkipHeaderRecord()) {
            format = format.builder().setHeader((String[]) null).get();
        }

        if (headers != null && headers.length != 0) {
            return new FixedColumnsMarshaller(format, headers);
        }
        return new DynamicColumnsMarshaller(format);
    }

    /**
     * Marshals the given object into the given stream.
     *
     * @param  exchange                           Exchange (used for access to type conversion)
     * @param  object                             Body to marshal
     * @param  outputStream                       Output stream of the CSV
     * @throws NoTypeConversionAvailableException if the body cannot be converted
     * @throws IOException                        if we cannot write into the given stream
     */
    @SuppressWarnings("rawtypes")
    public void marshal(Exchange exchange, Object object, OutputStream outputStream)
            throws NoTypeConversionAvailableException, IOException {
        CSVPrinter printer = createPrinter(exchange, outputStream);
        try {
            Function<Map<?, ?>, Iterable<?>> mapRecordValues = createMapRecordValues();
            if (object instanceof Map map) {
                printer.printRecord(mapRecordValues.apply(map));
            } else {
                Iterator it = ObjectHelper.createIterator(object);
                while (it.hasNext()) {
                    Object child = it.next();
                    printer.printRecord(getRecordValues(exchange, child, mapRecordValues));
                }
            }
        } finally {
            IOHelper.close(printer);
        }
    }

    /**
     * Creates and returns a {@link CSVPrinter}.
     *
     * @param  exchange     Exchange (used for access to type conversion). Could NOT be <code>null</code>.
     * @param  outputStream Output stream of the CSV. Could NOT be <code>null</code>.
     * @return              a new {@link CSVPrinter}. Never <code>null</code>.
     */
    protected CSVPrinter createPrinter(Exchange exchange, OutputStream outputStream) throws IOException {
        org.apache.camel.util.ObjectHelper.notNull(exchange, "Exchange");
        org.apache.camel.util.ObjectHelper.notNull(outputStream, "Output stream");
        return new CSVPrinter(new OutputStreamWriter(outputStream, ExchangeHelper.getCharsetName(exchange)), format);
    }

    private Iterable<?> getRecordValues(Exchange exchange, Object data, Function<Map<?, ?>, Iterable<?>> mapRecordValues)
            throws NoTypeConversionAvailableException {
        // each row must be a map or list based
        Map<?, ?> map = exchange.getContext().getTypeConverter().tryConvertTo(Map.class, exchange, data);
        if (map != null) {
            return mapRecordValues.apply(map);
        }
        return ExchangeHelper.convertToMandatoryType(exchange, List.class, data);
    }

    /**
     * Gets the CSV record values of the given map.
     *
     * @param  map Input map
     * @return     CSV record values of the given map
     */
    protected abstract Iterable<?> getMapRecordValues(Map<?, ?> map);

    /**
     * Creates the function that gives the CSV record values of the map rows of one marshalled body. It is created for
     * each body, so it can keep the columns of the rows already written.
     *
     * @return the function that gives the CSV record values of a map row
     */
    protected Function<Map<?, ?>, Iterable<?>> createMapRecordValues() {
        return this::getMapRecordValues;
    }

    //region Implementations

    /**
     * This marshaller has fixed columns
     */
    private static final class FixedColumnsMarshaller extends CsvMarshaller {
        private final String[] fixedColumns;

        private FixedColumnsMarshaller(CSVFormat format, String[] fixedColumns) {
            super(format);
            this.fixedColumns = Arrays.copyOf(fixedColumns, fixedColumns.length);
        }

        @Override
        protected Iterable<?> getMapRecordValues(Map<?, ?> map) {
            List<Object> result = new ArrayList<>(fixedColumns.length);
            for (String key : fixedColumns) {
                result.add(map.get(key));
            }
            return result;
        }
    }

    /**
     * This marshaller takes the columns from the keys of the map rows. Within one body each key keeps its column: the
     * keys of the first row, then the keys of later rows in the order they are first seen. A row without one of these
     * keys gets a null value in its column.
     */
    private static final class DynamicColumnsMarshaller extends CsvMarshaller {
        private DynamicColumnsMarshaller(CSVFormat format) {
            super(format);
        }

        @Override
        protected Function<Map<?, ?>, Iterable<?>> createMapRecordValues() {
            // the columns of one body, so each key keeps its column in every row, whatever the iteration order of the
            // row map; a new body starts with no columns, as the rows of another message may have other keys
            Set<Object> columns = new LinkedHashSet<>();
            return map -> {
                columns.addAll(map.keySet());
                List<Object> result = new ArrayList<>(columns.size());
                for (Object column : columns) {
                    result.add(map.get(column));
                }
                return result;
            };
        }

        @Override
        protected Iterable<?> getMapRecordValues(Map<?, ?> map) {
            // a map on its own: its keys are the columns
            return createMapRecordValues().apply(map);
        }
    }
    //endregion
}
