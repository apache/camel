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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that custom CLI arguments passed via the {@link DoclingHeaders#CUSTOM_ARGUMENTS} header are validated to ensure
 * they do not conflict with producer-managed options such as the output directory.
 */
class DoclingCustomArgsValidationTest extends CamelTestSupport {

    @TempDir
    Path tempDir;

    @Test
    void customArgsWithOutputFlagAreRejected() throws Exception {
        Path inputFile = createInputFile();

        // The --output flag conflicts with the producer-managed output directory
        // and should be rejected before the process is started.
        CamelExecutionException ex = assertThrows(CamelExecutionException.class, () -> {
            template.requestBodyAndHeaders("direct:cli-convert",
                    inputFile.toString(),
                    java.util.Map.of(DoclingHeaders.CUSTOM_ARGUMENTS, List.of("--output", "/tmp/other-dir")));
        });

        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("--output"));
        assertTrue(ex.getCause().getMessage().contains("not allowed"));
    }

    @Test
    void customArgsWithOutputEqualsFormAreRejected() throws Exception {
        Path inputFile = createInputFile();

        CamelExecutionException ex = assertThrows(CamelExecutionException.class, () -> {
            template.requestBodyAndHeaders("direct:cli-convert",
                    inputFile.toString(),
                    java.util.Map.of(DoclingHeaders.CUSTOM_ARGUMENTS, List.of("--output=/tmp/other-dir")));
        });

        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("--output"));
    }

    @Test
    void customArgsWithShortOutputFlagAreRejected() throws Exception {
        Path inputFile = createInputFile();

        CamelExecutionException ex = assertThrows(CamelExecutionException.class, () -> {
            template.requestBodyAndHeaders("direct:cli-convert",
                    inputFile.toString(),
                    java.util.Map.of(DoclingHeaders.CUSTOM_ARGUMENTS, List.of("-o", "/tmp/other-dir")));
        });

        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("-o"));
    }

    @Test
    void customArgsWithPathTraversalAreRejected() throws Exception {
        Path inputFile = createInputFile();

        CamelExecutionException ex = assertThrows(CamelExecutionException.class, () -> {
            template.requestBodyAndHeaders("direct:cli-convert",
                    inputFile.toString(),
                    java.util.Map.of(DoclingHeaders.CUSTOM_ARGUMENTS, List.of("--some-flag", "../../etc/passwd")));
        });

        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("relative path traversal"));
    }

    @Test
    void customArgsWithNullEntryAreRejected() throws Exception {
        Path inputFile = createInputFile();
        List<String> argsWithNull = new java.util.ArrayList<>();
        argsWithNull.add(null);

        CamelExecutionException ex = assertThrows(CamelExecutionException.class, () -> {
            template.requestBodyAndHeaders("direct:cli-convert",
                    inputFile.toString(),
                    java.util.Map.of(DoclingHeaders.CUSTOM_ARGUMENTS, argsWithNull));
        });

        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("null"));
    }

    @Test
    void safeCustomArgsPassValidation() throws Exception {
        Path inputFile = createInputFile();

        // Safe arguments should pass validation and proceed to process execution.
        // The process itself will fail (docling binary not installed in test env),
        // but the error should NOT be IllegalArgumentException — that proves
        // the validation passed and execution moved on to ProcessBuilder.
        CamelExecutionException ex = assertThrows(CamelExecutionException.class, () -> {
            template.requestBodyAndHeaders("direct:cli-convert",
                    inputFile.toString(),
                    java.util.Map.of(DoclingHeaders.CUSTOM_ARGUMENTS, List.of("--verbose", "--table-mode", "fast")));
        });

        // The failure should be from process execution, not from argument validation
        assertFalse(ex.getCause() instanceof IllegalArgumentException,
                "Safe custom arguments should pass validation; failure should come from process execution, not argument validation");
    }

    @Test
    void noCustomArgsIsAllowed() throws Exception {
        Path inputFile = createInputFile();

        // With no custom arguments at all, validation is skipped entirely.
        // The process will still fail (no docling binary), but not from validation.
        CamelExecutionException ex = assertThrows(CamelExecutionException.class, () -> {
            template.requestBody("direct:cli-convert", inputFile.toString());
        });

        assertFalse(ex.getCause() instanceof IllegalArgumentException,
                "No custom arguments should not trigger argument validation");
    }

    private Path createInputFile() throws Exception {
        Path file = tempDir.resolve("test-input.txt");
        Files.writeString(file, "test content");
        return file;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // CLI mode (useDoclingServe=false is the default)
                from("direct:cli-convert")
                        .to("docling:convert?operation=CONVERT_TO_MARKDOWN");
            }
        };
    }
}
