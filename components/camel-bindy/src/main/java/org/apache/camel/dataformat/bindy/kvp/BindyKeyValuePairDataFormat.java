/**
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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.apache.camel.Exchange;
import org.apache.camel.TypeConverter;
import org.apache.camel.dataformat.bindy.BindyAbstractDataFormat;
import org.apache.camel.dataformat.bindy.BindyAbstractFactory;
import org.apache.camel.dataformat.bindy.BindyKeyValuePairFactory;
import org.apache.camel.dataformat.bindy.FormatFactory;
import org.apache.camel.dataformat.bindy.util.ConverterUtils;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A <a href="http://camel.apache.org/data-format.html">data format</a> (
 * {@link DataFormat}) using Bindy to marshal to and from CSV files
 */
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

    @SuppressWarnings("unchecked")
    public void marshal(Exchange exchange, Object body, OutputStream outputStream) throws Exception {
        final BindyAbstractFactory factory = getFactory();
        final byte[] crlf = ConverterUtils.getByteReturn(factory.getCarriageReturn());
        final TypeConverter converter = exchange.getContext().getTypeConverter();

        // the body may not be a prepared list of map that bindy expects so help
        // a bit here and create one if needed
        final Iterator<Object> it = ObjectHelper.createIterator(body);
        while (it.hasNext()) {
            Object model = it.next();

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

    public Object unmarshal(Exchange exchange, InputStream inputStream) throws Exception {
        BindyKeyValuePairFactory factory = (BindyKeyValuePairFactory)getFactory();

        // List of Pojos
        List<Map<String, Object>> models = new ArrayList<Map<String, Object>>();

        // Pojos of the model
        Map<String, Object> model;
        
        // Map to hold the model @OneToMany classes while binding
        Map<String, List<Object>> lists = new HashMap<String, List<Object>>();

        InputStreamReader in = new InputStreamReader(inputStream, IOHelper.getCharsetName(exchange));

        // Scanner is used to read big file
        Scanner scanner = new Scanner(in);

        // Retrieve the pair separator defined to split the record
        ObjectHelper.notNull(factory.getPairSeparator(), "The pair separator property of the annotation @Message");
        String separator = factory.getPairSeparator();

        int count = 0;
        try {
            while (scanner.hasNextLine()) {
                // Read the line
                String line = scanner.nextLine().trim();

                if (ObjectHelper.isEmpty(line)) {
                    // skip if line is empty
                    continue;
                }

                // Increment counter
                count++;

                // Create POJO
                model = factory.factory();

                // Split the message according to the pair separator defined in
                // annotated class @Message
                List<String> result = Arrays.asList(line.split(separator));

                if (result.size() == 0 || result.isEmpty()) {
                    throw new java.lang.IllegalArgumentException("No records have been defined in the KVP");
                }

                if (result.size() > 0) {
                    // Bind data from message with model classes
                    // Counter is used to detect line where error occurs
                    factory.bind(getCamelContext(), result, model, count, lists);

                    // Link objects together
                    factory.link(model);

                    // Add objects graph to the list
                    models.add(model);

                    LOG.debug("Graph of objects created: {}", model);
                }
            }

            // BigIntegerFormatFactory if models list is empty or not
            // If this is the case (correspond to an empty stream, ...)
            if (models.size() == 0) {
                throw new java.lang.IllegalArgumentException("No records have been defined in the CSV");
            } else {
                return extractUnmarshalResult(models);
            }

        } finally {
            scanner.close();
            IOHelper.close(in, "in", LOG);
        }
    }

    protected BindyAbstractFactory createModelFactory(FormatFactory formatFactory) throws Exception {
        BindyKeyValuePairFactory bindyKeyValuePairFactory = new BindyKeyValuePairFactory(getClassType());
        bindyKeyValuePairFactory.setFormatFactory(formatFactory);
        return bindyKeyValuePairFactory;
    }
}
