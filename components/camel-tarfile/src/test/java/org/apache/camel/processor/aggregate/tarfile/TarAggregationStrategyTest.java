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
package org.apache.camel.processor.aggregate.tarfile;

import java.io.File;
import java.io.FileInputStream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.Test;

public class TarAggregationStrategyTest extends CamelTestSupport {

    private static final int EXPECTED_NO_FILES = 3;

    private TarAggregationStrategy tar = new TarAggregationStrategy();

    @Override
    public void setUp() throws Exception {
        tar.setParentDir("target/temp");
        deleteDirectory("target/temp");
        deleteDirectory("target/out");
        super.setUp();
    }

    @Test
    public void testSplitter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregateToTarEntry");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("foo", "bar");

        assertMockEndpointsSatisfied();

        Thread.sleep(500);

        File[] files = new File("target/out").listFiles();
        assertTrue(files != null);
        assertTrue("Should be a file in target/out directory", files.length > 0);

        File resultFile = files[0];

        TarArchiveInputStream tin = new TarArchiveInputStream(new FileInputStream(resultFile));
        try {
            int fileCount = 0;
            for (TarArchiveEntry te = tin.getNextTarEntry(); te != null; te = tin.getNextTarEntry()) {
                fileCount = fileCount + 1;
            }
            assertEquals("Tar file should contains " + TarAggregationStrategyTest.EXPECTED_NO_FILES + " files", TarAggregationStrategyTest.EXPECTED_NO_FILES, fileCount);
        } finally {
            IOHelper.close(tin);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // Untar file and Split it according to FileEntry
                from("file:src/test/resources/org/apache/camel/aggregate/tarfile/data?consumer.delay=1000&noop=true")
                    .setHeader("foo", constant("bar"))
                    .aggregate(tar)
                        .constant(true)
                        .completionFromBatchConsumer()
                        .eagerCheckCompletion()
                    .to("file:target/out")
                    .to("mock:aggregateToTarEntry")
                    .log("Done processing tar file: ${header.CamelFileName}");
            }
        };

    }
}
