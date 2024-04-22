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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileProducerCharsetUTFtoISOConfiguredTest extends ContextTestSupport {

    private static final String DATA = "ABC\u00e6";
    private static final String INPUT_FILE
            = "input." + FileProducerCharsetUTFtoISOConfiguredTest.class.getSimpleName() + ".txt";
    private static final String OUTPUT_FILE
            = "output." + FileProducerCharsetUTFtoISOConfiguredTest.class.getSimpleName() + ".txt";

    @Test
    void testFileProducerCharsetUTFtoISO() throws Exception {
        try (OutputStream fos = Files.newOutputStream(testFile(INPUT_FILE))) {
            fos.write(DATA.getBytes(StandardCharsets.UTF_8));
        }

        assertTrue(oneExchangeDone.matchesWaitTime());

        assertFileExists(testFile(OUTPUT_FILE));
        byte[] data = Files.readAllBytes(testFile(OUTPUT_FILE));

        assertEquals(DATA, new String(data, StandardCharsets.ISO_8859_1));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF(fileUri("?initialDelay=0&delay=10&charset=utf-8&fileName=%s"), INPUT_FILE)
                        .toF(fileUri("?fileName=%s&charset=iso-8859-1"), OUTPUT_FILE);
            }
        };
    }
}
