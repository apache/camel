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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * <b>Camel</b> based test cases for {@link org.apache.camel.dataformat.csv.CsvDataFormat}.
 */
public class CsvMarshalHeaderTest extends CamelTestSupport {

    @TempDir
    File folder;

    @Produce("direct:start")
    ProducerTemplate producerTemplate;

    File outputFile;

    @Override
    protected void doPreSetup() {
        outputFile = new File(folder, "output.csv");
    }

    @Test
    void testSendBody() throws IOException {
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
        try (Stream<String> stream = Files.lines(Paths.get(outputFile.toURI()))
                .filter(l -> !l.isBlank())) {
            List<String> lines = stream.toList();
            // We got twice the headers... :(
            assertEquals(4, lines.size());
        }
    }

    @Test
    void testSendBodyWithList() throws IOException {
        List<List<String>> body = Collections.singletonList(Arrays.asList("John", "Doe"));
        String fileName = outputFile.getName();
        assertEquals("output.csv", fileName);
        producerTemplate.sendBodyAndHeader(body, Exchange.FILE_NAME, fileName);
        body = Collections.singletonList(Arrays.asList("Max", "Mustermann"));
        producerTemplate.sendBodyAndHeader(body, Exchange.FILE_NAME, fileName);
        try (Stream<String> stream = Files.lines(Paths.get(outputFile.toURI()))
                .filter(l -> !l.isBlank())) {
            List<String> lines = stream.toList();
            // We got twice the headers... :(
            assertEquals(4, lines.size());
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String uri
                        = String.format("file:%s?charset=utf-8&fileExist=Append", outputFile.getParentFile().getAbsolutePath());
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
        return dataFormat;
    }
}
