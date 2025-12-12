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
import java.util.Map;
import java.util.TreeMap;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.test.junit6.TestSupport;
import org.apache.camel.util.IOHelper;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.logging.log4j.core.util.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TarAggregationStrategyNullBodyTest extends CamelTestSupport {

    @BeforeEach
    public void cleanOutputDir() {
        TestSupport.deleteDirectory("target/out");
    }

    @Test
    void testNullBodyLast() throws Exception {
        template.sendBody("direct:start", "Hello");
        template.sendBody("direct:start", "Hello again");
        template.sendBody("direct:start", null);
        assertTarFileContains(2);
    }

    @Test
    void testNullBodyFirst() throws Exception {
        template.sendBody("direct:start", null);
        template.sendBody("direct:start", "Hello");
        template.sendBody("direct:start", "Hello again");
        assertTarFileContains(2);
    }

    @Test
    void testNullBodyMiddle() throws Exception {
        template.sendBody("direct:start", "Hello");
        template.sendBody("direct:start", null);
        template.sendBody("direct:start", "Hello again");
        assertTarFileContains(2);
    }

    @Test
    void testNullBodiesOnly() throws Exception {
        template.sendBody("direct:start", null);
        template.sendBody("direct:start", null);
        template.sendBody("direct:start", null);
        assertTarFileContains(0);
    }

    @Test
    void testTwoNullBodies() throws Exception {
        template.sendBody("direct:start", null);
        template.sendBody("direct:start", null);
        template.sendBody("direct:start", "Hello");
        assertTarFileContains(1);
    }

    public void assertTarFileContains(int filesInTarExpected) throws Exception {
        await("Should be a file in target/out directory").until(() -> {
            File[] files = new File("target/out").listFiles();
            return files != null && files.length > 0;
        });
        File[] files = new File("target/out").listFiles();
        assertEquals(1, files.length, "Should only be one file in target/out directory");
        Map<String, String> tar = readTar(files[0]);
        assertEquals(filesInTarExpected, tar.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // @formatter:off
                from("direct:start")
                        .aggregate(new TarAggregationStrategy(false))
                        .constant(true)
                        .completionSize(3)
                        .eagerCheckCompletion()
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
            for (TarArchiveEntry te = tin.getNextEntry();
                 te != null;
                 te = tin.getNextEntry()) {
                String c = IOUtils.toString(new InputStreamReader(tin));
                content.put(te.getName(), c);
            }
        } finally {
            IOHelper.close(tin);
        }
        return content;
    }
}
