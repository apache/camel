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

package org.apache.camel.dsl.jbang.core.commands;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DependencyListTest extends CamelCommandBaseTest {

    @Test
    public void calculateDependencies() throws Exception {
        UserConfigHelper.createUserConfig("");

        DependencyList command = createCommand();
        Path javaFile = Path.of("src/test/resources/Sample.java");
        Assertions.assertTrue(javaFile.toFile().exists(), "The src/test/resources/Sample.java was not found.");
        command.filePaths = new Path[] { javaFile };
        command.output = "gav";
        command.doCall();

        String[] deps = new String[] {
                "org.apache.camel:camel-aws2-s3", "org.apache.camel:camel-caffeine", "org.apache.camel:camel-dropbox",
                "org.apache.camel:camel-jacksonxml", "org.apache.camel:camel-kafka", "org.apache.camel:camel-kamelet",
                "org.apache.camel:camel-mongodb", "org.apache.camel:camel-rest", "org.apache.camel:camel-telegram",
                "org.apache.camel:camel-zipfile" };
        List<String> lines = printer.getLines();
        int depsFound = 0;
        for (int i = 0; i < lines.size(); i++) {
            for (String dep : deps) {
                if (lines.get(i).contains(dep)) {
                    depsFound++;
                    break;
                }
            }
        }
        Assertions.assertEquals(deps.length, depsFound, "The expected dependencies were not calculated correctly.");
    }

    @Test
    public void calculateDependenciesOutputJBang() throws Exception {
        UserConfigHelper.createUserConfig("");

        DependencyList command = createCommand();
        Path javaFile = Path.of("src/test/resources/sample.yaml");
        Assertions.assertTrue(javaFile.toFile().exists(), "The src/test/resources/sample.yaml was not found.");
        command.filePaths = new Path[] { javaFile };
        command.output = "jbang";
        command.doCall();

        String[] deps = new String[] {
                "//DEPS org.apache.camel:camel-bom:",
                "//DEPS org.apache.camel:camel-caffeine",
                "//DEPS org.apache.camel:camel-http",
                "//DEPS org.apache.camel:camel-jackson",
                "//DEPS org.apache.camel:camel-jsonpath",
                "//DEPS org.apache.camel:camel-kamelet",
                "//DEPS org.apache.camel:camel-log",
                "//DEPS org.apache.camel:camel-rest",
                "//DEPS org.apache.camel:camel-timer",
                "//DEPS org.apache.camel:camel-yaml-dsl" };

        List<String> lines = printer.getLines();
        int depsFound = 0;
        for (int i = 0; i < lines.size(); i++) {
            for (String dep : deps) {
                if (lines.get(i).contains(dep)) {
                    depsFound++;
                    break;
                }
            }
        }
        Assertions.assertEquals(deps.length, depsFound,
                "The expected dependencies were not calculated correctly when output=jbang.");
    }

    @Test
    public void calculateDependenciesOutputMaven() throws Exception {
        UserConfigHelper.createUserConfig("");

        DependencyList command = createCommand();
        Path javaFile = Path.of("src/test/resources/sample.yaml");
        Assertions.assertTrue(javaFile.toFile().exists(), "The src/test/resources/sample.yaml was not found.");
        command.filePaths = new Path[] { javaFile };
        command.output = "maven";
        command.doCall();

        String[] deps = new String[] {
                "camel-caffeine",
                "camel-http",
                "camel-jackson",
                "camel-jsonpath",
                "camel-kamelet",
                "camel-log",
                "camel-rest",
                "camel-timer",
                "camel-yaml-dsl" };

        List<String> lines = printer.getLines();
        int depsFound = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("<artifactId>")) {
                for (String dep : deps) {
                    if (lines.get(i).contains(dep)) {
                        depsFound++;
                        break;
                    }
                }
            }
        }
        Assertions.assertEquals(deps.length, depsFound,
                "The expected dependencies were not calculated correctly when output=maven.");
    }

    private DependencyList createCommand() {
        return new DependencyList(new CamelJBangMain().withPrinter(printer));
    }
}
