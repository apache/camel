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
package org.apache.camel.dsl.jbang.core.generator;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class QuarkusGenerator implements CamelJbangGenerator {

    private List<String> dependencies;
    private String bomVersion;

    public QuarkusGenerator(List<String> dependencies, String bomVersion) {
        this.dependencies = dependencies;
        this.bomVersion = bomVersion;
    }

    @Override
    public Path getPropertyFileLocation() {
        return Paths.get("src", "main", "resources", "application.properties");
    }

    @Override
    public String getPropertyFileContent(String name) {
        StringBuilder sb = new StringBuilder();
        sb.append("quarkus.banner.enabled = false\n");
        sb.append("quarkus.log.file.enable = true\n");
        sb.append("camel.context.name = ").append(name).append("\n");
        sb.append("camel.main.routes-include-pattern= classpath:routes/*");

        return sb.toString();
    }

    @Override
    public List<PomProperty> getPomProperties() {
        return Arrays.asList(
                new PomProperty("quarkus.platform.version", bomVersion));
    }

    public String getTemplate() {
        return "quarkus-pom.ftl";
    }

    @Override
    public List<PomDependency> getPomDependencies() {
        return dependencies.stream()
                .map(PomDependency::of)
                .collect(Collectors.toList());
    }
}
