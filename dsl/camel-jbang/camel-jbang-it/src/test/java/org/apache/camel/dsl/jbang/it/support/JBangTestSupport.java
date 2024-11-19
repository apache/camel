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
package org.apache.camel.dsl.jbang.it.support;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.camel.test.infra.cli.common.CliProperties;
import org.apache.camel.test.infra.cli.services.CliService;
import org.apache.camel.test.infra.cli.services.CliServiceFactory;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;

public abstract class JBangTestSupport {

    protected Logger logger = LoggerFactory.getLogger(getClass());

    @RegisterExtension
    protected static CliService containerService = CliServiceFactory.createService();

    private static final String DATA_FOLDER = System.getProperty(CliProperties.DATA_FOLDER);

    protected static final int ASSERTION_WAIT_SECONDS
            = Integer.parseInt(System.getProperty("jbang.it.assert.wait.timeout", "60"));

    protected static final String DEFAULT_ROUTE_FOLDER = "/home/jbang";

    public static final String DEFAULT_MSG = "Hello Camel from";

    protected String containerDataFolder;

    @BeforeEach
    protected void beforeEach(TestInfo testInfo) throws IOException {
        Assertions.assertThat(DATA_FOLDER).as("%s need to be set", CliProperties.DATA_FOLDER).isNotBlank();
        containerDataFolder = Files.createDirectory(Paths.get(DATA_FOLDER, containerService.id())).toAbsolutePath().toString();
        Files.setPosixFilePermissions(Paths.get(containerDataFolder), EnumSet.allOf(PosixFilePermission.class));
        logger.debug("running {}#{} using data folder {}", getClass().getName(), testInfo.getDisplayName(), getDataFolder());
    }

    @AfterEach
    protected void afterEach(TestInfo testInfo) throws IOException {
        logger.debug("ending {}#{} using data folder {}", getClass().getName(), testInfo.getDisplayName(), getDataFolder());
        assertNoErrors();
        logger.debug("clean up data folder");
        FileUtils.deleteQuietly(new File(containerDataFolder));
    }

    protected void stopAllRoutes() {
        execute("stop --all");
    }

    protected enum TestResources {
        ROUTE2("route2.yaml", "/jbang/it/route2.yaml"),
        TEST_PROFILE_PROP("application-test.properties", "/jbang/it/application-test.properties"),
        HELLO_NAME("helloName.xml", "/jbang/it/helloName.xml"),
        JOKE("joke.yaml", "/jbang/it/joke.yaml"),
        MQQT_CONSUMER("mqttConsumer.yaml", "/jbang/it/mqttConsumer.yaml"),
        BUILD_GRADLE("build.gradle", "/jbang/it/maven-gradle/build.gradle"),
        DIR_ROUTE("FromDirectoryRoute.java", "/jbang/it/from-source-dir/FromDirectoryRoute.java"),
        SERVER_ROUTE("server.yaml", "/jbang/it/server.yaml"),
        CIRCUIT_BREAKER("CircuitBreakerRoute.java", "/jbang/it/CircuitBreakerRoute.java"),
        SRC_MAPPING_DATA("data.json", "/jbang/it/data-mapping/src/data.json"),
        SRC_MAPPING_TEMPLATE("transform.yaml", "/jbang/it/data-mapping/src/transform.yaml"),
        COMP_MAPPING_DATA("data.xml", "/jbang/it/data-mapping/components/data.xml"),
        COMP_MAPPING_TEMPLATE("transform.xml", "/jbang/it/data-mapping/components/transform.xml"),
        FORMATS_MAPPING_DATA("data.csv", "/jbang/it/data-mapping/data-formats/data.csv");

        private String name;
        private String resPath;

        TestResources(String name, String resPath) {
            this.name = name;
            this.resPath = resPath;
        }

        public InputStream openStream() {
            return TestResources.class.getResourceAsStream(resPath);
        }

        public String getName() {
            return name;
        }
    }

    protected String execInContainer(final String command) {
        return containerService.executeGenericCommand(command);
    }

    protected String execute(final String command) {
        return containerService.execute(command);
    }

    protected String executeBackground(final String command) {
        return containerService.executeBackground(command);
    }

