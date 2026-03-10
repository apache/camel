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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.ResourceTemplateArg;
import io.quarkiverse.mcp.server.TextResourceContents;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * MCP Resources exposing Camel test infrastructure reference data.
 * <p>
 * These resources provide browseable test-infra service mappings that clients can pull into context when helping users
 * write tests for Camel routes. Each service maps component schemes to their Testcontainers-based test infrastructure
 * classes and Maven dependencies.
 */
@ApplicationScoped
public class TestInfraResources {

    @Inject
    TestInfraData testInfraData;

    /**
     * All available Camel test infrastructure services with their supported component schemes.
     */
    @Resource(uri = "camel://testing/infra-services",
              name = "camel_testing_infra_services",
              title = "Camel Test Infrastructure Services",
              description = "Registry of all available Camel Testcontainers-based test infrastructure services "
                            + "including Kafka, Artemis (JMS), MongoDB, PostgreSQL, Redis, RabbitMQ, FTP, and more. "
                            + "Each service lists its supported component schemes, service/factory classes, "
                            + "and Maven artifact ID.",
              mimeType = "application/json")
    public TextResourceContents infraServices() {
        JsonObject result = new JsonObject();

        // Group schemes by unique service (reverse the map)
        Map<TestInfraData.TestInfraInfo, List<String>> serviceToSchemes = new LinkedHashMap<>();
        for (Map.Entry<String, TestInfraData.TestInfraInfo> entry : testInfraData.getTestInfraMap().entrySet()) {
            serviceToSchemes
                    .computeIfAbsent(entry.getValue(), k -> new ArrayList<>())
                    .add(entry.getKey());
        }

        JsonArray services = new JsonArray();
        for (Map.Entry<TestInfraData.TestInfraInfo, List<String>> entry : serviceToSchemes.entrySet()) {
            TestInfraData.TestInfraInfo info = entry.getKey();
            JsonObject serviceJson = new JsonObject();
            serviceJson.put("serviceClass", info.serviceClass());
            serviceJson.put("factoryClass", info.factoryClass());
            serviceJson.put("artifactId", info.artifactId());
            serviceJson.put("package", info.packageName());

            JsonArray schemesJson = new JsonArray();
            for (String scheme : entry.getValue()) {
                schemesJson.add(scheme);
            }
            serviceJson.put("schemes", schemesJson);

            services.add(serviceJson);
        }

        result.put("services", services);
        result.put("totalServices", services.size());
        result.put("totalSchemes", testInfraData.getTestInfraMap().size());

        return new TextResourceContents("camel://testing/infra-services", result.toJson(), "application/json");
    }

    /**
     * Test infrastructure detail for a specific component scheme.
     */
    @ResourceTemplate(uriTemplate = "camel://testing/infra-service/{scheme}",
                      name = "camel_testing_infra_service_detail",
                      title = "Test Infrastructure Service Detail",
                      description = "Test infrastructure detail for a specific Camel component scheme including "
                                    + "the Testcontainers service class, factory class, Maven artifact ID, "
                                    + "and a usage snippet with @RegisterExtension.",
                      mimeType = "application/json")
    public TextResourceContents infraServiceDetail(
            @ResourceTemplateArg(name = "scheme") String scheme) {

        String uri = "camel://testing/infra-service/" + scheme;

        TestInfraData.TestInfraInfo info = testInfraData.getTestInfra(scheme);
        if (info == null) {
            JsonObject result = new JsonObject();
            result.put("scheme", scheme);
            result.put("found", false);
            result.put("message", "No test infrastructure service is available for scheme '" + scheme + "'. "
                                  + "Use the camel://testing/infra-services resource to see all supported schemes.");
            return new TextResourceContents(uri, result.toJson(), "application/json");
        }

        JsonObject result = new JsonObject();
        result.put("scheme", scheme);
        result.put("found", true);
        result.put("serviceClass", info.serviceClass());
        result.put("factoryClass", info.factoryClass());
        result.put("artifactId", info.artifactId());
        result.put("package", info.packageName());

        // Usage snippet
        String fieldName = Character.toLowerCase(info.serviceClass().charAt(0)) + info.serviceClass().substring(1);
        String snippet = "@RegisterExtension\n"
                         + "static " + info.serviceClass() + " " + fieldName
                         + " = " + info.factoryClass() + ".createService();";
        result.put("usageSnippet", snippet);

        // Maven dependency
        result.put("mavenDependency",
                "<dependency>\n"
                                      + "    <groupId>org.apache.camel</groupId>\n"
                                      + "    <artifactId>" + info.artifactId() + "</artifactId>\n"
                                      + "    <scope>test</scope>\n"
                                      + "</dependency>");

        return new TextResourceContents(uri, result.toJson(), "application/json");
    }
}
