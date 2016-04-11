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
package org.apache.camel.dataformat.bindy.csv;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.apache.camel.Exchange;
import org.apache.camel.dataformat.bindy.BindyAbstractDataFormat;
import org.apache.camel.dataformat.bindy.BindyAbstractFactory;
import org.apache.camel.dataformat.bindy.BindyCsvFactory;
import org.apache.camel.dataformat.bindy.annotation.Link;
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
public class BindyCsvDataFormat extends BindyAbstractDataFormat {
    private static final Logger LOG = LoggerFactory.getLogger(BindyCsvDataFormat.class);

    public BindyCsvDataFormat() {
    }

    public BindyCsvDataFormat(Class<?> type) {
        super(type);
    }

    @Override
    public String getDataFormatName() {
        return "bindy-csv";
    }

    @SuppressWarnings("unchecked")
    public void marshal(Exchange exchange, Object body, OutputStream outputStream) throws Exception {

        BindyCsvFactory factory = (BindyCsvFactory)getFactory();
        ObjectHelper.notNull(factory, "not instantiated");

        // Get CRLF
        byte[] bytesCRLF = ConverterUtils.getByteReturn(factory.getCarriageReturn());

        if (factory.getGenerateHeaderColumnNames()) {

            String result = factory.generateHeader();
            byte[] bytes = exchange.getContext().getTypeConverter().convertTo(byte[].class, exchange, result);
            outputStream.write(bytes);

            // Add a carriage return
            outputStream.write(bytesCRLF);
        }

        List<Map<String, Object>> models = new ArrayList<Map<String, Object>>();

        // the body is not a prepared list of map that bindy expects so help a bit here and create one for us
        Iterator<Object> it = ObjectHelper.createIterator(body);
        while (it.hasNext()) {
            Object model = it.next();
            if (model instanceof Map) {
                models.add((Map<String, Object>) model);
            } else {
                String name = model.getClass().getName();
                Map<String, Object> row = new HashMap<String, Object>(1);
                row.put(name, model);
                // search for @Link-ed fields and add them to the model
                for (Field field : model.getClass().getDeclaredFields()) {
                    Link linkField = field.getAnnotation(Link.class);
                    if (linkField != null) {
                        boolean accessible = field.isAccessible();
                        field.setAccessible(true);
                        row.put(field.getType().getName(), field.get(model));
                        field.setAccessible(accessible);
                    }
                } 
                models.add(row);
            }
        }

        for (Map<String, Object> model : models) {

            String result = factory.unbind(model);

            byte[] bytes = exchange.getContext().getTypeConverter().convertTo(byte[].class, exchange, result);
            outputStream.write(bytes);

            // Add a carriage return
            outputStream.write(bytesCRLF);
        }
    }

    public Object unmarshal(Exchange exchange, InputStream inputStream) throws Exception {
        BindyCsvFactory factory = (BindyCsvFactory)getFactory();
        ObjectHelper.notNull(factory, "not instantiated");

        // List of Pojos
        List<Map<String, Object>> models = new ArrayList<Map<String, Object>>();

        // Pojos of the model
        Map<String, Object> model;

        InputStreamReader in = new InputStreamReader(inputStream, IOHelper.getCharsetName(exchange));

        // Scanner is used to read big file
        Scanner scanner = new Scanner(in);

        // Retrieve the separator defined to split the record
        String separator = factory.getSeparator();
        String quote = factory .getQuote();
        ObjectHelper.notNull(separator, "The separator has not been defined in the annotation @CsvRecord or not instantiated during initModel.");

        int count = 0;
        try {
            // If the first line of the CSV file contains columns name, then we
            // skip this line
            if (factory.getSkipFirstLine()) {
                // Check if scanner is empty
                if (scanner.hasNextLine()) {
                    scanner.nextLine();
                }
            }

            while (scanner.hasNextLine()) {

                // Read the line
                String line = scanner.nextLine().trim();

                if (ObjectHelper.isEmpty(line)) {
                    // skip if line is empty
                    continue;
                }

                // Increment counter
                count++;

                // Create POJO where CSV data will be stored
                model = factory.factory();

                // Split the CSV record according to the separator defined in
                // annotated class @CSVRecord
                String[] tokens = line.split(separator, factory.getAutospanLine() ? factory.getMaxpos() : -1);
                List<String> result = Arrays.asList(tokens);
                // must unquote tokens before use
                result = unquoteTokens(result, separator, quote);

                if (result.size() == 0 || result.isEmpty()) {
                    throw new java.lang.IllegalArgumentException("No records have been defined in the CSV");
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Size of the record splitted : {}", result.size());
                    }

                    // Bind data from CSV record with model classes
                    factory.bind(result, model, count);

                    // Link objects together
                    factory.link(model);

                    // Add objects graph to the list
                    models.add(model);

                    LOG.debug("Graph of objects created: {}", model);
                }
            }

            // Test if models list is empty or not
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

    /**
     * Unquote the tokens, by removing leading and trailing quote chars,
     * as will handling fixing broken tokens which may have been split
     * by a separator inside a quote.
     */
    private List<String> unquoteTokens(List<String> result, String separator, String quote) {
        // a current quoted token which we assemble from the broken pieces
        // we need to do this as we use the split method on the String class
        // to split the line using regular expression, and it does not handle
        // if the separator char is also inside a quoted token, therefore we need
        // to fix this afterwards
        StringBuilder current = new StringBuilder();

        List<String> answer = new ArrayList<String>();
        for (String s : result) {
            boolean startQuote = false;
            boolean endQuote = false;
            if (s.startsWith(quote)) {
                s = s.substring(1);
                startQuote = true;
            }
            if (s.endsWith(quote)) {
                s = s.substring(0, s.length() - 1);
                endQuote = true;
            }

            // are we in progress of rebuilding a broken token
            boolean currentInProgress = current.length() > 0;

            // situation when field ending with a separator symbol.
            if (currentInProgress && startQuote && s.isEmpty()) {
                // Add separator, append current and reset it
                current.append(separator);
                answer.add(current.toString());
                current.setLength(0);
                continue;
            }

            // if we hit a start token then rebuild a broken token
            if (currentInProgress || startQuote) {
                // append to current if we are in the middle of a start quote
                if (currentInProgress) {
                    // must append separator back as this is a quoted token that was broken
                    // but a separator inside the quotes
                    current.append(separator);
                }
                current.append(s);
            }

            // are we in progress of rebuilding a broken token
            currentInProgress = current.length() > 0;

            if (endQuote) {
                // we hit end quote so append current and reset it
                answer.add(current.toString());
                current.setLength(0);
            } else if (!currentInProgress) {
                // not rebuilding so add directly as is
                answer.add(s);
            }
        }

        // any left over from current?
        if (current.length() > 0) {
            answer.add(current.toString());
            current.setLength(0);
        }

        return answer;
    }

    @Override
    protected BindyAbstractFactory createModelFactory() throws Exception {
        return new BindyCsvFactory(getClassType());
    }
}
