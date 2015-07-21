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
package org.apache.camel.processor.aggregate.zipfile;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.junit.Test;

public class AggregationStrategyWithPreservationTest extends CamelTestSupport {

    private static final int EXPECTED_NO_FILES = 5;

    @Override
    public void setUp() throws Exception {
        deleteDirectory("target/out");
        super.setUp();
    }

    @Test
    public void testSplitter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregateToZipEntry");
        mock.expectedMessageCount(1);

        assertMockEndpointsSatisfied();

        Thread.sleep(500);

        File[] files = new File("target/out").listFiles();
        assertTrue("Should be a file in target/out directory", files.length > 0);
        
        File resultFile = files[0];
        Set<String> expectedZipFiles = new HashSet<String>(Arrays.asList("another" + File.separator + "hello.txt", 
                                                                         "other" + File.separator + "greetings.txt",
                                                                         "chiau.txt", "hi.txt", "hola.txt"));
        ZipInputStream zin = new ZipInputStream(new FileInputStream(resultFile));
        try {
            int fileCount = 0;
            for (ZipEntry ze = zin.getNextEntry(); ze != null; ze = zin.getNextEntry()) {
                expectedZipFiles.remove(ze.toString());
                fileCount++;
            }
            assertTrue("Zip file should contains " + AggregationStrategyWithPreservationTest.EXPECTED_NO_FILES + " files",
                       fileCount == AggregationStrategyWithPreservationTest.EXPECTED_NO_FILES);
            assertEquals("Should have found all of the zip files in the file.", 0, expectedZipFiles.size());
        } finally {
            IOHelper.close(zin);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // Unzip file and Split it according to FileEntry
                from("file:src/test/resources/org/apache/camel/aggregate/zipfile/data?consumer.delay=1000&noop=true&recursive=true")
                    .aggregate(new ZipAggregationStrategy(true, true))
                        .constant(true)
                        .completionFromBatchConsumer()
                        .eagerCheckCompletion()
                    .to("file:target/out")
                    .to("mock:aggregateToZipEntry")
                    .log("Done processing zip file: ${header.CamelFileName}");
            }
        };

    }
}
