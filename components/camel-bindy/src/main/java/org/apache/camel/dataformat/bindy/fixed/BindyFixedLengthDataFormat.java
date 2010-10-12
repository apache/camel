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
package org.apache.camel.dataformat.bindy.fixed;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.apache.camel.Exchange;
import org.apache.camel.dataformat.bindy.BindyFixedLengthFactory;
import org.apache.camel.dataformat.bindy.util.Converter;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A <a href="http://camel.apache.org/data-format.html">data format</a> (
 * {@link DataFormat}) using Bindy to marshal to and from Fixed Length
 */
public class BindyFixedLengthDataFormat implements DataFormat {
    private static final transient Log LOG = LogFactory.getLog(BindyFixedLengthDataFormat.class);

    private String[] packages;
    private BindyFixedLengthFactory modelFactory;

    public BindyFixedLengthDataFormat() {
    }

    public BindyFixedLengthDataFormat(String... packages) {
        this.packages = packages;
    }

    @SuppressWarnings("unchecked")
    public void marshal(Exchange exchange, Object body, OutputStream outputStream) throws Exception {

        BindyFixedLengthFactory factory = getFactory(exchange.getContext().getPackageScanClassResolver());
        ObjectHelper.notNull(factory, "not instantiated");

        // Get CRLF
        byte[] bytesCRLF = Converter.getByteReturn(factory.getCarriageReturn());

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
        BindyFixedLengthFactory factory = getFactory(exchange.getContext().getPackageScanClassResolver());
        ObjectHelper.notNull(factory, "not instantiated");

        // List of Pojos
        List<Map<String, Object>> models = new ArrayList<Map<String, Object>>();

        // Pojos of the model
        Map<String, Object> model;

        InputStreamReader in = new InputStreamReader(inputStream);

        // Scanner is used to read big file
        Scanner scanner = new Scanner(in);

        int count = 0;

        try {

            // TODO Test if we have a Header
            // TODO Test if we have a Footer (containing by example checksum)

            while (scanner.hasNextLine()) {

                // Read the line
                String line = scanner.nextLine().trim();

                if (ObjectHelper.isEmpty(line)) {
                    // skip if line is empty
                    continue;
                }

                // Increment counter
                count++;
                
                // Check if the record length corresponds to the parameter
                // provided in the @FixedLengthRecord
                if ((line.length() < factory.recordLength()) || (line.length() > factory.recordLength())) {
                    throw new java.lang.IllegalArgumentException("Size of the record : " + line.length() + " is not equal to the value provided in the model : " + factory.recordLength() + " !");
                }

                // Create POJO where Fixed data will be stored
                model = factory.factory();
                
                // Bind data from Fixed record with model classes
                factory.bind(line, model, count);

                // Link objects together
                factory.link(model);

                // Add objects graph to the list
                models.add(model);

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Graph of objects created : " + model);
                }

            }

            // Test if models list is empty or not
            // If this is the case (correspond to an empty stream, ...)
            if (models.size() == 0) {
                throw new java.lang.IllegalArgumentException("No records have been defined in the message !");
            } else {
                return models;
            }

        } finally {
            scanner.close();
            IOHelper.close(in, "in", LOG);
        }

    }

    /**
     * Method used to create the singleton of the BindyCsvFactory
     */
    public BindyFixedLengthFactory getFactory(PackageScanClassResolver resolver) throws Exception {
        if (modelFactory == null) {
            modelFactory = new BindyFixedLengthFactory(resolver, packages);
        }
        return modelFactory;
    }

    public void setModelFactory(BindyFixedLengthFactory modelFactory) {
        this.modelFactory = modelFactory;
    }

    public String[] getPackages() {
        return packages;
    }

    public void setPackages(String[] packages) {
        this.packages = packages;
    }

}
