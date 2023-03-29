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
package org.apache.camel.processor.aggregate.tarfile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.test.junit5.TestSupport;
import org.apache.camel.util.IOHelper;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.logging.log4j.core.util.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TarAggregationStrategyEmptyFirstFileTest extends CamelTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        TestSupport.deleteDirectory("target/out");
        super.setUp();
    }

    @Test
    public void testNormal() throws Exception {
        doTest("A", "B", "C");
    }

    @Test
    public void testEmptyFirst() throws Exception {
        doTest("", "A");
    }

    @Test
    public void testEmptyOnly() throws Exception {
        doTest("");
    }

    @Test
    public void testEmptyMiddle() throws Exception {
        doTest("Start", "", "", "End");
    }

    public void doTest(String... messages) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregateToTarEntry");
        mock.expectedMessageCount(1);

        StringBuilder input = new StringBuilder();
        int nonEmptyFile = 0;
        for (String m : messages) {
            input.append("#").append(m);
            if (!m.isEmpty()) {
                nonEmptyFile++;
            }
        }
        if (input.toString().endsWith("#")) {
            input.append("#");
        }

        template.sendBody("direct:start", input.toString());

        MockEndpoint.assertIsSatisfied(context);

        File[] files = new File("target/out").listFiles();
        if (files != null) {
            assertEquals(1, files.length, "Should only be one file in target/out directory");
            Map<String, String> tar = readTar(files[0]);
            assertEquals(nonEmptyFile, tar.size(), "Tar file " + tar + " should contain " + nonEmptyFile + " files");
            Iterator<Entry<String, String>> i = tar.entrySet().iterator();
            for (int n = 0; n < messages.length; n++) {
                if (!messages[n].isEmpty()) {
                    Map.Entry<String, String> entry = i.next();
                    assertEquals(Integer.toString(n), entry.getKey(), "Tar file should contain entry named " + n);
                    assertEquals(messages[n], entry.getValue(), "Tar entry " + n + " content is wrong");
                }
            }
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // @formatter:off
                from("direct:start")
                    .split(body().tokenize("#"), new TarAggregationStrategy(false, true))
                    .setHeader(Exchange.FILE_NAME, simple("${exchangeProperty.CamelSplitIndex}"))
                .end()
                .to("file:target/out")
                .to("mock:aggregateToTarEntry");
                // @formatter:on
            }
        };
    }

    private static Map<String, String> readTar(File file) throws IOException {
        Map<String, String> content = new TreeMap<>();
        TarArchiveInputStream tin = new TarArchiveInputStream(new FileInputStream(file));
        try {
            for (TarArchiveEntry te = (TarArchiveEntry) tin.getNextEntry();
                 te != null;
                 te = (TarArchiveEntry) tin.getNextEntry()) {
                String c = IOUtils.toString(new InputStreamReader(tin));
                content.put(te.getName(), c);
            }
        } finally {
            IOHelper.close(tin);
        }
        return content;
    }
}
