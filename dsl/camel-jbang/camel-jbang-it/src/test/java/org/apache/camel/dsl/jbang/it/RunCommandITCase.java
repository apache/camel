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

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Date;

import org.apache.camel.dsl.jbang.it.support.JBangTestSupport;
import org.apache.camel.dsl.jbang.it.support.JiraIssue;
import org.apache.camel.test.infra.cli.common.CliProperties;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.Assume;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.condition.OS.LINUX;

public class RunCommandITCase extends JBangTestSupport {

    @Test
    public void initClearDirectoryTest() {
        initFileInDataFolder("willbedeleted.yaml");
        initFileInDataFolder("keepthisone.yaml");
        assertFileInDataFolderDoesNotExist("willbedeleted.yaml");
        assertFileInDataFolderExists("keepthisone.yaml");
    }

    @Test
    public void initAndRunTest() {
        initFileInDataFolder("cheese.xml");
        executeBackground(String.format("run %s/cheese.xml", mountPoint()));
        checkLogContains("cheese", DEFAULT_MSG);
    }

    @Test
    public void runRoutesFromMultipleFilesTest() throws IOException {
        initFileInDataFolder("route1.yaml");
        copyResourceInDataFolder(TestResources.ROUTE2);
        executeBackground(String.format("run %s/route1.yaml %s/%s",
                mountPoint(), mountPoint(), TestResources.ROUTE2.getName()));

        checkLogContains(DEFAULT_MSG);
        checkLogContains("Hello Camel from custom integration");
    }

    @Test
    public void runRoutesFromMultipleFilesUsingWildcardTest() {
        execute("init one.yaml --directory=/tmp/one");
        execute("init two.xml --directory=/tmp/two");
        copyInternallyToDataFolder("/tmp/one/one.yaml");
        copyInternallyToDataFolder("/tmp/two/two.xml");
        executeBackground(String.format("run %s/* ", mountPoint()));
        checkLogContains(DEFAULT_MSG);
        checkLogContains(DEFAULT_MSG);
    }

    @Test
    public void runRouteFromInputParameterTest() {
        executeBackground("run --code='from(\"kamelet:beer-source\").to(\"log:beer\")'");
        checkLogContains("Started route1 (kamelet://beer-source)");
        checkLogContains("Started beer-source-1 (timer://beer)");
    }

    @Test
    public void runRouteFromGithubTest() {
        executeBackground("run github:apache:camel-kamelets-examples:jbang/hello-java/Hey.java");
        checkLogContains("Hello from Camel");
    }

    @Test
    @JiraIssue("CAMEL-20351")
    public void runRouteFromGithubUsingHttpsTest() {
        executeBackground("run https://github.com/apache/camel-kamelets-examples/tree/main/jbang/hello-java");
        checkLogContains("Hello from Camel");
    }

    @Test
    @JiraIssue("CAMEL-20351")
    public void runRouteFromGithubUsingWildcardTest() {
        executeBackground("run https://github.com/apache/camel-kamelets-examples/tree/main/jbang/languages/*.groovy");
        checkLogContains("Hello Camel K from groovy");
        execute("stop simple");
        executeBackground("run https://github.com/apache/camel-kamelets-examples/tree/main/jbang/languages/rou*");
        checkLogContains("Hello Camel K from kotlin");
        checkLogContains("HELLO YAML !!!");
    }

    @Test
    @JiraIssue("CAMEL-20351")
    public void runRouteFromGistTest() {
        executeBackground("run https://gist.github.com/davsclaus/477ddff5cdeb1ae03619aa544ce47e92");
        checkLogContains("Hello Camel from xml");
    }

    @Test
    public void runDownloadedFromGithubTest() {
        execute("init https://github.com/apache/camel-kamelets-examples/tree/main/jbang/dependency-injection");
        Assertions.assertThat(containerService.listDirectory(DEFAULT_ROUTE_FOLDER))
                .as("default route directory")
                .contains("Echo.java", "Hello.java", "README.adoc", "application.properties");
        executeBackground("run *");
        checkLogContains("JackJack!! from Echo");
    }

    @Test
    public void runDownloadedInDirectoryFromGithubTest() {
        execute("init https://github.com/apache/camel-kamelets-examples/tree/main/jbang/dependency-injection --directory="
                + mountPoint());
        Assertions.assertThat(Paths.get(getDataFolder()).toFile().listFiles())
                .extracting("name")
                .containsExactlyInAnyOrder("Echo.java", "Hello.java", "README.adoc", "application.properties")
                .as("custom route directory");
        executeBackground(String.format("run %s/*", mountPoint()));
        checkLogContains("JackJack!! from Echo");
    }

    @ParameterizedTest
    @ValueSource(strings = { "4.0.0", "4.2.0" })
    @DisabledIfSystemProperty(named = CliProperties.FORCE_RUN_VERSION, matches = ".+", disabledReason = "Due to CAMEL-20426")
    public void runSpecificVersionTest(String version) {
        initFileInDataFolder("cheese.xml");
        final String pid = executeBackground(String.format("run %s/cheese.xml --camel-version=%s", mountPoint(), version));
        checkLogContainsPattern(String.format(" Apache Camel %s .* started", version));
        checkLogContains(DEFAULT_MSG);
        execute("stop " + pid);
    }

    @Test
    public void runCamelKCRDTest() throws IOException {
        copyResourceInDataFolder(TestResources.JOKE);
        executeBackground(String.format("run %s/%s",
                mountPoint(), TestResources.JOKE.getName()));
        checkLogContainsAllOf("timer://chuck", "log-sink");
    }

    @Test
    @EnabledOnOs(LINUX)
    @DisabledIf(value = "java.awt.GraphicsEnvironment#isHeadless")
    public void runFromClipboardTest() throws IOException {
        Assume.assumeTrue(execInHost("command -v ssh").contains("ssh"));
        Assume.assumeTrue(execInHost("command -v sshpass").contains("sshpass"));
        final String msg = "Hello World " + new Date();
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(
                        String.format("<route><from uri=\"timer:foo\"/><log message=\"%s\"/></route>", msg)), null);
        final ProcessBuilder builder = new ProcessBuilder(
                "/bin/bash", "-c",
                "sshpass -p '" + containerService.getSshPassword()
                                   + "' ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -X jbang@localhost -p "
                                   + containerService.getSshPort()
                                   + " \"camel run clipboard.xml --background && camel log\"")
                .redirectErrorStream(true);
        try (BufferedReader input = new BufferedReader(new InputStreamReader(builder.start().getInputStream()))) {
            Assertions.assertThatCode(() -> Awaitility.await()
                    .pollInterval(Duration.ofSeconds(1))
                    .atMost(Duration.ofMinutes(1))
                    .untilAsserted(() -> Assertions.assertThat(input.readLine()).isNotNull()
                            .contains("generated-clipboard.xml")
                            .contains(msg)))
                    .doesNotThrowAnyException();
        }
    }
}
