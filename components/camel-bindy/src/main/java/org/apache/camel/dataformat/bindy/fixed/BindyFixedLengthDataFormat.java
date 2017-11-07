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
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.dataformat.bindy.BindyAbstractDataFormat;
import org.apache.camel.dataformat.bindy.BindyAbstractFactory;
import org.apache.camel.dataformat.bindy.BindyFixedLengthFactory;
import org.apache.camel.dataformat.bindy.FormatFactory;
import org.apache.camel.dataformat.bindy.util.ConverterUtils;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A <a href="http://camel.apache.org/data-format.html">data format</a> (
 * {@link DataFormat}) using Bindy to marshal to and from Fixed Length
 */
public class BindyFixedLengthDataFormat extends BindyAbstractDataFormat {

    public static final String CAMEL_BINDY_FIXED_LENGTH_HEADER = "CamelBindyFixedLengthHeader";
    public static final String CAMEL_BINDY_FIXED_LENGTH_FOOTER = "CamelBindyFixedLengthFooter";

    private static final Logger LOG = LoggerFactory.getLogger(BindyFixedLengthDataFormat.class);

    private BindyFixedLengthFactory headerFactory;
    private BindyFixedLengthFactory footerFactory;

    public BindyFixedLengthDataFormat() {
    }

    public BindyFixedLengthDataFormat(Class<?> type) {
        super(type);
    }

    @Override
    public String getDataFormatName() {
        return "bindy-fixed";
    }

    @SuppressWarnings("unchecked")
    public void marshal(Exchange exchange, Object body, OutputStream outputStream) throws Exception {
        BindyFixedLengthFactory factory = (BindyFixedLengthFactory) getFactory();
        ObjectHelper.notNull(factory, "not instantiated");

        // Get CRLF
        byte[] bytesCRLF = ConverterUtils.getByteReturn(factory.getCarriageReturn());

        List<Map<String, Object>> models;

        // the body is not a prepared list so help a bit here and create one for us
        if (!isPreparedList(body)) {
            models = new ArrayList<Map<String, Object>>();
            Iterator<?> it = ObjectHelper.createIterator(body);
            while (it.hasNext()) {
                Object model = it.next();
                String name = model.getClass().getName();
                Map<String, Object> row = new HashMap<String, Object>();
                row.put(name, model);
                row.putAll(createLinkedFieldsModel(model));
                models.add(row);
            }
        } else {
            // cast to the expected type
            models = (List<Map<String, Object>>) body;
        }

        // add the header if it is in the exchange header
        Map<String, Object> headerRow = (Map<String, Object>) exchange.getIn().getHeader(CAMEL_BINDY_FIXED_LENGTH_HEADER);
        if (headerRow != null) {
            models.add(0, headerRow);
        }

        // add the footer if it is in the exchange header
        Map<String, Object> footerRow = (Map<String, Object>) exchange.getIn().getHeader(CAMEL_BINDY_FIXED_LENGTH_FOOTER);
        if (footerRow != null) {
            models.add(models.size(), footerRow);
        }

        int row = 0;
        for (Map<String, Object> model : models) {
            row++;
            String result = null;

            if (row == 1 && headerFactory != null) {
                // marshal the first row as a header if the models match
                Set<String> modelClassNames = model.keySet();
                // only use the header factory if the row is the header
                if (headerFactory.supportsModel(modelClassNames)) {
                    if (factory.skipHeader()) {
                        LOG.info("Skipping marshal of header row; 'skipHeader=true'");
                        continue;
                    } else {
                        result = headerFactory.unbind(getCamelContext(), model);
                    }
                }
            } else if (row == models.size() && footerFactory != null) {
                // marshal the last row as a footer if the models match
                Set<String> modelClassNames = model.keySet();
                // only use the header factory if the row is the header
                if (footerFactory.supportsModel(modelClassNames)) {
                    if (factory.skipFooter()) {
                        LOG.info("Skipping marshal of footer row; 'skipFooter=true'");
                        continue;
                    } else {
                        result = footerFactory.unbind(getCamelContext(), model);
                    }
                }
            }

            if (result == null) {
                // marshal as a normal / default row
                result = factory.unbind(getCamelContext(), model);
            }

            byte[] bytes = exchange.getContext().getTypeConverter().convertTo(byte[].class, exchange, result);
            outputStream.write(bytes);

            // Add a carriage return
            outputStream.write(bytesCRLF);
        }
    }

