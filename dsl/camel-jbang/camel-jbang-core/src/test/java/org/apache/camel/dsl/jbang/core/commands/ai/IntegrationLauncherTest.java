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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationLauncherTest {

    @Test
    void noFilesRunsEverySourceFileInTheDirectory(@TempDir Path dir) throws Exception {
        // the process is started without a shell, so nothing expands *, and camel run with no files looks for an
        // application.properties with camel.main.routesIncludePattern: the launcher lists the sources itself
        Files.writeString(dir.resolve("b.camel.yaml"), "- from:\n    uri: timer:tick\n    steps: []\n");
        Files.writeString(dir.resolve("a.camel.yaml"), "- from:\n    uri: timer:tock\n    steps: []\n");
        Files.writeString(dir.resolve("application.properties"), "x=1\n");
        Files.writeString(dir.resolve("README.md"), "not a source\n");
        Files.writeString(dir.resolve(".hidden.yaml"), "- from:\n    uri: timer:hidden\n    steps: []\n");
        Files.createDirectory(dir.resolve("sub.yaml"));
        assertThat(IntegrationLauncher.sourceFiles(dir))
                .containsExactly("a.camel.yaml", "application.properties", "b.camel.yaml");
        assertThat(IntegrationLauncher.runArguments(IntegrationLauncher.sourceFiles(dir), null, true, null))
                .containsExactly("run", "a.camel.yaml", "application.properties", "b.camel.yaml", "--dev",
                        "--logging-color=false");
        assertThat(IntegrationLauncher.sourceFiles(dir.resolve("nope"))).isEmpty();
    }

    /** CAMEL-24861: with no files given the directory is the app, so files added later are part of it. */
    @Test
    void noFilesRunsTheDirectoryAsTheApp() {
        assertThat(IntegrationLauncher.sourceDirArguments(null, true, null))
                .containsExactly("run", "--source-dir=.", "--dev", "--logging-color=false");
        assertThat(IntegrationLauncher.sourceDirArguments("demo", false, List.of("--port=9000")))
                .containsExactly("run", "--source-dir=.", "--name=demo", "--logging-color=false", "--port=9000");
    }

    @Test
    void filesNameAndExtraArgumentsArePassedThrough() {
        assertThat(IntegrationLauncher.runArguments(List.of("a.camel.yaml", "application.properties"), "demo", true,
                List.of("--port=9000")))
                .containsExactly("run", "a.camel.yaml", "application.properties", "--dev", "--name=demo",
                        "--logging-color=false", "--port=9000");
    }

    /**
     * CAMEL-25364: a failed start returns what went wrong, not the last stack frames of the runtime. The cause and the
     * first frame of the user's code are kept, the frames are counted.
     */
    @Test
    void failureOutputKeepsTheCauseNotTheFrames(@TempDir Path dir) throws Exception {
        StringBuilder out = new StringBuilder();
        out.append("2026-10-06 01:12:03.101  INFO 81990 --- [           main] org.apache.camel.main.MainSupport  :"
                   + " Apache Camel (JBang) 4.23.0-SNAPSHOT is starting\n");
        out.append("2026-10-06 01:12:04.552 ERROR 81990 --- [           main] org.apache.camel.main.MainSupport  :"
                   + " Failed to create route: order-generator at: >>> Bean[ref:orderNumber method:next] <<<\n");
        out.append("org.apache.camel.FailedToCreateRouteException: Failed to create route: order-generator\n");
        for (int i = 0; i < 30; i++) {
            out.append("\tat org.apache.camel.impl.engine.AbstractCamelContext.startingRoutes(AbstractCamelContext.java:")
                    .append(1196 + i).append(")\n");
        }
        out.append("Caused by: org.apache.camel.NoSuchBeanException: No bean could be found in the registry for:"
                   + " orderNumber\n");
        out.append("\tat org.apache.camel.component.bean.RegistryBean.getBean(RegistryBean.java:94)\n");
        out.append("\tat camel.example.OrderNumber.next(OrderNumber.java:12)\n");
        out.append("\t... 30 more\n");
        Path output = dir.resolve("camel-launch.log");
        Files.writeString(output, out.toString());

        String text = IntegrationLauncher.failureOutput(output);
        assertThat(text).contains("ERROR Failed to create route: order-generator at: >>> Bean[ref:orderNumber");
        assertThat(text).contains("org.apache.camel.FailedToCreateRouteException: Failed to create route: order-generator");
        assertThat(text).contains("Caused by: org.apache.camel.NoSuchBeanException: No bean could be found in the"
                                  + " registry for: orderNumber");
        assertThat(text).contains("at: camel.example.OrderNumber.next(OrderNumber.java:12)");
        assertThat(text).contains("(stack frames left out)");
        assertThat(text).doesNotContain("AbstractCamelContext.startingRoutes");
        // oldest first, as the console printed it
        assertThat(text.indexOf("is starting")).isLessThan(text.indexOf("Failed to create route"));
    }

    /**
     * CAMEL-25364: an exception message over several lines is kept, such as the diagnostics of a Java class that does
     * not compile: they are continuation lines in the same block as the frames, and this output is the only copy.
     */
    @Test
    void failureOutputKeepsAMessageOverSeveralLines(@TempDir Path dir) throws Exception {
        String out = "2026-10-06 01:12:04.552 ERROR 81990 --- [           main] org.apache.camel.main.KameletMain  :"
                     + " Error starting Camel: org.joor.ReflectException: Compilation error:\n"
                     + "org.joor.ReflectException: Compilation error:\n"
                     + "/work/OrderNumber.java:12: error: cannot find symbol\n"
                     + "        return prefix + counter.incrementAndGet();\n"
                     + "  symbol:   variable counter\n"
                     + "\tat org.joor.Compile.compile(Compile.java:178)\n"
                     + "\tat org.apache.camel.dsl.java.joor.MultiCompile.compileUnit(MultiCompile.java:207)\n"
                     + "\tat org.apache.camel.main.KameletMain.run(KameletMain.java:512)\n";
        Path output = dir.resolve("camel-launch.log");
        Files.writeString(output, out);

        String text = IntegrationLauncher.failureOutput(output);
        assertThat(text).contains("ERROR Error starting Camel: org.joor.ReflectException: Compilation error:");
        assertThat(text).contains("/work/OrderNumber.java:12: error: cannot find symbol");
        assertThat(text).contains("symbol:   variable counter");
        assertThat(text).contains("(stack frames left out)");
        assertThat(text).doesNotContain("MultiCompile.compileUnit(");
    }

    /**
     * CAMEL-25364: output with no log records is returned as it is, with its end, where a launcher prints its error.
     */
    @Test
    void failureOutputWithoutLogRecordsKeepsTheEnd(@TempDir Path dir) throws Exception {
        StringBuilder out = new StringBuilder();
        for (int i = 1; i <= 60; i++) {
            out.append("Downloading dependency ").append(i).append('\n');
        }
        out.append("Cannot find dependency org.example:missing:1.0\n");
        Path output = dir.resolve("camel-launch.log");
        Files.writeString(output, out.toString());
        assertThat(IntegrationLauncher.failureOutput(output)).contains("Downloading dependency 60")
                .endsWith("Cannot find dependency org.example:missing:1.0");
    }

    /** CAMEL-25364: console output that is not UTF-8 (a Windows code page) is still returned. */
    @Test
    void failureOutputThatIsNotUtf8IsKept(@TempDir Path dir) throws Exception {
        Path output = dir.resolve("camel-launch.log");
        // C:\Users\Jörg in ISO-8859-1: the ö is a single byte that is not valid UTF-8
        Files.write(output, "Cannot read C:\\Users\\J\u00f6rg\\route.camel.yaml\n".getBytes(StandardCharsets.ISO_8859_1));
        assertThat(IntegrationLauncher.failureOutput(output)).contains("Cannot read C:\\Users\\J").contains("rg\\route");
    }

    @Test
    void failureOutputOfPlainTextIsKept(@TempDir Path dir) throws Exception {
        Path output = dir.resolve("camel-launch.log");
        Files.writeString(output, "Cannot find dependency org.example:missing:1.0\n");
        assertThat(IntegrationLauncher.failureOutput(output)).isEqualTo("Cannot find dependency org.example:missing:1.0");
    }
}
