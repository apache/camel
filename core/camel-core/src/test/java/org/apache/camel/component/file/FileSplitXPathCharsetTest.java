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
package org.apache.camel.component.file;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FileSplitXPathCharsetTest extends ContextTestSupport {

    private Path inputCsv;
    private Path inputXml;

    @BeforeEach
    public void setUpTemporaryFiles() {
        inputXml = testFile("input.xml");
        inputCsv = testFile("input.csv");
    }

    @Test
    public void testCsv() throws Exception {
        MockEndpoint out = getMockEndpoint("mock:result");
        out.expectedMessageCount(3);
        out.expectedBodiesReceived("abc", "xyz", "åäö");

        Files.write(inputCsv, "abc,xyz,åäö".getBytes(StandardCharsets.ISO_8859_1));

        out.assertIsSatisfied();
    }

    @Test
    public void testXml() throws Exception {
        MockEndpoint out = getMockEndpoint("mock:result");
        out.expectedMessageCount(3);
        out.expectedBodiesReceived("abc", "xyz", "åäö");

        Files.copy(getClass().getResourceAsStream("FileSplitXPathCharsetTest-input.xml"), inputXml);

        out.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // input: *.csv
                from(fileUri("?charset=ISO-8859-1&include=.*\\.csv")).split().tokenize(",").to("mock:result");

                // input: *.xml
                fromF(fileUri("?charset=ISO-8859-1&include=.*\\.xml")).split().xpath("/foo/bar/text()")
                        .to("mock:result");
            }
        };
    }
}
