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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25814: a Java file is compiled together with the other Java files of the project, as camel run compiles them,
 * so a class that uses another class of the project is not refused.
 */
class JavaChecksProjectTest {

    private static final String ORDER = """
            package camel.example;

            import java.util.List;

            public record Order(String orderId, String customer, List<OrderLine> lines) {
            }
            """;

    @TempDir
    Path project;

    @Test
    void aClassThatUsesAnotherClassOfTheProjectCompiles() throws Exception {
        write("src/main/java/camel/example/OrderLine.java", """
                package camel.example;

                public record OrderLine(String sku, int qty, double price) {
                }
                """);

        assertThat(JavaChecks.validateJava("src/main/java/camel/example/Order.java", ORDER, project)).isEmpty();
        // on its own, as before: the class of the other file is not found
        assertThat(JavaChecks.validateJava("src/main/java/camel/example/Order.java", ORDER)).isNotEmpty()
                .first().asString().contains("cannot find symbol");
    }

    @Test
    void aClassNextToTheFileCompilesToo() throws Exception {
        write("OrderLine.java", """
                public record OrderLine(String sku, int qty) {
                }
                """);

        assertThat(JavaChecks.validateJava("Order.java", """
                import java.util.List;

                public record Order(String orderId, List<OrderLine> lines) {
                }
                """, project)).isEmpty();
    }

    @Test
    void theErrorsOfAnotherFileAreNotThisFilesProblem() throws Exception {
        write("src/main/java/camel/example/OrderLine.java", """
                package camel.example;

                public record OrderLine(String sku, int qty, double price) {
                }
                """);
        write("src/main/java/camel/example/Broken.java", """
                package camel.example;

                public class Broken {
                    void x() { int i = "text" }
                }
                """);

        assertThat(JavaChecks.validateJava("src/main/java/camel/example/Order.java", ORDER, project)).isEmpty();
    }

    @Test
    void theFileOnDiskIsReplacedByTheContent() throws Exception {
        // the version on disk is the one being rewritten: it is no second class of the same name
        write("src/main/java/camel/example/Order.java", """
                package camel.example;

                public class Order {
                    private String orderId;
                }
                """);
        write("src/main/java/camel/example/OrderLine.java", """
                package camel.example;

                public record OrderLine(String sku, int qty, double price) {
                }
                """);

        assertThat(JavaChecks.validateJava("src/main/java/camel/example/Order.java", ORDER, project)).isEmpty();
    }

    @Test
    void aClassThatIsNowhereIsStillReported() {
        List<String> msgs = JavaChecks.validateJava("src/main/java/camel/example/Order.java", ORDER, project);
        assertThat(msgs).isNotEmpty();
        assertThat(msgs.get(0)).contains("cannot find symbol").contains("OrderLine");
    }

    private void write(String file, String content) throws Exception {
        Path p = project.resolve(file);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }
}
