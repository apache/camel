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
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.apache.camel.Exchange;
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
public class BindyKeyValuePairDataFormat implements DataFormat {

    private static final transient Log LOG = LogFactory.getLog(BindyKeyValuePairDataFormat.class);

    private String[] packages;
    private BindyKeyValuePairFactory modelFactory;

    public BindyKeyValuePairDataFormat() {
    }

    public BindyKeyValuePairDataFormat(String... packages) {
        this.packages = packages;
    }

    @SuppressWarnings("unchecked")
    public void marshal(Exchange exchange, Object body, OutputStream outputStream) throws Exception {
        BindyKeyValuePairFactory factory = getFactory(exchange.getContext().getPackageScanClassResolver());
        List<Map<String, Object>> models = (ArrayList<Map<String, Object>>)body;
        byte[] crlf;

        // Get CRLF
        crlf = Converter.getByteReturn(factory.getCarriageReturn());

        for (Map<String, Object> model : models) {
            String result = factory.unbind(model);
            byte[] bytes = exchange.getContext().getTypeConverter().convertTo(byte[].class, exchange, result);
            outputStream.write(bytes);

            // Add a carriage return
            outputStream.write(crlf);
        }
    }

    public Object unmarshal(Exchange exchange, InputStream inputStream) throws Exception {
        BindyKeyValuePairFactory factory = getFactory(exchange.getContext().getPackageScanClassResolver());

        // List of Pojos
        List<Map<String, Object>> models = new ArrayList<Map<String, Object>>();

        // Pojos of the model
        Map<String, Object> model;

        InputStreamReader in = new InputStreamReader(inputStream);

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
                    throw new java.lang.IllegalArgumentException("No records have been defined in the KVP !");
                }

                if (result.size() > 0) {

                    // Bind data from message with model classes
                    // Counter is used to detect line where error occurs
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
                throw new java.lang.IllegalArgumentException("No records have been defined in the KVP !");
            } else {
                return models;
            }

        } finally {
            scanner.close();
            IOHelper.close(in, "in", LOG);
        }
    }

    /**
     * Method used to create the singleton of the BindyKeyValuePairFactory
     */
    public BindyKeyValuePairFactory getFactory(PackageScanClassResolver resolver) throws Exception {
        if (modelFactory == null) {
            modelFactory = new BindyKeyValuePairFactory(resolver, this.packages);
        }
        return modelFactory;
    }

    public void setModelFactory(BindyKeyValuePairFactory modelFactory) {
        this.modelFactory = modelFactory;
    }

    public String[] getPackages() {
        return packages;
    }

    public void setPackages(String... packages) {
        this.packages = packages;
    }

}
