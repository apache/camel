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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
public class FileProducerCharsetUTFOptimizedTest extends ContextTestSupport {

    // use utf-8 as original payload with 00e6 which is a danish ae letter
    private byte[] utf = "ABC\u00e6D\uD867\uDE3DE\uD83C\uDFF3".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    public void createData() throws IOException {
        testDirectory("input", true);

        log.debug("utf: {}", new String(utf, StandardCharsets.UTF_8));
        for (byte b : utf) {
            log.debug("utf byte: {}", b);
        }
        // write the byte array to a file using plain API
        try (OutputStream fos = Files.newOutputStream(testFile("input/input.txt"))) {
            fos.write(utf);
        }
    }

    @Test
    public void testFileProducerCharsetUTFOptimized() throws Exception {
        oneExchangeDone.matchesWaitTime();

        assertTrue(Files.exists(testFile("output.txt")), "File should exist");

        byte[] data = Files.readAllBytes(testFile("output.txt"));
        assertArrayEquals(utf, data);
    }

    @AfterEach
    public void deleteData() {
        deleteDirectory(testDirectory().toFile());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri("input?initialDelay=0&delay=10&noop=true"))
                        // no charset so its optimized to write directly
                        .to(fileUri("?fileName=output.txt"));
            }
        };
    }
}
