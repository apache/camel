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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFileMessage;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class ZipAggregationStrategyTest extends CamelTestSupport {

    private static final int EXPECTED_NO_FILES = 3;

    @Test
    public void testSplitter() throws Exception {
        MockEndpoint aggregateToZipEntry = getMockEndpoint("mock:aggregateToZipEntry");
        aggregateToZipEntry.expectedMessageCount(1);
        assertMockEndpointsSatisfied();

        Exchange out = aggregateToZipEntry.getExchanges().get(0);
        assertTrue("Result message does not contain GenericFileMessage", GenericFileMessage.class.isAssignableFrom(out.getIn().getClass()));
        File resultFile = out.getIn().getBody(File.class);
        assertNotNull(resultFile);
        assertTrue("Zip file should exist", resultFile.isFile());
        assertTrue("Result file name does not end with .zip", resultFile.getName().endsWith(".zip"));

        ZipInputStream zin = new ZipInputStream(new FileInputStream(resultFile));
        try {
            int fileCount = 0;
            for (ZipEntry ze = zin.getNextEntry(); ze != null; ze = zin.getNextEntry()) {
                fileCount++;
            }
            assertTrue("Zip file should contains " + ZipAggregationStrategyTest.EXPECTED_NO_FILES + " files",
                       fileCount == ZipAggregationStrategyTest.EXPECTED_NO_FILES);
        } finally {
            zin.close();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // Unzip file and Split it according to FileEntry
                from("file:src/test/resources/org/apache/camel/aggregate/zipfile/data?consumer.delay=1000&noop=true")
                    .aggregate(new ZipAggregationStrategy())
                        .constant(true)
                        .completionFromBatchConsumer()
                        .eagerCheckCompletion()
                    .to("mock:aggregateToZipEntry")
                    .log("Done processing big file: ${header.CamelFileName}");
            }
        };

    }
}