    protected String mountPoint() {
        return String.format("%s/%s", containerService.getMountPoint(), containerService.id());
    }

    protected void initAndRunInBackground(String file) {
        execute("init " + file);
        executeBackground("run " + file);
    }

    protected void initFileInDataFolder(String file) {
        execute(String.format("init %s --directory=%s", file, mountPoint()));
        assertFileInDataFolderExists(file);
    }

    protected void assertFileInDataFolderExists(String file) {
        final Path toVerify = Path.of(containerDataFolder, file);
        Assertions.assertThat(toVerify)
                .as("file " + toVerify + " exists")
                .exists()
                .isReadable();
    }

    protected void checkCommandOutputs(String command, String contains) {
        Assertions.assertThat(execute(command))
                .as("command camel" + command + "should output" + contains)
                .contains(contains);
    }

    protected void checkCommandOutputsPattern(String command, String contains) {
        Assertions.assertThat(execute(command))
                .as("command camel" + command + "should output pattern" + contains)
                .containsPattern(contains);
    }

    protected void checkCommandDoesNotOutput(String command, String contains) {
        Assertions.assertThat(execute(command))
                .as("command camel" + command + "should not output" + contains)
                .doesNotContain(contains);
    }

    protected void assertFileInDataFolderDoesNotExist(String file) {
        final Path toVerify = Path.of(containerDataFolder, file);
        Assertions.assertThat(toVerify)
                .as("file " + toVerify + " exists")
                .doesNotExist();
    }

    protected void checkLogContainsAllOf(String... contains) {
        checkLogContainsAllOf(ASSERTION_WAIT_SECONDS, contains);
    }

    protected void checkLogContainsAllOf(int waitForSeconds, String... contains) {
        Assertions.assertThatNoException().isThrownBy(() -> Awaitility.await()
                .atMost(waitForSeconds, TimeUnit.SECONDS)
                .untilAsserted(() -> Assertions.assertThat(getLogs())
                        .contains(contains)));
    }

    protected void checkLogContains(String contains) {
        checkLogContains(contains, ASSERTION_WAIT_SECONDS);
    }

    protected void checkLogContains(String route, String contains) {
        checkLogContains(route, contains, ASSERTION_WAIT_SECONDS);
    }

    protected void checkLogContains(String contains, int waitForSeconds) {
        checkLogContains(null, contains, waitForSeconds);
    }

    protected void checkLogContains(String route, String contains, int waitForSeconds) {
        Assertions.assertThatNoException().isThrownBy(() -> Awaitility.await()
                .atMost(waitForSeconds, TimeUnit.SECONDS)
                .untilAsserted(() -> Assertions.assertThat(getLogs(route))
                        .as("log contains " + contains)
                        .contains(contains)));
    }

    protected void checkLogDoesNotContain(String route, String contains, int waitForSeconds) {
        Assertions.assertThatNoException().isThrownBy(() -> Awaitility.await()
                .atMost(waitForSeconds, TimeUnit.SECONDS)
                .untilAsserted(() -> Assertions.assertThat(getLogs(route))
                        .as("log does not contain " + contains)
                        .doesNotContain(contains)));
    }

    protected void checkLogDoesNotContain(String contains, int waitForSeconds) {
        checkLogDoesNotContain(null, contains, waitForSeconds);
    }

    protected void checkLogDoesNotContain(String route, String contains) {
        checkLogDoesNotContain(route, contains, ASSERTION_WAIT_SECONDS);
    }

    protected void checkLogDoesNotContain(String contains) {
        checkLogDoesNotContain(contains, ASSERTION_WAIT_SECONDS);
    }

    protected void checkLogContainsPattern(String contains) {
        checkLogContainsPattern(contains, ASSERTION_WAIT_SECONDS);
    }

    protected void checkLogContainsPattern(String route, String contains) {
        checkLogContainsPattern(route, contains, ASSERTION_WAIT_SECONDS);
    }

    protected void checkLogContainsPattern(String contains, int waitForSeconds) {
        checkLogContainsPattern(null, contains, waitForSeconds);
    }

