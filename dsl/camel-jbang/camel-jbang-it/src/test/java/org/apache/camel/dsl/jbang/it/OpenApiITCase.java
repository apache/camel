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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.camel.dsl.jbang.it.support.InVersion;
import org.apache.camel.dsl.jbang.it.support.JBangTestSupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

@Tag("container-only")
public class OpenApiITCase extends JBangTestSupport {

    final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    public void runOpenApiOnExistingImplementation() {
        final String openApiImpl
                = "https://raw.githubusercontent.com/apache/camel-kamelets-examples/main/jbang/open-api/Greetings.java";
        final String openApiUrl
                = "https://raw.githubusercontent.com/apache/camel-kamelets-examples/main/jbang/open-api/greetings-api.json";

        downloadFile(openApiImpl);
        downloadFile(openApiUrl);
        executeBackground("run --open-api greetings-api.json Greetings.java");
        checkLogContains("HTTP endpoints summary");
        HttpResponse<String> response = executeHttpRequest("/camel/greetings/jack", false);
        Assertions.assertThat(response.statusCode()).isEqualTo(200);
        Assertions.assertThat(response.body()).isEqualTo("Hello from jack");
    }

    @Test
    public void runOpenApiUsingContractFirstApproach() throws IOException {
        final String openApiUrl
                = "https://raw.githubusercontent.com/apache/camel-kamelets-examples/main/jbang/open-api-contract-first/petstore-v3.json";
        final String openApiConfig
                = "https://raw.githubusercontent.com/apache/camel-kamelets-examples/main/jbang/open-api-contract-first/petstore.camel.yaml";
        final String appConfig
                = "https://raw.githubusercontent.com/apache/camel-kamelets-examples/refs/heads/main/jbang/open-api-contract-first/application.properties";

        downloadFile(openApiUrl);
        downloadFile(openApiConfig);
        downloadFile(appConfig);
        containerService.executeGenericCommand("mkdir -p camel-mock/pet");
        downloadFile(
                "https://raw.githubusercontent.com/apache/camel-kamelets-examples/main/jbang/open-api-contract-first/camel-mock/pet/123.json");
        containerService.executeGenericCommand("mv 123.json camel-mock/pet/");
        executeBackground("run petstore-v3.json petstore.camel.yaml application.properties");
        checkLogContains("HTTP endpoints summary");

        //verify mock
        HttpResponse<String> response = executeHttpRequest("/myapi/pet/123", true);
        Assertions.assertThat(response.statusCode()).isEqualTo(200);
        Assertions.assertThat(response.body()).contains("Donald the duck");

        //verify sample response
        response = executeHttpRequest("/myapi/pet/" + new Random().nextInt(124, 500), true);
        Assertions.assertThat(response.statusCode()).isEqualTo(200);
        Assertions.assertThat(response.body()).contains("Jack the cat");

        //verify api-doc
        response = executeHttpRequest("/myapi/api-doc", true);
        Assertions.assertThat(response.statusCode()).isEqualTo(200);
        final ObjectMapper objectMapper = new ObjectMapper();
        Map expectedDoc = objectMapper.readValue(URI.create(
                openApiUrl).toURL(),
                Map.class);
        Map actualDoc = objectMapper.readValue(response.body(), Map.class);
        Assertions.assertThat(((Map) actualDoc.get("paths")).size())
                .as("check api doc exposed paths size")
                .isEqualTo(((Map) expectedDoc.get("paths")).size());
    }

    @Test
    public void exportOpenApiUsingContractFirstApproach() {
        final String openApiUrl
                = "https://raw.githubusercontent.com/apache/camel-kamelets-examples/main/jbang/open-api-contract-first/petstore-v3.json";

        downloadFile(openApiUrl);
        final String generatedPath = mountPoint() + "/petstore";
        generateProperties(Map.of("camel.jbang.runtime", "spring-boot", "camel.jbang.gav", "example:petstore:1.0-SNAPSHOT",
                "camel.jbang.exportDir", generatedPath, "camel.jbang.open-api",
                DEFAULT_ROUTE_FOLDER + "/petstore-v3.json"));
        execute("export");
        assertFileInDataFolderExists("petstore");
        assertFileInDataFolderExists("petstore/src/main/resources/petstore-v3.json");
        assertFileInDataFolderExists("petstore/src/main/resources/camel/generated-openapi.yaml");
    }

    @Test
    @InVersion(from = "4.7.00")
    public void generateOpenApiWithDtoUsingContractFirstApproach() {
        final String openApiUrl
                = "https://raw.githubusercontent.com/apache/camel-kamelets-examples/main/jbang/open-api-contract-first/petstore-v3.json";

        downloadFile(openApiUrl);

        execute("plugin add generate");
        execute("plugin get");
        execute("generate rest --dto --input=petstore-v3.json --output=rest-dsl.yaml --runtime=spring-boot --routes");
        List<String> generatedDTO = containerService.listDirectory(DEFAULT_ROUTE_FOLDER + "/model").toList();
        Assertions.assertThat(generatedDTO).as("check generated DTO number").hasSize(8);
        assertFileInContainerExists(DEFAULT_ROUTE_FOLDER + "/rest-dsl.yaml");
    }

    private HttpResponse<String> executeHttpRequest(final String ctxUrl, boolean acceptJson) {
        try {
            final HttpRequest.Builder builder = HttpRequest
                    .newBuilder(
                            new URI(String.format("http://localhost:%s%s", containerService.getDevConsolePort(), ctxUrl)))
                    .timeout(Duration.ofSeconds(5))
                    .GET();
            if (acceptJson) {
                builder.headers("Accept", "application/json");
            }
            return httpClient.send(builder
                    .build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException | URISyntaxException e) {
            Assertions.fail("unable to execute the request");
            throw new RuntimeException(e);
        }
    }
}
