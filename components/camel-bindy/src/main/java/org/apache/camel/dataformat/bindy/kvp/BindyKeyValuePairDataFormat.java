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
package org.apache.camel.dataformat.bindy.kvp;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.Exchange;
import org.apache.camel.TypeConverter;
import org.apache.camel.dataformat.bindy.BindyAbstractDataFormat;
import org.apache.camel.dataformat.bindy.BindyAbstractFactory;
import org.apache.camel.dataformat.bindy.BindyKeyValuePairFactory;
import org.apache.camel.dataformat.bindy.FormatFactory;
import org.apache.camel.dataformat.bindy.WrappedException;
import org.apache.camel.dataformat.bindy.util.ConverterUtils;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A <a href="http://camel.apache.org/data-format.html">data format</a> (
 * {@link DataFormat}) using Bindy to marshal to and from CSV files
 */
@Dataformat("bindy-kvp")
public class BindyKeyValuePairDataFormat extends BindyAbstractDataFormat {

    private static final Logger LOG = LoggerFactory.getLogger(BindyKeyValuePairDataFormat.class);

    public BindyKeyValuePairDataFormat() {
    }

    public BindyKeyValuePairDataFormat(Class<?> type) {
        super(type);
    }

    @Override
    public String getDataFormatName() {
        return "bindy-kvp";
    }

    @Override
    @SuppressWarnings("unchecked")
    public void marshal(Exchange exchange, Object body, OutputStream outputStream) throws Exception {
        final BindyAbstractFactory factory = getFactory();
        final byte[] crlf = ConverterUtils.getByteReturn(factory.getCarriageReturn());
        final TypeConverter converter = exchange.getContext().getTypeConverter();

        // the body may not be a prepared list of map that bindy expects so help
        // a bit here and create one if needed
        for (Object model : ObjectHelper.createIterable(body)) {

            Map<String, Object> row;
            if (model instanceof Map) {
                row = (Map<String, Object>) model;
            } else {
                row = Collections.singletonMap(model.getClass().getName(), model);
            }

            String result = factory.unbind(getCamelContext(), row);

            outputStream.write(converter.convertTo(byte[].class, exchange, result));
            outputStream.write(crlf);
        }
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream inputStream) throws Exception {
        BindyKeyValuePairFactory factory = (BindyKeyValuePairFactory) getFactory();

        // List of Pojos
        List<Map<String, Object>> models = new ArrayList<>();

        // Map to hold the model @OneToMany classes while binding
        Map<String, List<Object>> lists = new HashMap<>();

        InputStreamReader in = new InputStreamReader(inputStream, ExchangeHelper.getCharsetName(exchange));

        // Use a Stream to stream a file across
        try (Stream<String> lines = new BufferedReader(in).lines()) {
            // Retrieve the pair separator defined to split the record
            org.apache.camel.util.ObjectHelper.notNull(factory.getPairSeparator(), "The pair separator property of the annotation @Message");
            String separator = factory.getPairSeparator();
            AtomicInteger count = new AtomicInteger(0);

            try {
                lines.forEachOrdered(line -> {
                    consumeFile(factory, models, lists, separator, count, line);
                });
            } catch (WrappedException e) {
                throw e.getWrappedException();
            }

            // BigIntegerFormatFactory if models list is empty or not
            // If this is the case (correspond to an empty stream, ...)
            if (models.isEmpty() && !isAllowEmptyStream()) {
                throw new java.lang.IllegalArgumentException("No records have been defined in the CSV");
            } else {
                return extractUnmarshalResult(models);
            }

        } finally {
            IOHelper.close(in, "in", LOG);
        }
    }

    private void consumeFile(BindyKeyValuePairFactory factory, List<Map<String, Object>> models, Map<String, List<Object>> lists, String separator, AtomicInteger count, String line) {
        try {
            // Trim the line coming in to remove any trailing whitespace
            String trimmedLine = line.trim();

            if (!org.apache.camel.util.ObjectHelper.isEmpty(trimmedLine)) {
                // Increment counter
                count.incrementAndGet();
                // Pojos of the model
                Map<String, Object> model;

                // Create POJO
                model = factory.factory();

                // Split the message according to the pair separator defined in
                // annotated class @Message
                // Explicitly replace any occurrence of the Unicode new line character.
                // Simply reading the line in with the File stream doesn't get us around the fact
                // that this character is still present in the data set, and we don't wish for it
                // to be present when storing the actual data in the model.
                List<String> result = Arrays.stream(line.split(separator))
                        .map(x -> x.replace("\u0085", ""))
                        .collect(Collectors.toList());

                if (result.size() == 0 || result.isEmpty()) {
                    throw new IllegalArgumentException("No records have been defined in the KVP");
                }

                if (result.size() > 0) {
                    // Bind data from message with model classes
                    // Counter is used to detect line where error occurs
                    factory.bind(getCamelContext(), result, model, count.get(), lists);

                    // Link objects together
                    factory.link(model);

                    // Add objects graph to the list
                    models.add(model);

                    LOG.debug("Graph of objects created: {}", model);
                }
            }
        } catch (Exception e) {
            throw new WrappedException(e);
        }
    }

    @Override
    protected BindyAbstractFactory createModelFactory(FormatFactory formatFactory) throws Exception {
        BindyKeyValuePairFactory bindyKeyValuePairFactory = new BindyKeyValuePairFactory(getClassType());
        bindyKeyValuePairFactory.setFormatFactory(formatFactory);
        return bindyKeyValuePairFactory;
    }
}
