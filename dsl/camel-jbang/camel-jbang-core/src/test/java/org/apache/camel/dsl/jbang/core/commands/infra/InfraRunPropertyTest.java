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
import java.util.Map;

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTestSupport;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

/**
 * The --property options of camel infra run, which a service reads as system properties.
 */
public class InfraRunPropertyTest extends CamelCommandBaseTestSupport {

    @Test
    public void propertiesArePairsInTheOrderTheyWereGiven() {
        assertThat(infraRun("ollama.model=qwen2.5:0.5b", "ollama.container.enable.gpu=enabled")
                .parseServiceProperties())
                .containsExactly(
                        entry("ollama.model", "qwen2.5:0.5b"),
                        entry("ollama.container.enable.gpu", "enabled"));
    }

    @Test
    public void onlyTheFirstSeparatorSplitsTheProperty() {
        assertThat(infraRun("ollama.model=library/qwen2.5:0.5b=latest").parseServiceProperties())
                .containsExactly(entry("ollama.model", "library/qwen2.5:0.5b=latest"));
    }

    @Test
    public void aPropertyWithoutANameOrAValueIsRejected() {
        assertThat(List.of("ollama.model", "=qwen2.5:0.5b", "ollama.model="))
                .allSatisfy(property -> assertThatThrownBy(() -> infraRun(property).parseServiceProperties())
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("key=value"));
    }

    @Test
    public void aMalformedPropertyIsRefusedBeforeTheServiceIsStartedInTheBackground() throws Exception {
        InfraRun infraRun = infraRun("ollama.model");
        infraRun.setServiceName(List.of("ollama"));
        infraRun.background = true;

        assertThat(infraRun.doCall()).isEqualTo(1);
        assertThat(printer.getOutput()).contains("key=value");
    }

    @Test
    public void theValueTheJvmWasStartedWithComesBack() {
        System.setProperty("ollama.model", "granite4:3b");
        try {
            Map<String, String> replaced = InfraRun.applyServiceProperties(Map.of("ollama.model", "qwen2.5:0.5b"));
            assertThat(System.getProperty("ollama.model")).isEqualTo("qwen2.5:0.5b");

            InfraRun.restoreProperties(replaced);

            assertThat(System.getProperty("ollama.model")).isEqualTo("granite4:3b");
        } finally {
            System.clearProperty("ollama.model");
        }
    }

    @Test
    public void aPropertyTheJvmDidNotHaveIsRemovedAgain() {
        Map<String, String> replaced = InfraRun.applyServiceProperties(Map.of("ollama.embedding.model", "all-minilm"));
        assertThat(System.getProperty("ollama.embedding.model")).isEqualTo("all-minilm");

        InfraRun.restoreProperties(replaced);

        assertThat(System.getProperty("ollama.embedding.model")).isNull();
    }

    private InfraRun infraRun(String... properties) {
        InfraRun infraRun = new InfraRun(new CamelJBangMain().withPrinter(printer));
        infraRun.serviceProperties = List.of(properties);
        return infraRun;
    }
}
