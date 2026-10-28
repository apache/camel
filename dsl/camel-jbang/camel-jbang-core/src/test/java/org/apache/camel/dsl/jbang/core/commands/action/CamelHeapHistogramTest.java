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

import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * camel cmd heap-histogram: the classes the integration answers, filtered, sorted and cut to the top.
 */
@ExtendWith(MockitoExtension.class)
class CamelHeapHistogramTest extends ActionCommandTestSupport {

    @Test
    void largestFirst() throws Exception {
        assertThat(classNames()).containsExactly("byte[]", "java.lang.String", "org.apache.camel.Exchange");
    }

    @Test
    void sortByInstancesOrName() throws Exception {
        assertThat(classNames("--sort=instances")).containsExactly("java.lang.String", "byte[]",
                "org.apache.camel.Exchange");
        assertThat(classNames("--sort=className")).containsExactly("byte[]", "java.lang.String",
                "org.apache.camel.Exchange");
        // smallest first
        assertThat(classNames("--sort=-bytes")).containsExactly("org.apache.camel.Exchange", "java.lang.String",
                "byte[]");
    }

    @Test
    void filterAndTop() throws Exception {
        assertThat(classNames("--filter=camel")).containsExactly("org.apache.camel.Exchange");
        assertThat(classNames("--filter=non-jdk")).containsExactly("byte[]", "org.apache.camel.Exchange");
        assertThat(classNames("--top=1")).containsExactly("byte[]");
    }

    @Test
    void sizes() {
        assertThat(CamelHeapHistogram.formatBytes(512)).isEqualTo("512 B");
        assertThat(CamelHeapHistogram.formatBytes(1536)).isEqualTo("1.5 KB");
        assertThat(CamelHeapHistogram.formatBytes(3L * 1024 * 1024 * 1024)).isEqualTo("3.0 GB");
        assertThat(CamelHeapHistogram.formatNumber(1234567)).isEqualTo("1,234,567");
    }

    private List<String> classNames(String... args) throws Exception {
        writeStatusFile(TEST_PID, "orders");
        printer = new StringPrinter();
        CamelHeapHistogram command = new CamelHeapHistogram(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, args);
        command.name = "orders";
        command.jsonOutput = true;

        assertThat(callWithResponse(command, histogram())).isZero();

        JsonObject answer = Jsoner.deserialize(printer.getStdout(), (JsonObject) null);
        assertThat(answer.getLong("pid")).isEqualTo(TEST_PID);
        JsonArray classes = answer.getCollection("classes");
        return classes.stream().map(c -> ((JsonObject) c).getString("className")).toList();
    }

    private static JsonObject histogram() {
        JsonObject answer = new JsonObject();
        answer.put("totalInstances", 1600L);
        answer.put("totalBytes", 90000L);
        answer.put("classes", new JsonArray(
                List.of(
                        entry(1, "byte[]", 500, 60000),
                        entry(2, "java.lang.String", 1000, 25000),
                        entry(3, "org.apache.camel.Exchange", 100, 5000))));
        return answer;
    }

    private static JsonObject entry(int num, String className, long instances, long bytes) {
        JsonObject entry = new JsonObject();
        entry.put("num", num);
        entry.put("className", className);
        entry.put("instances", instances);
        entry.put("bytes", bytes);
        return entry;
    }
}
