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
package org.apache.camel.dsl.jbang.core.commands.infra;

import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTestSupport;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The --property options of camel infra run, which a service reads as system properties.
 */
public class InfraRunPropertyTest extends CamelCommandBaseTestSupport {

    @Test
    public void propertiesAreSetAndReported() {
        InfraRun infraRun = infraRun("ollama.model=qwen2.5:0.5b", "ollama.container.enable.gpu=enabled");
        try {
            assertThat(infraRun.setServiceProperties())
                    .containsExactly("ollama.model", "ollama.container.enable.gpu");
            assertThat(System.getProperty("ollama.model")).isEqualTo("qwen2.5:0.5b");
            assertThat(System.getProperty("ollama.container.enable.gpu")).isEqualTo("enabled");
        } finally {
            System.clearProperty("ollama.model");
            System.clearProperty("ollama.container.enable.gpu");
        }
    }

    @Test
    public void onlyTheFirstSeparatorSplitsTheProperty() {
        InfraRun infraRun = infraRun("ollama.model=library/qwen2.5:0.5b=latest");
        try {
            infraRun.setServiceProperties();

            assertThat(System.getProperty("ollama.model")).isEqualTo("library/qwen2.5:0.5b=latest");
        } finally {
            System.clearProperty("ollama.model");
        }
    }

    @Test
    public void aPropertyWithoutANameOrAValueIsRejected() {
        assertThatThrownBy(() -> infraRun("ollama.model").setServiceProperties())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key=value");
        assertThatThrownBy(() -> infraRun("=qwen2.5:0.5b").setServiceProperties())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key=value");
    }

    private InfraRun infraRun(String... properties) {
        InfraRun infraRun = new InfraRun(new CamelJBangMain().withPrinter(printer));
        infraRun.properties = List.of(properties);
        return infraRun;
    }
}
