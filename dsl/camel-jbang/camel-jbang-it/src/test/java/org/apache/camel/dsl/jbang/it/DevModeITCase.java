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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.it.support.JBangTestSupport;
import org.apache.camel.dsl.jbang.it.support.JiraIssue;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

public class DevModeITCase extends JBangTestSupport {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    public void runInDevModeTest() throws IOException {
        initFileInDataFolder("cheese.xml");
        executeBackground(String.format("run %s/cheese.xml --dev", mountPoint()));
        checkLogContains("cheese", DEFAULT_MSG);
        final Path routeFile = Path.of(getDataFolder(), "cheese.xml");
        makeTheFileWriteable(String.format("%s/cheese.xml", mountPoint()));
        Files.write(routeFile,
                Files.readAllLines(routeFile).stream()
                        .map(line -> line.replace("${routeId}", "custom"))
                        .collect(Collectors.toList()));
        checkLogContains("cheese", "Hello Camel from custom");
    }

    @Test
    public void runDevConsoleTest() {
        initFileInDataFolder("hello.java");
        executeBackground(String.format("run %s/hello.java --console", mountPoint()));
        checkLogContains("Vert.x HttpServer started on 0.0.0.0:8080");
        checkLogContains(DEFAULT_MSG);
        final HttpResponse<String> res = getDevRequest("");
        Assertions.assertThat(res.statusCode()).as("dev console status code").isEqualTo(200);
        final HttpResponse<String> health = getDevRequest("/health");
        Assertions.assertThat(health.statusCode()).as("dev console health status code").isEqualTo(200);
        Assertions.assertThat(health.body()).as("health status is UP").contains("Health Check Status: UP");
        final HttpResponse<String> top = getDevRequest("/top");
        Assertions.assertThat(top.statusCode()).as("dev console top status code").isEqualTo(200);
        Assertions.assertThat(top.body()).as("top contains route").contains("Route Id: route1");
        //check all endpoints status code
        SoftAssertions.assertSoftly(softly -> res.body().lines()
                .map(line -> "/" + line.split(":")[0])
                .map(this::getDevRequest)
                .forEach(response -> softly.assertThat(response.statusCode())
                        .as(response.uri() + " response code")
                        .isEqualTo(200)));
    }

    @Test
    @JiraIssue("CAMEL-20939")
    public void runUsingProfileTest() throws IOException {
        copyResourceInDataFolder(TestResources.HELLO_NAME);
        copyResourceInDataFolder(TestResources.TEST_PROFILE_PROP);
        containerService.copyFileInternally(mountPoint() + "/" + TestResources.TEST_PROFILE_PROP.getName(),
                DEFAULT_ROUTE_FOLDER);
        executeBackground(String.format("run %s/%s", mountPoint(), TestResources.HELLO_NAME.getName()));
        checkLogContains("Hello Camel from John");
        execute("stop helloName");
        executeBackground(String.format("run %s/%s --profile=test", mountPoint(), TestResources.HELLO_NAME.getName()));
        checkLogContains("Hello Camel from Jenna");
    }

    @Test
    public void testDeleteFileViaHttp() throws IOException {
        copyResourceInDataFolder(TestResources.DIR_ROUTE);
        executeBackground(String.format("run --dev --console --source-dir=%s", mountPoint()));
        checkLogContains("Hello world!");
        execInHost(String.format(
                "curl -X DELETE http://localhost:%s/q/upload/FromDirectoryRoute.java",
                containerService.getDevConsolePort()));
        checkLogContains("FromDirectoryRoute.java does not exist");
    }

    @Test
    public void testUploadFileViaHttp() throws IOException {
        copyResourceInDataFolder(TestResources.DIR_ROUTE);
        Files.createDirectory(Path.of(getDataFolder() + "/source-dir"));
        Files.copy(Path.of(getDataFolder() + "/FromDirectoryRoute.java"),
                Path.of(getDataFolder() + "/source-dir/FromDirectoryRoute.java"));
        executeBackground(String.format("run --dev --console --source-dir=%s/source-dir", mountPoint()));
        checkLogContains("Hello world!");
        makeTheFileWriteable(String.format("%s/FromDirectoryRoute.java", mountPoint()));
        Path routeFile = Path.of(getDataFolder(), "FromDirectoryRoute.java");
        Files.write(routeFile,
                Files.readAllLines(routeFile).stream()
                        .map(line -> line.replace("Hello world!", "I have been modified!"))
                        .collect(Collectors.toList()));
        checkLogDoesNotContain("I have been modified!");
        execInHost(String.format(
                "curl -X PUT http://localhost:%s/q/upload/FromDirectoryRoute.java --data-binary \"@%s/FromDirectoryRoute.java\"",
                containerService.getDevConsolePort(), getDataFolder()));
        checkLogContains("I have been modified!");
    }

    private HttpResponse<String> getDevRequest(final String ctxUrl) {
        try {
            return httpClient.send(HttpRequest
                    .newBuilder(
                            new URI(String.format("http://localhost:%s/q/dev%s", containerService.getDevConsolePort(), ctxUrl)))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException | URISyntaxException e) {
            Assertions.fail("unable to call dev console");
            throw new RuntimeException(e);
        }
    }
}