    /*
     * Check if the body is already parsed.
     * Bindy expects a list containing Map<String, Object> entries
     * where each Map contains only one entry where the key is the class
     * name of the object to be marshalled, and the value is the
     * object to be marshalled.
     */
    private boolean isPreparedList(Object object) {
        if (List.class.isAssignableFrom(object.getClass())) {
            List<?> list = (List<?>) object;
            if (list.size() > 0) {
                // Check first entry, should be enough
                Object entry = list.get(0);
                if (Map.class.isAssignableFrom(entry.getClass())) {
                    Map<?, ?> map = (Map<?, ?>) entry;
                    if (map.size() == 1) {
                        if (map.keySet().toArray()[0] instanceof String) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public Object unmarshal(Exchange exchange, InputStream inputStream) throws Exception {
        BindyFixedLengthFactory factory = (BindyFixedLengthFactory) getFactory();
        ObjectHelper.notNull(factory, "not instantiated");

        // List of Pojos
        List<Map<String, Object>> models = new ArrayList<Map<String, Object>>();

        // Pojos of the model
        Map<String, Object> model;

        InputStreamReader in = new InputStreamReader(inputStream, IOHelper.getCharsetName(exchange));

        // Scanner is used to read big file
        Scanner scanner = new Scanner(in);
        boolean isEolSet = false;
        if (!"".equals(factory.getEndOfLine())) {
            scanner.useDelimiter(factory.getEndOfLine());
            isEolSet = true;
        }

        AtomicInteger count = new AtomicInteger(0);

        try {

            // Parse the header if it exists
            if (((isEolSet && scanner.hasNext()) || (!isEolSet && scanner.hasNextLine())) && factory.hasHeader()) {

                // Read the line (should not trim as its fixed length)
                String line = getNextNonEmptyLine(scanner, count, isEolSet);

                if (!factory.skipHeader()) {
                    Map<String, Object> headerObjMap = createModel(headerFactory, line, count.intValue());
                    exchange.getOut().setHeader(CAMEL_BINDY_FIXED_LENGTH_HEADER, headerObjMap);
                }
            }

            String thisLine = getNextNonEmptyLine(scanner, count, isEolSet);

            String nextLine = null;
            if (thisLine != null) {
                nextLine = getNextNonEmptyLine(scanner, count, isEolSet);
            }

            // Parse the main file content
            while (thisLine != null && nextLine != null) {

                model = createModel(factory, thisLine, count.intValue());

                // Add objects graph to the list
                models.add(model);

                thisLine = nextLine;
                nextLine = getNextNonEmptyLine(scanner, count, isEolSet);
            }

            // this line should be the last non-empty line from the file
            // optionally parse the line as a footer
            if (thisLine != null) {
                if (factory.hasFooter()) {
                    if (!factory.skipFooter()) {
                        Map<String, Object> footerObjMap = createModel(footerFactory, thisLine, count.intValue());
                        exchange.getOut().setHeader(CAMEL_BINDY_FIXED_LENGTH_FOOTER, footerObjMap);
                    }
                } else {
                    model = createModel(factory, thisLine, count.intValue());
                    models.add(model);
                }
            }

            // BigIntegerFormatFactory if models list is empty or not
            // If this is the case (correspond to an empty stream, ...)
            if (models.size() == 0) {
                throw new java.lang.IllegalArgumentException("No records have been defined in the the file");
            } else {
                return extractUnmarshalResult(models);
            }

        } finally {
            scanner.close();
            IOHelper.close(in, "in", LOG);
        }

    }

    private String getNextNonEmptyLine(Scanner scanner, AtomicInteger count, boolean isEolSet) {
        String line = "";
        while (ObjectHelper.isEmpty(line) && ((isEolSet && scanner.hasNext()) || (!isEolSet && scanner.hasNextLine()))) {
            count.incrementAndGet();
            if (!isEolSet) {
                line = scanner.nextLine();
            } else {
                line = scanner.next();
            }
        }

        if (ObjectHelper.isEmpty(line)) {
            return null;
        } else {
            return line;
        }
    }

    protected Map<String, Object> createModel(BindyFixedLengthFactory factory, String line, int count) throws Exception {
        String myLine = line;

        // Check if the record length corresponds to the parameter
        // provided in the @FixedLengthRecord
        if (factory.recordLength() > 0) {
            if (isPaddingNeededAndEnable(factory, myLine)) {
                //myLine = rightPad(myLine, factory.recordLength());
            }
            if (isTrimmingNeededAndEnabled(factory, myLine)) {
                myLine = myLine.substring(0, factory.recordLength());
            }
            if ((myLine.length() < factory.recordLength()
                    && !factory.isIgnoreMissingChars()) || (myLine.length() > factory.recordLength())) {
                throw new java.lang.IllegalArgumentException("Size of the record: " + myLine.length()
                        + " is not equal to the value provided in the model: " + factory.recordLength());
            }
        }

        // Create POJO where Fixed data will be stored
        Map<String, Object> model = factory.factory();

        // Bind data from Fixed record with model classes
        factory.bind(getCamelContext(), myLine, model, count);

        // Link objects together
        factory.link(model);

        LOG.debug("Graph of objects created: {}", model);
        return model;
    }

    private boolean isTrimmingNeededAndEnabled(BindyFixedLengthFactory factory, String myLine) {
        return factory.isIgnoreTrailingChars() && myLine.length() > factory.recordLength();
    }

    private String rightPad(String myLine, int length) {
        return String.format("%1$-" + length + "s", myLine);
    }

    private boolean isPaddingNeededAndEnable(BindyFixedLengthFactory factory, String myLine) {
        return myLine.length() < factory.recordLength() && factory.isIgnoreMissingChars();
    }

    @Override
    protected BindyAbstractFactory createModelFactory(FormatFactory formatFactory) throws Exception {

        BindyFixedLengthFactory factory = new BindyFixedLengthFactory(getClassType());
        factory.setFormatFactory(formatFactory);

        // Optionally initialize the header factory... using header model classes
        if (factory.hasHeader()) {
            this.headerFactory = new BindyFixedLengthFactory(factory.header());
            this.headerFactory.setFormatFactory(formatFactory);
        }

        // Optionally initialize the footer factory... using footer model classes
        if (factory.hasFooter()) {
            this.footerFactory = new BindyFixedLengthFactory(factory.footer());
            this.footerFactory.setFormatFactory(formatFactory);
        }

        return factory;
    }

}