    protected void checkLogContainsPattern(String route, String contains, int waitForSeconds) {
        Assertions.assertThatNoException().isThrownBy(() -> Awaitility.await()
                .atMost(waitForSeconds, TimeUnit.SECONDS)
                .untilAsserted(() -> Assertions.assertThat(getLogs(route))
                        .as("log contains pattern " + contains)
                        .containsPattern(contains)));
    }

    protected String getLogs() {
        return getLogs(null);
    }

    protected String getLogs(String route) {
        return execute(Optional.ofNullable(route)
                .map(r -> String.format("log %s --follow=false", r))
                .orElseGet(() -> "log --follow=false"));
    }

    protected void assertNoErrors() {
        Assertions.assertThat(getLogs()).as("log contains ERROR").doesNotContain("ERROR");
    }

    protected void copyInternallyToDataFolder(String sourcePath) {
        containerService.copyFileInternally(sourcePath, mountPoint());
    }

    protected void copyResourceInDataFolder(final TestResources resource) throws IOException {
        try (InputStream is = resource.openStream()) {
            Files.copy(is, Path.of(containerDataFolder, resource.getName()), StandardCopyOption.REPLACE_EXISTING);
        }
        assertFileInDataFolderExists(resource.getName());
    }

    protected void newFileInDataFolder(String fileName, String content) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
            Files.copy(in, Path.of(containerDataFolder, fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        assertFileInDataFolderExists(fileName);
    }

    protected String getDataFolder() {
        return containerDataFolder;
    }

    public String version() {
        return containerService.version();
    }

    protected String execInHost(String command) {
        try {
            ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", command);
            builder.redirectErrorStream(true);
            final Process process = builder.start();
            Awaitility.await("process is running")
                    .atMost(Duration.ofMinutes(2))
                    .pollInterval(Duration.ofSeconds(1))
                    .until(() -> !process.isAlive());
            if (process.exitValue() != 0) {
                logger.error(String.valueOf(process.getErrorStream()));
                logger.info(String.valueOf(process.getOutputStream()));
            }
            return new String(process.getInputStream().readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected String makeTheFileWriteable(String containerPath) {
        return containerService.executeGenericCommand("chmod 777 " + containerPath);
    }

    protected String downloadNewFileInDataFolder(String downloadUrl) {
        try {
            return this.downloadNewFileInDataFolder(new URL(downloadUrl), null);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    protected String downloadNewFileInDataFolder(URL downloadUrl, String fileName) {
        final String fName = fileName == null ? Paths.get(downloadUrl.getPath().toString()).getFileName().toString() : fileName;

        final StringWriter sw = new StringWriter();
        try (ReadableByteChannel channel = Channels.newChannel(downloadUrl.openStream());
             Reader reader = Channels.newReader(channel, Charset.defaultCharset())) {
            reader.transferTo(sw);
            sw.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.newFileInDataFolder(fName, sw.toString());
        return fName;
    }

    protected String downloadFile(String downloadUrl) {
        String fileName = this.downloadNewFileInDataFolder(downloadUrl);
        containerService.copyFileInternally(mountPoint() + "/" + fileName, DEFAULT_ROUTE_FOLDER);
        return fileName;
    }

    protected void generateProperties(Map<String, String> properties) {
        this.generateProperties("application.properties", properties, false);
    }

    protected void generateProperties(String fileName, Map<String, String> properties, boolean inDataFolder) {
        final Properties prop = new Properties();
        prop.putAll(properties);
        final StringWriter contentWriter = new StringWriter();
        try {
            prop.store(contentWriter, "");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.newFileInDataFolder(fileName, contentWriter.toString());
        if (!inDataFolder) {
            containerService.executeGenericCommand(
                    String.format("mv %s/%s %s/%s", mountPoint(), fileName, DEFAULT_ROUTE_FOLDER, fileName));
        }
    }

    protected void assertFileInContainerExists(String fileAbsolutePath) {
        String fileName = Path.of(fileAbsolutePath).getFileName().toFile().getName();
        Assertions.assertThat(containerService.listDirectory(Path.of(fileAbsolutePath).getParent().toAbsolutePath().toString())
                .anyMatch(child -> fileName.equals(child)))
                .as("check if file " + fileAbsolutePath + " exists")
                .isTrue();
    }

}
