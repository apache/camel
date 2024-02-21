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
package org.apache.camel.dsl.jbang.it;

import org.apache.camel.dsl.jbang.it.support.InVersion;
import org.apache.camel.dsl.jbang.it.support.JBangTestSupport;
import org.apache.camel.test.infra.cli.common.CliProperties;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

public class VersionCommandITCase extends JBangTestSupport {

    @Test
    @DisabledIfSystemProperty(named = CliProperties.FORCE_RUN_VERSION, matches = ".+")
    public void versionCommandTest() {
        Assertions.assertThat(execute("version").trim())
                .contains("Camel JBang version: " + version());
        execute("version set 3.20.2");
        Assertions.assertThat(execute("version").trim())
                .contains("Camel JBang version: " + version())
                .contains("User configuration:")
                .contains("camel-version = 3.20.2");
    }

    @Test
    @EnabledIfSystemProperty(named = CliProperties.FORCE_RUN_VERSION, matches = ".+")
    public void versionCommandTestWithCustomVersion() {
        Assertions.assertThat(execute("version").trim())
                .contains("User configuration:")
                .contains("camel-version = " + version());
    }

    @Test
    public void executeAfterVersionSetTest() {
        execute("version set 3.20.2");
        initAndRunInBackground("foo.java");
        checkLogContains("Apache Camel (JBang) 3.20.2 is starting");
    }

    @Test
    public void versionListCommandTest() {
        Assertions.assertThatNoException()
                .isThrownBy(() -> execute("version list"));
    }

    @Test
    public void versionListWithSBCommandTest() {
        Assertions.assertThatNoException()
                .isThrownBy(() -> execute("version list --runtime=spring-boot"));
    }

    @Test
    public void runVersionListWithQuarkusCommandTest() {
        Assertions.assertThatNoException()
                .isThrownBy(() -> execute("version list --runtime=quarkus"));
    }

    @Test
    @InVersion(from = "4.00.00")
    public void versionListFromVersionTest() {
        Assertions.assertThat(execute("version list --from-version=3.20.1"))
                .doesNotContain("3.19.0");
    }

    @Test
    @InVersion(to = "3.21.00")
    public void versionListMinimumVersionTest() {
        Assertions.assertThat(execute("version list --minimum-version=3.20.1"))
                .doesNotContain("3.19.0");
    }
}
