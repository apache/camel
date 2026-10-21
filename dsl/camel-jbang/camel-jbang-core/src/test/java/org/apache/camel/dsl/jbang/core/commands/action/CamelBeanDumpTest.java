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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * camel get bean: the beans of an integration with their properties, as the bean dev console has them, and the beans of
 * the YAML or XML DSL with their configuration, as the bean-model dev console has them.
 */
@ExtendWith(MockitoExtension.class)
class CamelBeanDumpTest extends ActionCommandTestSupport {

    @Test
    void testRequestsBeanDumpAndRendersBeans() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command(), beans(bean("myBean", "com.foo.MyBean",
                property("greeting", "java.lang.String", "helloWorld"))))).isZero();

        JsonObject action = readActionFile(TEST_PID);
        assertThat(action.getString("action")).isEqualTo("bean");
        assertThat(action.containsKey("filter")).isFalse();
        assertThat(action.getBoolean("properties")).isTrue();
        assertThat(action.getBoolean("nulls")).isTrue();
        assertThat(action.getBoolean("internal")).isFalse();
        assertThat(printer.getOutput()).contains("BEAN: myBean (com.foo.MyBean):", "PROPERTY", "TYPE", "VALUE", "greeting",
                "java.lang.String", "helloWorld");
    }

    @Test
    void aNullValue() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command(), beans(bean("myBean", "com.foo.MyBean",
                property("greeting", "java.lang.String", null))))).isZero();

        assertThat(lineOf("greeting")).contains("null");
    }

    @Test
    void withoutPropertiesATableOfTheBeans() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("--properties=false"), someBeans())).isZero();

        String[] lines = printer.getOutput().split(System.lineSeparator());
        assertThat(lines[0]).contains("NAME", "TYPE");
        assertThat(printer.getOutput()).doesNotContain("BEAN:", "PROPERTY");
        // by name
        assertThat(lines[1]).contains("camel-tracer");
        assertThat(lines[2]).contains("executor", "java.util.concurrent.ThreadPoolExecutor");
    }

    @Test
    void sortByType() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("--properties=false", "--sort=type"), someBeans())).isZero();

        String[] lines = printer.getOutput().split(System.lineSeparator());
        assertThat(lines[1]).contains("com.foo.OrderService");
        assertThat(lines[5]).contains("org.springframework");
    }

    @Test
    void userScopeLeavesOutTheBeansOfTheFrameworksAndTheJdk() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("--properties=false", "--scope=user"), someBeans())).isZero();

        // the internal beans are asked for, as a scope picks from all of them
        assertThat(readActionFile(TEST_PID).getBoolean("internal")).isTrue();
        assertThat(printer.getOutput()).contains("orders").doesNotContain("camel-tracer", "executor", "quarkus",
                "spring");
    }

    @Test
    void camelScope() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("--properties=false", "--scope=camel"), someBeans())).isZero();

        assertThat(printer.getOutput()).contains("camel-tracer").doesNotContain("orders", "executor");
    }

    @Test
    void unknownScopeIsAUsageError() {
        CamelBeanDump command = command("--scope=mine");

        assertThatThrownBy(command::doCall)
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.USAGE))
                .hasMessage("--scope must be one of all, user, camel, spring, quarkus, was: mine");
    }

    @Test
    void theFilterAndOptionsAreSentToTheIntegration() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("--filter=order*", "--nulls=false", "--internal"), someBeans())).isZero();

        JsonObject action = readActionFile(TEST_PID);
        assertThat(action.getString("filter")).isEqualTo("order*");
        assertThat(action.getBoolean("nulls")).isFalse();
        assertThat(action.getBoolean("internal")).isTrue();
    }

    @Test
    void dslShowsTheConfigurationOfEachProperty() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        // without --nulls the configuration has only the properties that are configured
        JsonObject bean = bean("orders", "com.foo.OrderService",
                property("retries", "java.lang.Integer", 3), property("url", "java.lang.String", "http://x"));
        bean.put("modelProperties", new JsonArray(List.of(property("url", null, "{{orders.url}}"))));
        JsonObject answer = new JsonObject();
        answer.put("bean-models", new JsonObject(Map.of("orders", bean)));

        assertThat(callWithResponse(command("--dsl", "--nulls=false"), answer)).isZero();

        assertThat(printer.getOutput()).contains("BEAN: orders (com.foo.OrderService):", "CONFIGURATION");
        assertThat(lineOf("retries")).contains("3").doesNotContain("{{orders.url}}");
        assertThat(lineOf("url")).contains("{{orders.url}}", "http://x");
    }

    @Test
    void jsonOfTheBeans() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        JsonObject bean = bean("orders", "com.foo.OrderService", property("url", "java.lang.String", "http://x"));
        bean.put("modelProperties", new JsonArray(List.of(property("url", null, "{{orders.url}}"))));
        JsonObject answer = new JsonObject();
        answer.put("bean-models", new JsonObject(Map.of("orders", bean)));

        assertThat(callWithResponse(command("--dsl", "--json"), answer)).isZero();

        JsonArray beans = Jsoner.deserialize(printer.getOutput(), (JsonArray) null);
        JsonObject orders = beans.getMap(0);
        assertThat(orders.getString("name")).isEqualTo("orders");
        assertThat(orders.getString("type")).isEqualTo("com.foo.OrderService");
        List<JsonObject> properties = orders.getCollection("properties");
        assertThat(properties.get(0).getString("name")).isEqualTo("url");
        assertThat(properties.get(0).getString("value")).isEqualTo("http://x");
        assertThat(properties.get(0).getString("configValue")).isEqualTo("{{orders.url}}");
    }

    @Test
    void anAnswerWithoutBeansIsAFailure() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        CamelBeanDump command = command();

        assertThatThrownBy(() -> callWithResponse(command, new JsonObject()))
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.FAILURE))
                .hasMessage("Bean data not available from the running integration");
    }

    @Test
    void testReturnsErrorWhenNameDoesNotMatch() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        CamelBeanDump command = new CamelBeanDump(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, "doesNotExist");

        assertThatThrownBy(() -> callWithSingleProcess(command))
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.NOT_FOUND))
                .hasMessage("No running Camel integration matches: doesNotExist");
        assertThat(printer.getOutput()).isEmpty();
    }

    private CamelBeanDump command(String... args) {
        CamelBeanDump command = new CamelBeanDump(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, Stream.concat(Stream.of("myApp"), Arrays.stream(args)).toArray(String[]::new));
        return command;
    }

    private String lineOf(String text) {
        return Arrays.stream(printer.getOutput().split(System.lineSeparator()))
                .filter(l -> l.strip().startsWith(text + " ")).findFirst().orElseThrow();
    }

    // the beans of a user, of Camel, of the JDK, of Spring and of Quarkus
    private static JsonObject someBeans() {
        return beans(
                bean("orders", "com.foo.OrderService"),
                bean("camel-tracer", "org.apache.camel.impl.engine.DefaultTracer"),
                bean("executor", "java.util.concurrent.ThreadPoolExecutor"),
                bean("spring", "org.springframework.core.env.Environment"),
                bean("quarkus", "io.quarkus.runtime.Application"));
    }

    // as the bean dev console answers
    private static JsonObject beans(JsonObject... beans) {
        JsonObject map = new JsonObject();
        for (JsonObject bean : beans) {
            map.put(bean.getString("name"), bean);
        }
        JsonObject answer = new JsonObject();
        answer.put("beans", map);
        return answer;
    }

    private static JsonObject bean(String name, String type, JsonObject... properties) {
        JsonObject bean = new JsonObject();
        bean.put("name", name);
        bean.put("type", type);
        if (properties.length > 0) {
            bean.put("properties", new JsonArray(Arrays.asList(properties)));
        }
        return bean;
    }

    private static JsonObject property(String name, String type, Object value) {
        JsonObject property = new JsonObject();
        property.put("name", name);
        if (type != null) {
            property.put("type", type);
        }
        property.put("value", value);
        return property;
    }
}
