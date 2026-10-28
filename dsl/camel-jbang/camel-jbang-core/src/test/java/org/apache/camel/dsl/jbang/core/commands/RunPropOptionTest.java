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

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25437: --prop takes its value after a space too, as every other option does, instead of losing it to the files
 * to run.
 */
class RunPropOptionTest {

    @Test
    void aPropertyAfterASpace() {
        Run run = new Run(new CamelJBangMain());
        new CommandLine(run).parseArgs("route.camel.yaml", "--prop", "greeting=Hi");

        assertThat(run.property).containsExactly("greeting=Hi");
        assertThat(run.files).containsExactly("route.camel.yaml");
    }

    @Test
    void propertiesInEveryForm() {
        Run run = new Run(new CamelJBangMain());
        new CommandLine(run).parseArgs("--prop=a=1", "--prop", "b=2", "--property", "c=3", "route.camel.yaml");

        assertThat(run.property).containsExactly("a=1", "b=2", "c=3");
        assertThat(run.files).containsExactly("route.camel.yaml");
    }

    @Test
    void aScriptPropertyAfterASpace() {
        Script script = new Script(new CamelJBangMain());
        new CommandLine(script).parseArgs("--prop", "greeting=Hi", "route.camel.yaml");

        assertThat(script.property).containsExactly("greeting=Hi");
        assertThat(script.file).isEqualTo("route.camel.yaml");
    }
}
