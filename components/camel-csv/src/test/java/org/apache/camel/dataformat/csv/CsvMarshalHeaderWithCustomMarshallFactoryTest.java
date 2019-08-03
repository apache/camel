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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * <b>Camel</b> based test cases for {@link CsvDataFormat}.
 */
public class CsvMarshalHeaderWithCustomMarshallFactoryTest extends CamelTestSupport {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Produce("direct:start")
    private ProducerTemplate producerTemplate;

    private File outputFile;

    @Override
    protected void doPreSetup() throws Exception {
        outputFile = new File(folder.newFolder(), "output.csv");
    }

    @Test
    public void testSendBody() throws IOException {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("first_name", "John");
        body.put("last_name", "Doe");
        String fileName = outputFile.getName();
        assertEquals("output.csv", fileName);
        producerTemplate.sendBodyAndHeader(body, Exchange.FILE_NAME, fileName);
        body = new LinkedHashMap<>();
        body.put("first_name", "Max");
        body.put("last_name", "Mustermann");
        producerTemplate.sendBodyAndHeader(body, Exchange.FILE_NAME, fileName);
        List<String> lines = Files.lines(Paths.get(outputFile.toURI()))
                .filter(l -> l.trim().length() > 0).collect(Collectors.toList());
        assertEquals(3, lines.size());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String uri = String.format("file:%s?charset=utf-8&fileExist=Append", outputFile.getParentFile().getAbsolutePath());
                from("direct:start").marshal(createCsvDataFormat()).to(uri);
            }
        };
    }

    private static CsvDataFormat createCsvDataFormat() {
        CsvDataFormat dataFormat = new CsvDataFormat();
        dataFormat.setDelimiter('\t');
        dataFormat.setTrim(true);
        dataFormat.setIgnoreSurroundingSpaces(true);
        dataFormat.setHeader(Arrays.asList("first_name", "last_name").toArray(new String[0]));
        dataFormat.setMarshallerFactory(new CsvMarshallerFactory() {

            @Override
            public CsvMarshaller create(CSVFormat format, CsvDataFormat dataFormat) {
                return new SinglePrinterCsvMarshaller(format);
            }
        });
        return dataFormat;
    }

    //
    // Helper classes
    //

    private static final class SinglePrinterCsvMarshaller extends CsvMarshaller {

        private final CSVPrinter printer;

        private SinglePrinterCsvMarshaller(CSVFormat format) {
            super(format);
            printer = createPrinter(format);
        }

        private static CSVPrinter createPrinter(CSVFormat format) {
            try {
                // Headers and header comments are written out in the constructor already.
                return format.print(new StringBuilder());
            } catch (IOException e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public void marshal(Exchange exchange, Object object, OutputStream outputStream) throws IOException {
            Iterator<Map<String, String>> it = (Iterator<Map<String, String>>) ObjectHelper.createIterator(object);
            synchronized (printer) {
                while (it.hasNext()) {
                    printer.printRecord(getMapRecordValues(it.next()));
                }
                // Access the 'Appendable'
                StringBuilder stringBuilder = (StringBuilder) printer.getOut();
                outputStream.write(stringBuilder.toString().getBytes());
                // Reset the 'Appendable' for the next exchange.
                stringBuilder.setLength(0);
            }
        }

        @Override
        protected Iterable<?> getMapRecordValues(Map<?, ?> map) {
            List<String> result = new ArrayList<>(map.size());
            for (Object key : map.keySet()) {
                result.add((String) map.get(key));
            }
            return result;
        }
    }
}