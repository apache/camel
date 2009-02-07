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
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.apache.camel.Exchange;
import org.apache.camel.dataformat.bindy.BindyCsvFactory;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A <a href="http://camel.apache.org/data-format.html">data format</a>
 * ({@link DataFormat}) using Bindy to marshal to and from CSV files
 */
public class BindyCsvDataFormat implements DataFormat {
    private static final transient Log LOG = LogFactory.getLog(BindyCsvDataFormat.class);

    private String packageName;
    private BindyCsvFactory modelFactory;

    public BindyCsvDataFormat() {
    }

    public BindyCsvDataFormat(String packageName) {
        this.packageName = packageName;
    }

    @SuppressWarnings("unchecked")
    public void marshal(Exchange exchange, Object body, OutputStream outputStream) throws Exception {
        List<Map<String, Object>> models = (ArrayList<Map<String, Object>>) body;

        for (Map<String, Object> model : models) {
            String result = getFactory().unbind(model);
            byte[] bytes = exchange.getContext().getTypeConverter().convertTo(byte[].class, exchange, result);
            outputStream.write(bytes);
        }
    }

    public Object unmarshal(Exchange exchange, InputStream inputStream) throws Exception {

        // List of Pojos
        List<Map<String, Object>> models = new ArrayList<Map<String, Object>>();

        // Create POJO where CSV data will be stored
        Map<String, Object> model = getFactory().factory();

        InputStreamReader in = new InputStreamReader(inputStream);

        // Scanner is used to read big file
        Scanner scanner = new Scanner(in);

        // Retrieve the separator defined to split the record
        String separator = getFactory().getSeparator();
        ObjectHelper.notEmpty(separator, "The separator has not been defined in the annotation @Record or not instantiated during initModel.");

        int count = 0;
        try {

            // If the first line of the CSV file contains columns name, then we skip this line
            if (getFactory().getSkipFirstLine()) {
                scanner.nextLine();
            }

            while (scanner.hasNextLine()) {

                // Read the line
                String line = scanner.nextLine().trim();

                if (ObjectHelper.isEmpty(line)) {
                    // skip if line is empty
                    continue;
                }

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Counter " + count++ + " : content : " + line);
                }

                // Split the CSV record according to the separator defined in
                // annotated class @CSVRecord
                List<String> result = Arrays.asList(line.split(separator));

                // Bind data from CSV record with model classes
                getFactory().bind(result, model);

                // Link objects together
                getFactory().link(model);

                // Add objects graph to the list
                models.add(model);

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Graph of objects created : " + model);
                }

            }

            return models;

        } finally {
            scanner.close();
            ObjectHelper.close(in, "in", LOG);
        }

    }

    /**
     * Method used to create the singleton of the BindyCsvFactory
     */
    public BindyCsvFactory getFactory() throws Exception {
        if (modelFactory == null) {
            modelFactory = new BindyCsvFactory(this.packageName);
        }
        return modelFactory;
    }

    public void setModelFactory(BindyCsvFactory modelFactory) {
        this.modelFactory = modelFactory;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

}
