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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.junit.Test;

public class ZipAggregationStrategyEmptyFileTest extends CamelTestSupport {

    private static final int EXPECTED_NO_FILES = 3;

    @Override
    public void setUp() throws Exception {
        deleteDirectory("target/foo");
        deleteDirectory("target/out");
        super.setUp();
    }

    @Test
    public void testEmptyFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregateToZipEntry");
        mock.expectedMessageCount(1);

        template.sendBody("file:target/foo", "Hello");
        // empty file which is not aggregated
        template.sendBody("file:target/foo", "");
        template.sendBody("file:target/foo", "Bye");
        template.sendBody("file:target/foo", "Howdy");

        assertMockEndpointsSatisfied();

        Thread.sleep(500);

        File[] files = new File("target/out").listFiles();
        assertTrue(files != null);
        assertTrue("Should be a file in target/out directory", files.length > 0);

        File resultFile = files[0];

        ZipInputStream zin = new ZipInputStream(new FileInputStream(resultFile));
        try {
            int fileCount = 0;
            for (ZipEntry ze = zin.getNextEntry(); ze != null; ze = zin.getNextEntry()) {
                fileCount = fileCount + 1;
            }
            assertEquals("Zip file should contains " + ZipAggregationStrategyEmptyFileTest.EXPECTED_NO_FILES + " files", ZipAggregationStrategyEmptyFileTest.EXPECTED_NO_FILES, fileCount);
        } finally {
            IOHelper.close(zin);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/foo")
                    .aggregate(new ZipAggregationStrategy())
                        .constant(true)
                        .completionSize(4)
                        .eagerCheckCompletion()
                    .to("file:target/out")
                    .to("mock:aggregateToZipEntry")
                    .log("Done processing zip file: ${header.CamelFileName}");
            }
        };

    }
}
