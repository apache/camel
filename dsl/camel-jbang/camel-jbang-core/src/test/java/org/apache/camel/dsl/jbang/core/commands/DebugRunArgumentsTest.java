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

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The arguments of the camel run that camel debug starts: before, every argument that started with the name of a field
 * of Debug was removed, and the value of an option given with a space was left as a file (CAMEL-26930).
 */
class DebugRunArgumentsTest {

    @Test
    void theRunOptionsStay() {
        // the field source of Debug removed --source-dir=...
        assertThat(Debug.withoutDebugOnlyOptions(List.of("run", "--source-dir=src", "--source", "route.yaml")))
                .containsExactly("run", "--source-dir=src", "route.yaml");
    }

    @Test
    void theDebugOptionsGoWithTheirValues() {
        assertThat(Debug.withoutDebugOnlyOptions(List.of("run", "--breakpoint", "tick", "--log-lines=5", "route.yaml",
                "--show-headers", "--port=8081")))
                .containsExactly("run", "route.yaml", "--port=8081");
        assertThat(Debug.withoutDebugOnlyOptions(List.of("run", "--breakpoint=tick", "route.yaml")))
                .containsExactly("run", "route.yaml");
    }

    @Test
    void aFlagDoesNotTakeTheNextArgument() {
        // --pretty has no value: route.yaml after it is the file of the run
        assertThat(Debug.withoutDebugOnlyOptions(List.of("run", "--pretty", "route.yaml")))
                .containsExactly("run", "route.yaml");
    }
}
