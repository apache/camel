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

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.univocity.parsers.common.AbstractWriter;
import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;

import static org.apache.camel.support.ExchangeHelper.convertToMandatoryType;
import static org.apache.camel.support.ExchangeHelper.convertToType;

/**
 * This class marshalls the exchange body using an uniVocity writer. It can
 * automatically generates headers and keep their order in memory.
 *
 * @param <W> Writer class
 */
final class Marshaller<W extends AbstractWriter<?>> {
    private final LinkedHashSet<String> headers = new LinkedHashSet<>();
    private final boolean adaptHeaders;

    /**
     * Creates a new instance.
     *
     * @param headers the base headers to use
     * @param adaptHeaders whether or not we can add headers on the fly
     *            depending on the data
     */
    Marshaller(String[] headers, boolean adaptHeaders) {
        if (headers != null) {
            this.headers.addAll(Arrays.asList(headers));
        }
        this.adaptHeaders = adaptHeaders;
    }

    /**
     * Marshals the given body.
     *
     * @param exchange exchange to use (for type conversion)
     * @param body body to marshal
     * @param writer uniVocity writer to use
     * @throws NoTypeConversionAvailableException when it's not possible to
     *             convert the body as list and maps.
     */
    public void marshal(Exchange exchange, Object body, W writer) throws NoTypeConversionAvailableException {
        try {
            List<?> list = convertToType(exchange, List.class, body);
            if (list != null) {
                for (Object row : list) {
                    writeRow(exchange, row, writer);
                }
            } else {
                writeRow(exchange, body, writer);
            }
        } finally {
            writer.close();
        }
    }

    /**
     * Writes the given row.
     *
     * @param exchange exchange to use (for type conversion)
     * @param row row to write
     * @param writer uniVocity writer to use
     * @throws NoTypeConversionAvailableException when it's not possible to
     *             convert the row as map.
     */
    private void writeRow(Exchange exchange, Object row, W writer) throws NoTypeConversionAvailableException {
        Map<?, ?> map = convertToMandatoryType(exchange, Map.class, row);
        if (adaptHeaders) {
            synchronized (headers) {
                for (Object key : map.keySet()) {
                    headers.add(convertToMandatoryType(exchange, String.class, key));
                }
                writeRow(map, writer);
            }
        } else {
            writeRow(map, writer);
        }
    }

    /**
     * Writes the given map as row.
     * 
     * @param map row values by header
     * @param writer uniVocity writer to use
     */
    private void writeRow(Map<?, ?> map, W writer) {
        Object[] values = new Object[headers.size()];
        int index = 0;
        for (String header : headers) {
            values[index++] = map.get(header);
        }
        writer.writeRow(values);
    }
}
