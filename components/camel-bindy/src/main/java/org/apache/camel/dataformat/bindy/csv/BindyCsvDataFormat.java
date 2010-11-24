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
import org.apache.camel.dataformat.bindy.BindyKeyValuePairFactory;
import org.apache.camel.dataformat.bindy.util.Converter;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A <a href="http://camel.apache.org/data-format.html">data format</a> (
 * {@link DataFormat}) using Bindy to marshal to and from CSV files
 */
public class BindyCsvDataFormat extends BindyAbstractDataFormat {
    private static final transient Log LOG = LogFactory.getLog(BindyCsvDataFormat.class);

    public BindyCsvDataFormat() {
    }

    public BindyCsvDataFormat(String... packages) {
        super(packages);
    }

    @SuppressWarnings("unchecked")
    public void marshal(Exchange exchange, Object body, OutputStream outputStream) throws Exception {

        BindyCsvFactory factory = (BindyCsvFactory)getFactory(exchange.getContext().getPackageScanClassResolver());
        ObjectHelper.notNull(factory, "not instantiated");

        // Get CRLF
        byte[] bytesCRLF = Converter.getByteReturn(factory.getCarriageReturn());

        if (factory.getGenerateHeaderColumnNames()) {

            String result = factory.generateHeader();
            byte[] bytes = exchange.getContext().getTypeConverter().convertTo(byte[].class, exchange, result);
            outputStream.write(bytes);

            // Add a carriage return
            outputStream.write(bytesCRLF);
        }

        List<Map<String, Object>> models;

        // the body is not a prepared list so help a bit here and create one for us
        if (exchange.getContext().getTypeConverter().convertTo(List.class, body) == null) {
            models = new ArrayList<Map<String, Object>>();
            Iterator it = ObjectHelper.createIterator(body);
            while (it.hasNext()) {
                Object model = it.next();
                String name = model.getClass().getName();
                Map<String, Object> row = new HashMap<String, Object>();
                row.put(name, body);
                models.add(row);
            }
        } else {
            // cast to the expected type
            models = (List<Map<String, Object>>) body;
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
        BindyCsvFactory factory = (BindyCsvFactory)getFactory(exchange.getContext().getPackageScanClassResolver());
        ObjectHelper.notNull(factory, "not instantiated");

        // List of Pojos
        List<Map<String, Object>> models = new ArrayList<Map<String, Object>>();

        // Pojos of the model
        Map<String, Object> model;

        InputStreamReader in = new InputStreamReader(inputStream);

        // Scanner is used to read big file
        Scanner scanner = new Scanner(in);

        // Retrieve the separator defined to split the record
        String separator = factory.getSeparator();
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
                
                // Added for camel- jira ticket
                // We will remove the first and last character  of the line
                // when the separator contains quotes, double quotes 
                // e.g. ',' or "," ...
                // REMARK : We take the assumption that the data fields are
                // quoted or double quoted like that 
                // e.g : "1 ", "street 1, NY", "USA"
                if (separator.length() > 1) {
                    String tempLine = line.substring(1, line.length() - 1);
                    line = tempLine;
                }
                // Split the CSV record according to the separator defined in
                // annotated class @CSVRecord
                String[] tokens = line.split(separator, -1);
                List<String> result = Arrays.asList(tokens);

                if (result.size() == 0 || result.isEmpty()) {
                    throw new java.lang.IllegalArgumentException("No records have been defined in the CSV !");
                }

                if (result.size() > 0) {

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Size of the record splitted : " + result.size());
                    }

                    // Bind data from CSV record with model classes
                    factory.bind(result, model, count);

                    // Link objects together
                    factory.link(model);

                    // Add objects graph to the list
                    models.add(model);

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Graph of objects created : " + model);
                    }

                }

            }

            // Test if models list is empty or not
            // If this is the case (correspond to an empty stream, ...)
            if (models.size() == 0) {
                throw new java.lang.IllegalArgumentException("No records have been defined in the CSV !");
            } else {
                return models;
            }

        } finally {
            scanner.close();
            IOHelper.close(in, "in", LOG);
        }

    }

    protected BindyAbstractFactory createModelFactory(PackageScanClassResolver resolver) throws Exception {
        return new BindyCsvFactory(resolver, getPackages());
    }
}
