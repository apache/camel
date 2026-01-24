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

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileProducerCharsetUTFtoISOConvertBodyToTest extends ContextTestSupport {

    private static final String DATA = "ABC\u00e6";

    private static final String INPUT_FILE
            = "input." + FileProducerCharsetUTFtoISOConvertBodyToTest.class.getSimpleName() + ".txt";
    private static final String OUTPUT_FILE
            = "output." + FileProducerCharsetUTFtoISOConvertBodyToTest.class.getSimpleName() + ".txt";

    @BeforeEach
    void writeTestData() {
        assertDoesNotThrow(() -> {
            try (OutputStream fos = Files.newOutputStream(testFile(INPUT_FILE))) {
                fos.write(DATA.getBytes(StandardCharsets.UTF_8));
            }
        }, "The test cannot run due to an I/O error");
    }

    @AfterEach
    void cleanupFile() {
        assertDoesNotThrow(() -> Files.delete(testFile(OUTPUT_FILE)),
                "The test cannot run due to an error cleaning up");
    }

    @Test
    void testFileProducerCharsetUTFtoISOConvertBodyTo() throws Exception {
        assertTrue(oneExchangeDone.matchesWaitTime());

        final Path outputFile = testFile(OUTPUT_FILE);

        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertFileExists(outputFile));
        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(Files.size(outputFile) > 0));

        byte[] data = Files.readAllBytes(outputFile);

        assertEquals(DATA, new String(data, StandardCharsets.ISO_8859_1));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // the input file is in utf-8
                fromF(fileUri("?initialDelay=0&delay=10&fileName=%s&charset=utf-8"), INPUT_FILE)
                        // now convert the input file from utf-8 to iso-8859-1
                        .convertBodyTo(byte[].class, "iso-8859-1")
                        // and write the file using that encoding
                        .setProperty(Exchange.CHARSET_NAME, header("someCharsetHeader"))
                        .toF(fileUri("?fileName=%s"), OUTPUT_FILE);
            }
        };
    }
}
