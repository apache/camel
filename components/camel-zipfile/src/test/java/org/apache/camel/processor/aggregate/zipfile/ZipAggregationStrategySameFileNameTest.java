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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit6.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Two messages with the same entry name (files with the same name from different folders, or the same CamelFileName)
 * must not replace each other in the zip file: the later one is added as {@code name (1).ext}, {@code name (2).ext},
 * ...
 */
class ZipAggregationStrategySameFileNameTest extends CamelTestSupport {

    private static final String DIR = "target/ZipAggregationStrategySameFileNameTest";

    @BeforeEach
    void cleanDirectories() {
        deleteDirectory(DIR);
    }

    @Test
    void secondFileWithSameNameIsAddedUnderANumberedName() throws Exception {
        write(DIR + "/files/a/orders.csv", "from A");
        write(DIR + "/files/b/orders.csv", "from B");
        ZipAggregationStrategy strategy = new ZipAggregationStrategy();

        Exchange answer = strategy.aggregate(null, fileExchange(DIR + "/files/a/orders.csv"));
        answer = strategy.aggregate(answer, fileExchange(DIR + "/files/b/orders.csv"));

        assertEquals(List.of("orders (1).csv=from B", "orders.csv=from A"), entries(answer.getIn().getBody(File.class)));
    }

    @Test
    void secondBodyWithSameFileNameHeaderIsAddedUnderANumberedName() throws Exception {
        ZipAggregationStrategy strategy = new ZipAggregationStrategy(false, true);

        Exchange answer = strategy.aggregate(null, bytesExchange("report.txt", "first"));
        answer = strategy.aggregate(answer, bytesExchange("report.txt", "second"));

        assertEquals(List.of("report (1).txt=second", "report.txt=first"), entries(answer.getIn().getBody(File.class)));
    }

    @Test
    void manyFilesWithSameNameAreAllKept() throws Exception {
        write(DIR + "/files/a/orders.csv", "from A");
        write(DIR + "/files/b/orders (1).csv", "from B, a file with this name");
        write(DIR + "/files/c/orders.csv", "from C");
        write(DIR + "/files/d/orders.csv", "from D");
        write(DIR + "/files/e/orders.csv", "from E");
        ZipAggregationStrategy strategy = new ZipAggregationStrategy();

        Exchange answer = null;
        for (String path : List.of("a/orders.csv", "b/orders (1).csv", "c/orders.csv", "d/orders.csv", "e/orders.csv")) {
            answer = strategy.aggregate(answer, fileExchange(DIR + "/files/" + path));
        }

        assertEquals(List.of(
                "orders (1).csv=from B, a file with this name",
                "orders (2).csv=from C",
                "orders (3).csv=from D",
                "orders (4).csv=from E",
                "orders.csv=from A"), entries(answer.getIn().getBody(File.class)));
    }

    @Test
    void namesWithoutExtensionGetTheNumberAtTheEnd() throws Exception {
        ZipAggregationStrategy strategy = new ZipAggregationStrategy(false, true);

        Exchange answer = strategy.aggregate(null, bytesExchange("README", "one"));
        answer = strategy.aggregate(answer, bytesExchange("README", "two"));
        answer = strategy.aggregate(answer, bytesExchange(".profile", "three"));
        answer = strategy.aggregate(answer, bytesExchange(".profile", "four"));

        assertEquals(List.of(".profile (1)=four", ".profile=three", "README (1)=two", "README=one"),
                entries(answer.getIn().getBody(File.class)));
    }

    @Test
    void recursivePollWithTwoFilesOfTheSameNameZipsBoth() throws Exception {
        // only the two files: the duplicate is the last exchange of the batch and completes the group (eager check)
        write(DIR + "/two/a/orders.csv", "from A");
        write(DIR + "/two/b/orders.csv", "from B");

        assertEquals(List.of("orders (1).csv=from B", "orders.csv=from A"), zipOnce("two"));
    }

    @Test
    void recursivePollWithFilesOfTheSameNameZipsAll() throws Exception {
        write(DIR + "/three/a/orders.csv", "from A");
        write(DIR + "/three/b/orders.csv", "from B");
        write(DIR + "/three/c/other.csv", "from C");

        assertEquals(List.of("orders (1).csv=from B", "orders.csv=from A", "other.csv=from C"), zipOnce("three"));
    }

    @Test
    void preserveFolderStructureKeepsTheFolderNames() throws Exception {
        // the entry names include the folders, so they differ and are not numbered (same result as before the fix)
        write(DIR + "/preserve/a/orders.csv", "from A");
        write(DIR + "/preserve/b/orders.csv", "from B");

        assertEquals(List.of("a/orders.csv=from A", "b/orders.csv=from B"), zipOnce("preserve"));
    }

    private List<String> zipOnce(String routeId) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:" + routeId);
        mock.expectedMessageCount(1);
        context.getRouteController().startRoute(routeId);
        MockEndpoint.assertIsSatisfied(context);

        File[] zips = new File(DIR + "/out-" + routeId).listFiles();
        assertNotNull(zips);
        assertEquals(1, zips.length);
        return entries(zips[0]);
    }

    private Exchange fileExchange(String path) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(new File(path));
        return exchange;
    }

    private Exchange bytesExchange(String fileName, String content) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(content.getBytes(StandardCharsets.UTF_8));
        exchange.getIn().setHeader(Exchange.FILE_NAME, fileName);
        return exchange;
    }

    private static List<String> entries(File zip) throws IOException {
        List<String> answer = new ArrayList<>();
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(zip))) {
            for (ZipEntry ze = zin.getNextEntry(); ze != null; ze = zin.getNextEntry()) {
                if (!ze.isDirectory()) {
                    answer.add(ze.getName() + "=" + new String(zin.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        answer.sort(null);
        return answer;
    }

    private static void write(String path, String content) throws IOException {
        Path p = Path.of(path);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                zipRoute("two", new ZipAggregationStrategy());
                zipRoute("three", new ZipAggregationStrategy());
                zipRoute("preserve", new ZipAggregationStrategy(true));
            }

            private void zipRoute(String id, ZipAggregationStrategy strategy) {
                // the documented pattern: one zip per poll of a recursive file consumer
                from("file:" + DIR + "/" + id + "?recursive=true&noop=true&sortBy=file:path&initialDelay=0&delay=100")
                        .routeId(id).autoStartup(false)
                        .aggregate(constant(true), strategy)
                        .completionFromBatchConsumer()
                        .eagerCheckCompletion()
                        .to("file:" + DIR + "/out-" + id)
                        .to("mock:" + id);
            }
        };
    }
}
