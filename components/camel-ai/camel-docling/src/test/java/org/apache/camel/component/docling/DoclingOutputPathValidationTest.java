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
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests that the {@code CamelDoclingOutputFilePath} header is normalized and, when {@code outputBaseDirectory} is
 * configured, confined to that directory before it reaches the docling CLI {@code --output} flag, consistently with how
 * input paths honour {@code inputBaseDirectory}.
 */
class DoclingOutputPathValidationTest extends CamelTestSupport {

    private static final String CONTENT = "just some document text";

    @TempDir
    Path tempDir;

    // ------------------------------------------------------- no outputBaseDirectory

    @Test
    void outputPathWithoutBaseDirectoryIsAllowed() {
        // no restriction is configured: the header is normalized only and passes through, so the run fails later on the
        // absent docling binary rather than on a containment check
        assertThatThrownBy(() -> template.requestBodyAndHeader("direct:default", CONTENT,
                DoclingHeaders.OUTPUT_FILE_PATH, tempDir.resolve("out").toString()))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .hasMessageNotContaining("outputBaseDirectory");
    }

    @Test
    void outputPathIsNormalizedInTheBuiltCommand() throws Exception {
        // with outputBaseDirectory unset (the default, and the branch most routes hit) the header is still
        // normalized lexically before it reaches --output: out/./sub/../x collapses to out/x
        List<String> command = buildDoclingCommandFor("out/./sub/../x");

        int i = command.indexOf("--output");
        assertThat(i).isGreaterThanOrEqualTo(0);
        assertThat(command.get(i + 1))
                .isEqualTo(Paths.get("out", "x").toString())
                .doesNotContain("..");
    }

    // ------------------------------------------------------ outputBaseDirectory jail

    @Test
    void outputPathInsideOutputBaseDirectoryIsAccepted() throws Exception {
        String inside = baseDir().resolve("out").toString();

        // the docling binary is absent so execution still fails, but it must not fail on the jail check
        assertThatThrownBy(() -> template.requestBodyAndHeader("direct:jailed", CONTENT,
                DoclingHeaders.OUTPUT_FILE_PATH, inside))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .hasMessageNotContaining("outputBaseDirectory");
    }

    @Test
    void outputPathOutsideOutputBaseDirectoryIsRejected() throws Exception {
        baseDir();
        String outside = tempDir.resolve("outside").toString();

        assertThatThrownBy(() -> template.requestBodyAndHeader("direct:jailed", CONTENT,
                DoclingHeaders.OUTPUT_FILE_PATH, outside))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IOException.class)
                .hasMessageContaining("outputBaseDirectory");
    }

    @Test
    void absoluteOutputPathOutsideOutputBaseDirectoryIsRejected() throws Exception {
        baseDir();

        // an absolute header value ignores the base directory when resolved, so it must be rejected as escaping
        assertThatThrownBy(() -> template.requestBodyAndHeader("direct:jailed", CONTENT,
                DoclingHeaders.OUTPUT_FILE_PATH, "/var/www/html/uploads"))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IOException.class)
                .hasMessageContaining("outputBaseDirectory");
    }

    @Test
    void traversalOutOfOutputBaseDirectoryIsRejected() throws Exception {
        baseDir();

        // a genuinely relative value, so the baseDir.resolve(..) + normalize() branch is exercised; an absolute
        // value would instead hit the same branch as absoluteOutputPathOutsideOutputBaseDirectoryIsRejected
        assertThatThrownBy(() -> template.requestBodyAndHeader("direct:jailed", CONTENT,
                DoclingHeaders.OUTPUT_FILE_PATH, "../outside"))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IOException.class)
                .hasMessageContaining("outputBaseDirectory");
    }

    @Test
    void siblingDirectorySharingANamePrefixIsRejected() throws Exception {
        // "<base>-evil" shares a string prefix with "<base>" but is not inside it; a plain String.startsWith
        // comparison would wrongly accept this
        baseDir();
        String sibling = tempDir.resolve("base-evil").resolve("out").toString();

        assertThatThrownBy(() -> template.requestBodyAndHeader("direct:jailed", CONTENT,
                DoclingHeaders.OUTPUT_FILE_PATH, sibling))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IOException.class)
                .hasMessageContaining("outputBaseDirectory");
    }

    // ------------------------------------------------------------------ configuration

    @Test
    void outputBaseDirectoryDefaultsToNull() {
        assertThat(new DoclingConfiguration().getOutputBaseDirectory()).isNull();
    }

    private Path baseDir() throws IOException {
        return Files.createDirectories(tempDir.resolve("base"));
    }

    private List<String> buildDoclingCommandFor(String outputHeader) throws Exception {
        // outputBaseDirectory is unset on this endpoint, so buildDoclingCommand exercises the no-base branch
        DoclingEndpoint endpoint
                = context.getEndpoint("docling:convert?operation=CONVERT_TO_MARKDOWN", DoclingEndpoint.class);
        DoclingProducer producer = (DoclingProducer) endpoint.createProducer();
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(DoclingHeaders.OUTPUT_FILE_PATH, outputHeader);

        Method m = DoclingProducer.class.getDeclaredMethod(
                "buildDoclingCommand", String.class, String.class, Exchange.class, String.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> command = (List<String>) m.invoke(producer, "input.pdf", "markdown", exchange, "/tmp/managed");
        return command;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                String missingBinary = tempDir.resolve("no-such-docling").toString();

                from("direct:default")
                        .to("docling:convert?operation=CONVERT_TO_MARKDOWN&doclingCommand=" + missingBinary);

                from("direct:jailed")
                        .to("docling:convert?operation=CONVERT_TO_MARKDOWN"
                            + "&outputBaseDirectory=" + baseDir() + "&doclingCommand=" + missingBinary);
            }
        };
    }
}
