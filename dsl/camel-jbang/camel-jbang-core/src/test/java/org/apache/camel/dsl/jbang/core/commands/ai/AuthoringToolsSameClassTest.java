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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25673: asked to change a class of a camel run project, whose sources sit next to the routes, a model wrote a
 * second copy of it under src/main/java instead, which is a second class of the same name. The write is refused and
 * names the file to change; a second beans.yaml or application.properties is written with a note.
 */
class AuthoringToolsSameClassTest {

    private static final String ORDER_NUMBER = """
            package camel.example;

            public class OrderNumber {
                private int counter = 1001;

                public String next() {
                    return "ORD-" + counter++;
                }
            }
            """;

    private static final String ORDER_NUMBER_WITH_START = """
            package camel.example;

            public class OrderNumber {
                private int counter = 1001;

                public void setStart(int start) {
                    this.counter = start;
                }

                public String next() {
                    return "ORD-" + counter++;
                }
            }
            """;

    @TempDir
    Path dir;

    private JsonObject write(String file, String content) {
        return AuthoringTools.writeFile(new ToolContext(), dir, file, content, true);
    }

    private void project(String file, String content) throws IOException {
        Path path = dir.resolve(file);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    @Test
    void aSecondCopyOfAClassInAnotherFolderIsRefused() throws IOException {
        project("OrderNumber.java", ORDER_NUMBER);

        JsonObject result = write("src/main/java/camel/example/OrderNumber.java", ORDER_NUMBER_WITH_START);

        assertThat(result.getString("status")).isEqualTo("invalid");
        assertThat(result.getString("existingFile")).isEqualTo("OrderNumber.java");
        assertThat(((JsonArray) result.get("errors")).get(0).toString())
                .startsWith("the class camel.example.OrderNumber is already in OrderNumber.java");
        assertThat(result.getString("message")).contains("change the class where it is, in OrderNumber.java");
        assertThat(dir.resolve("src/main/java/camel/example/OrderNumber.java")).doesNotExist();
    }

    @Test
    void theClassItselfIsWrittenAsBefore() throws IOException {
        project("OrderNumber.java", ORDER_NUMBER);

        JsonObject result = write("OrderNumber.java", ORDER_NUMBER_WITH_START);

        assertThat(result.getString("status")).isEqualTo("overwritten");
        assertThat(dir.resolve("OrderNumber.java")).content().contains("setStart");
    }

    @Test
    void aClassOfTheSameNameInAnotherPackageIsWritten() throws IOException {
        project("OrderNumber.java", ORDER_NUMBER);

        JsonObject result = write("src/main/java/shop/OrderNumber.java",
                ORDER_NUMBER_WITH_START.replace("package camel.example;", "package shop;"));

        assertThat(result.getString("status")).isEqualTo("created");
    }

    @Test
    void aTestSourceOfTheSameNameIsNotACopy() throws IOException {
        project("src/test/java/camel/example/OrderNumber.java", ORDER_NUMBER);

        JsonObject result = write("src/main/java/camel/example/OrderNumber.java", ORDER_NUMBER_WITH_START);

        assertThat(result.getString("status")).isEqualTo("created");
    }

    @Test
    void aSecondApplicationPropertiesIsWrittenWithANote() throws IOException {
        project("application.properties", "order.period=5000\n");

        JsonObject result = write("src/main/resources/application.properties", "order.first-number=1001\n");

        assertThat(result.getString("status")).isEqualTo("created");
        assertThat(result.get("notes").toString())
                .contains("the project already has application.properties at application.properties");
    }

    @Test
    void aSecondBeansYamlGetsTheNoteToo() throws IOException {
        project("beans.yaml", "- beans: []\n");

        assertThat(AuthoringTools.sameNamedConfigNote(dir, dir.resolve("src/main/resources/beans.yaml")))
                .startsWith("the project already has beans.yaml at beans.yaml");
        // a route file of the same name in another folder is no configuration file: no note
        project("orders.camel.yaml", "- route: {}\n");
        assertThat(AuthoringTools.sameNamedConfigNote(dir, dir.resolve("sub/orders.camel.yaml"))).isNull();
    }
}
