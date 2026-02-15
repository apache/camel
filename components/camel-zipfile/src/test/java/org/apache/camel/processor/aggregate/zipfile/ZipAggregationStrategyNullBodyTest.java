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
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit6.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ZipAggregationStrategyNullBodyTest extends CamelTestSupport {

    private static final String TEST_DIR = "target/out_ZipAggregationStrategyNullBodyTest";
    public static final String MOCK_AGGREGATE_TO_ZIP_ENTRY = "mock:aggregateToZipEntry";

    @BeforeEach
    public void deleteTestDirs() {
        deleteDirectory(TEST_DIR);
    }

    @Test
    public void testNullBodyLast() throws Exception {
        MockEndpoint mock = getMockEndpoint(MOCK_AGGREGATE_TO_ZIP_ENTRY);
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "Hello");
        template.sendBody("direct:start", "Hello again");
        template.sendBody("direct:start", null);

        assertZipContainsFiles(2);
    }

    @Test
    public void testNullBodyFirst() throws Exception {
        MockEndpoint mock = getMockEndpoint(MOCK_AGGREGATE_TO_ZIP_ENTRY);
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", null);
        template.sendBody("direct:start", "Hello");
        template.sendBody("direct:start", "Hello again");

        assertZipContainsFiles(2);
    }

    @Test
    public void testNullBodyMiddle() throws Exception {
        MockEndpoint mock = getMockEndpoint(MOCK_AGGREGATE_TO_ZIP_ENTRY);
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "Hello");
        template.sendBody("direct:start", null);
        template.sendBody("direct:start", "Hello again");

        assertZipContainsFiles(2);
    }

    @Test
    public void testNullBodiesOnly() throws Exception {
        MockEndpoint mock = getMockEndpoint(MOCK_AGGREGATE_TO_ZIP_ENTRY);
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", null);
        template.sendBody("direct:start", null);
        template.sendBody("direct:start", null);

        assertZipContainsFiles(0);
    }

    @Test
    public void testTwoNullBodies() throws Exception {
        MockEndpoint mock = getMockEndpoint(MOCK_AGGREGATE_TO_ZIP_ENTRY);
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", null);
        template.sendBody("direct:start", null);
        template.sendBody("direct:start", "Hello");

        assertZipContainsFiles(1);
    }

    private void assertZipContainsFiles(int expectedCount) throws InterruptedException, IOException {
        MockEndpoint.assertIsSatisfied(context);

        File[] files = new File(TEST_DIR).listFiles();
        assertNotNull(files);
        assertTrue(files.length > 0, "Should be a file in " + TEST_DIR + " directory");

        File resultFile = files[0];

        ZipInputStream zin = new ZipInputStream(new FileInputStream(resultFile));
        try {
            int fileCount = 0;
            for (ZipEntry ze = zin.getNextEntry(); ze != null; ze = zin.getNextEntry()) {
                fileCount++;
            }
            assertEquals(expectedCount, fileCount,
                    "Zip file should contains " + expectedCount + " files");
        } finally {
            IOHelper.close(zin);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .aggregate(new ZipAggregationStrategy())
                        .constant(true)
                        .completionSize(3)
                        .eagerCheckCompletion()
                        .to("file:" + TEST_DIR)
                        .to(MOCK_AGGREGATE_TO_ZIP_ENTRY)
                        .log("Done processing zip file: ${header.CamelFileName}");

            }
        };

    }
}
