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
package org.apache.camel.processor.aggregate.zipfile;

import java.io.File;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.junit.Before;
import org.junit.Test;

public class AggregationStrategyWithFilenameHeaderTest extends CamelTestSupport {

    private static final List<String> FILE_NAMES = Arrays.asList("foo", "bar");
    private static final String TEST_DIR = "target/out_AggregationStrategyWithFilenameHeaderTest";

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory(TEST_DIR);
        super.setUp();
    }

    @Test
    public void testSplitter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregateToZipEntry");
        mock.expectedMessageCount(1);

        template.setDefaultEndpointUri("direct:start");
        template.sendBodyAndHeader("foo", Exchange.FILE_NAME, FILE_NAMES.get(0));
        template.sendBodyAndHeader("bar", Exchange.FILE_NAME, FILE_NAMES.get(1));
        assertMockEndpointsSatisfied();

        File[] files = new File(TEST_DIR).listFiles();
        assertNotNull(files);
        assertTrue("Should be a file in " + TEST_DIR + " directory", files.length > 0);

        File resultFile = files[0];

        final ZipFile file = new ZipFile(resultFile);
        try {
            final Enumeration<? extends ZipEntry> entries = file.entries();
            int fileCount = 0;
            while (entries.hasMoreElements()) {
                fileCount++;
                final ZipEntry entry = entries.nextElement();
                assertTrue("Zip entry file name should be on of: " + FILE_NAMES, FILE_NAMES.contains(entry.getName()));
            }
            assertEquals("Zip file should contain " + FILE_NAMES.size() + " files", FILE_NAMES.size(), fileCount);
        } finally {
            IOHelper.close(file);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .aggregate(new ZipAggregationStrategy(false, true))
                            .constant(true)
                            .completionTimeout(50)
                            .to("file:" + TEST_DIR)
                            .to("mock:aggregateToZipEntry")
                            .log("Done processing zip file: ${header.CamelFileName}");
            }
        };

    }
}
