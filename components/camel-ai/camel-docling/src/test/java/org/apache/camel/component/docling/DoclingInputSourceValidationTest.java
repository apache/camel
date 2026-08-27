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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests that a String message body is only interpreted as a remote URL or as a local filesystem path when the route has
 * explicitly opted in, and that local input paths honour {@code inputBaseDirectory} when one is configured.
 */
class DoclingInputSourceValidationTest extends CamelTestSupport {

    @TempDir
    Path tempDir;

    // ------------------------------------------------------------------ URL bodies

    @Test
    void urlBodyIsRejectedByDefault() {
        assertThatThrownBy(() -> template.requestBody("direct:default", "http://example.org/doc.pdf"))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowUrlSource");
    }

    @Test
    void httpsUrlBodyIsRejectedByDefault() {
        assertThatThrownBy(() -> template.requestBody("direct:default", "https://example.org/doc.pdf"))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowUrlSource");
    }

    @Test
    void urlBodyPassesValidationWhenAllowed() {
        // validation lets it through; the failure that follows comes from executing the (absent) docling binary,
        // which is what proves the input-source check was not the thing that rejected it
        assertThatThrownBy(() -> template.requestBody("direct:allow-url", "http://example.org/doc.pdf"))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isNotInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------ file path bodies

    @Test
    void filePathBodyIsRejectedByDefault() throws Exception {
        Path input = Files.writeString(tempDir.resolve("input.txt"), "hello");

        assertThatThrownBy(() -> template.requestBody("direct:default", input.toString()))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowFilePathSource");
    }

    @Test
    void windowsStyleFilePathBodyIsRejectedByDefault() {
        assertThatThrownBy(() -> template.requestBody("direct:default", "C:\\docs\\input.pdf"))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowFilePathSource");
    }

    @Test
    void filePathBodyPassesValidationWhenAllowed() throws Exception {
        Path input = Files.writeString(tempDir.resolve("input.txt"), "hello");

        assertThatThrownBy(() -> template.requestBody("direct:allow-path", input.toString()))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isNotInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingFilePathBodyIsRejectedEvenWhenAllowed() {
        // a path that does not resolve to anything on disk used to pass the size check silently
        assertThatThrownBy(() -> template.requestBody("direct:allow-path", tempDir.resolve("absent.txt").toString()))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IOException.class)
                .hasMessageContaining("File not found");
    }

    @Test
    void plainContentBodyIsNotAffected() {
        // neither a URL nor a path: still treated as the document itself, with no opt-in required
        assertThatThrownBy(() -> template.requestBody("direct:default", "just some document text"))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isNotInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------- inputBaseDirectory jail

    @Test
    void filePathInsideInputBaseDirectoryIsAccepted() throws Exception {
        Path input = Files.writeString(baseDir().resolve("inside.txt"), "hello");

        // the docling binary is absent so execution still fails, but it must not fail on the jail check
        assertThatThrownBy(() -> template.requestBody("direct:jailed", input.toString()))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .hasMessageNotContaining("inputBaseDirectory");
    }

    @Test
    void filePathOutsideInputBaseDirectoryIsRejected() throws Exception {
        Path outside = Files.writeString(tempDir.resolve("outside.txt"), "hello");

        assertThatThrownBy(() -> template.requestBody("direct:jailed", outside.toString()))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IOException.class)
                .hasMessageContaining("inputBaseDirectory");
    }

    @Test
    void traversalOutOfInputBaseDirectoryIsRejected() throws Exception {
        Files.writeString(tempDir.resolve("outside.txt"), "hello");
        String traversal = baseDir().resolve("..").resolve("outside.txt").toString();

        assertThatThrownBy(() -> template.requestBody("direct:jailed", traversal))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IOException.class)
                .hasMessageContaining("inputBaseDirectory");
    }

    @Test
    void siblingDirectorySharingANamePrefixIsRejected() throws Exception {
        // "<base>-evil" shares a string prefix with "<base>" but is not inside it; a plain String.startsWith
        // comparison would wrongly accept this
        Path sibling = Files.createDirectories(tempDir.resolve("base-evil"));
        Path input = Files.writeString(sibling.resolve("input.txt"), "hello");

        assertThatThrownBy(() -> template.requestBody("direct:jailed", input.toString()))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IOException.class)
                .hasMessageContaining("inputBaseDirectory");
    }

    // ------------------------------------------------------------------- header path

    @Test
    void headerPathIsNotGatedByAllowFilePathSource() throws Exception {
        Path input = Files.writeString(tempDir.resolve("header-input.txt"), "hello");

        // the header is an explicit "the document lives here" signal, so it keeps working without opting in
        assertThatThrownBy(() -> template.requestBodyAndHeader("direct:default", null,
                DoclingHeaders.INPUT_FILE_PATH, input.toString()))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isNotInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void headerPathOutsideInputBaseDirectoryIsRejected() throws Exception {
        Path outside = Files.writeString(tempDir.resolve("header-outside.txt"), "hello");

        assertThatThrownBy(() -> template.requestBodyAndHeader("direct:jailed", null,
                DoclingHeaders.INPUT_FILE_PATH, outside.toString()))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IOException.class)
                .hasMessageContaining("inputBaseDirectory");
    }

    // ------------------------------------------------------------ batch operations

    @Test
    void batchStringBodyIsRejectedByDefault() {
        assertThatThrownBy(() -> template.requestBody("direct:batch", tempDir.toString()))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowFilePathSource");
    }

    @Test
    void batchTypedPathListIsNotGated() throws Exception {
        Path input = Files.writeString(tempDir.resolve("batch-input.txt"), "hello");

        // an explicit List<String> of paths cannot be mistaken for document content, so it needs no opt-in
        assertThatThrownBy(() -> template.requestBody("direct:batch", List.of(input.toString())))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isNotInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void configurationDefaultsAreClosed() {
        DoclingConfiguration configuration = new DoclingConfiguration();

        assertThat(configuration.isAllowUrlSource()).isFalse();
        assertThat(configuration.isAllowFilePathSource()).isFalse();
        assertThat(configuration.getInputBaseDirectory()).isNull();
    }

    private Path baseDir() throws IOException {
        return Files.createDirectories(tempDir.resolve("base"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                String missingBinary = tempDir.resolve("no-such-docling").toString();

                from("direct:default")
                        .to("docling:convert?operation=CONVERT_TO_MARKDOWN&doclingCommand=" + missingBinary);

                from("direct:allow-url")
                        .to("docling:convert?operation=CONVERT_TO_MARKDOWN&allowUrlSource=true&doclingCommand="
                            + missingBinary);

                from("direct:allow-path")
                        .to("docling:convert?operation=CONVERT_TO_MARKDOWN&allowFilePathSource=true&doclingCommand="
                            + missingBinary);

                from("direct:jailed")
                        .to("docling:convert?operation=CONVERT_TO_MARKDOWN&allowFilePathSource=true"
                            + "&inputBaseDirectory=" + baseDir() + "&doclingCommand=" + missingBinary);

                // batch operations are docling-serve only; the serve call fails against the unused default
                // endpoint, which is enough to show the input-source check let the body through
                from("direct:batch")
                        .to("docling:convert?operation=BATCH_CONVERT_TO_MARKDOWN&useDoclingServe=true");
            }
        };
    }
}
