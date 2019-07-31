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
import java.util.List;
import java.util.Map;

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
     * @param format     CSV format
     * @param dataFormat Camel CSV data format
     * @return New instance
     */
    public static CsvMarshaller create(CSVFormat format, CsvDataFormat dataFormat) {
        org.apache.camel.util.ObjectHelper.notNull(format, "CSV format");
        org.apache.camel.util.ObjectHelper.notNull(dataFormat, "CSV data format");
        // If we don't want the header record, clear it
        if (format.getSkipHeaderRecord()) {
            format = format.withHeader((String[]) null);
        }

        String[] fixedColumns = dataFormat.getHeader();
        if (fixedColumns != null && fixedColumns.length > 0) {
            return new FixedColumnsMarshaller(format, fixedColumns);
        }
        return new DynamicColumnsMarshaller(format);
    }

    /**
     * Marshals the given object into the given stream.
     *
     * @param exchange     Exchange (used for access to type conversion)
     * @param object       Body to marshal
     * @param outputStream Output stream of the CSV
     * @throws NoTypeConversionAvailableException if the body cannot be converted
     * @throws IOException                        if we cannot write into the given stream
     */
    @SuppressWarnings("rawtypes")
    public void marshal(Exchange exchange, Object object, OutputStream outputStream) throws NoTypeConversionAvailableException, IOException {
        CSVPrinter printer = createPrinter(exchange, outputStream);
        try {
            Iterator it = ObjectHelper.createIterator(object);
            while (it.hasNext()) {
                Object child = it.next();
                printer.printRecord(getRecordValues(exchange, child));
            }
        } finally {
            IOHelper.close(printer);
        }
    }

    /**
     * Creates and returns a {@link CSVPrinter}.
     *
     * @param exchange     Exchange (used for access to type conversion). Could NOT be <code>null</code>.
     * @param outputStream Output stream of the CSV. Could NOT be <code>null</code>.
     * @return a new {@link CSVPrinter}. Never <code>null</code>.
     */
    protected CSVPrinter createPrinter(Exchange exchange, OutputStream outputStream) throws IOException {
        org.apache.camel.util.ObjectHelper.notNull(exchange, "Exchange");
        org.apache.camel.util.ObjectHelper.notNull(outputStream, "Output stream");
        return new CSVPrinter(new OutputStreamWriter(outputStream, ExchangeHelper.getCharsetName(exchange)), format);
    }

    private Iterable<?> getRecordValues(Exchange exchange, Object data) throws NoTypeConversionAvailableException {
        // each row must be a map or list based
        Map<?, ?> map = exchange.getContext().getTypeConverter().tryConvertTo(Map.class, exchange, data);
        if (map != null) {
            return getMapRecordValues(map);
        }
        return ExchangeHelper.convertToMandatoryType(exchange, List.class, data);
    }

    /**
     * Gets the CSV record values of the given map.
     *
     * @param map Input map
     * @return CSV record values of the given map
     */
    protected abstract Iterable<?> getMapRecordValues(Map<?, ?> map);

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
     * This marshaller adapts the columns but always keep them in the same order
     */
    private static final class DynamicColumnsMarshaller extends CsvMarshaller {
        private DynamicColumnsMarshaller(CSVFormat format) {
            super(format);
        }

        @Override
        protected Iterable<?> getMapRecordValues(Map<?, ?> map) {
            List<Object> result = new ArrayList<>(map.size());
            for (Object key : map.keySet()) {
                result.add(map.get(key));
            }
            return result;
        }
    }
    //endregion
}
