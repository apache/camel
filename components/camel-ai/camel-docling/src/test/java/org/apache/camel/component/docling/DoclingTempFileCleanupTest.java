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
package org.apache.camel.component.docling;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that temporary files created in {@code DoclingProducer.getInputPath()} are cleaned up after exchange
 * processing completes.
 *
 * <p>
 * Before the fix, temp files created for String content and byte[] bodies accumulated on disk indefinitely. After the
 * fix, an {@code addOnCompletion} callback deletes them when the exchange finishes.
 */
class DoclingTempFileCleanupTest extends CamelTestSupport {

    @Test
    void tempFileFromStringContentIsCleanedUp() throws Exception {
        // Snapshot temp files before
        List<Path> before = listDoclingTempFiles();

        // Send string content (not a path, not a URL) — this triggers temp file creation.
        // The docling CLI will fail (not installed), but the temp file cleanup
        // runs on exchange completion regardless of success or failure.
        try {
            template.requestBody("direct:convert", "This is raw text content to convert");
        } catch (CamelExecutionException e) {
            // Expected — docling binary not available in test env
        }

        // After exchange completes, temp files should have been cleaned up
        List<Path> after = listDoclingTempFiles();
        List<Path> leaked = new ArrayList<>(after);
        leaked.removeAll(before);

        assertTrue(leaked.isEmpty(),
                "Temp files leaked after exchange completion: " + leaked);
    }

    @Test
    void tempFileFromByteArrayIsCleanedUp() throws Exception {
        List<Path> before = listDoclingTempFiles();

        try {
            template.requestBody("direct:convert", "Binary content for conversion".getBytes());
        } catch (CamelExecutionException e) {
            // Expected — docling binary not available in test env
        }

        List<Path> after = listDoclingTempFiles();
        List<Path> leaked = new ArrayList<>(after);
        leaked.removeAll(before);

        assertTrue(leaked.isEmpty(),
                "Temp files leaked after exchange completion: " + leaked);
    }

    private List<Path> listDoclingTempFiles() throws IOException {
        List<Path> result = new ArrayList<>();
        Path tmpDir = Path.of(System.getProperty("java.io.tmpdir"));
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tmpDir, "docling-*.tmp")) {
            for (Path entry : stream) {
                result.add(entry);
            }
        }
        return result;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:convert")
                        .to("docling:convert?operation=CONVERT_TO_MARKDOWN");
            }
        };
    }
}
