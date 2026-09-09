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
package org.apache.camel.dsl.jbang.core.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeTypeTest {

    @Test
    void fromValueAcceptsAliases() {
        assertThat(RuntimeType.fromValue("jbang")).isEqualTo(RuntimeType.jbang);
        assertThat(RuntimeType.fromValue("camel-jbang")).isEqualTo(RuntimeType.jbang);
        assertThat(RuntimeType.fromValue("JBang")).isEqualTo(RuntimeType.jbang);
        assertThat(RuntimeType.fromValue("main")).isEqualTo(RuntimeType.main);
        assertThat(RuntimeType.fromValue("camel-main")).isEqualTo(RuntimeType.main);
        assertThat(RuntimeType.fromValue("camel")).isEqualTo(RuntimeType.main);
        assertThat(RuntimeType.fromValue("spring-boot")).isEqualTo(RuntimeType.springBoot);
        assertThat(RuntimeType.fromValue("quarkus")).isEqualTo(RuntimeType.quarkus);
        assertThatThrownBy(() -> RuntimeType.fromValue("unknown")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void jbangExportsAsMain() {
        assertThat(RuntimeType.jbang.exportRuntime()).isEqualTo(RuntimeType.main);
        assertThat(RuntimeType.main.exportRuntime()).isEqualTo(RuntimeType.main);
        assertThat(RuntimeType.springBoot.exportRuntime()).isEqualTo(RuntimeType.springBoot);
        assertThat(RuntimeType.quarkus.exportRuntime()).isEqualTo(RuntimeType.quarkus);
    }

    @Test
    void runtimeNames() {
        assertThat(RuntimeType.jbang.runtime()).isEqualTo("jbang");
        assertThat(RuntimeType.main.runtime()).isEqualTo("main");
        assertThat(RuntimeType.jbang.version()).isEqualTo(RuntimeType.main.version());
    }

    @Test
    void convertersDifferOnJBang() throws Exception {
        // commands that export or inspect a project treat jbang as camel-main
        assertThat(new RuntimeTypeConverter().convert("jbang")).isEqualTo(RuntimeType.main);
        assertThat(new RuntimeTypeConverter().convert("quarkus")).isEqualTo(RuntimeType.quarkus);
        // camel run keeps the in-process jbang runtime
        assertThat(new RunRuntimeTypeConverter().convert("jbang")).isEqualTo(RuntimeType.jbang);
        assertThat(new RunRuntimeTypeConverter().convert("camel-main")).isEqualTo(RuntimeType.main);
    }
}
