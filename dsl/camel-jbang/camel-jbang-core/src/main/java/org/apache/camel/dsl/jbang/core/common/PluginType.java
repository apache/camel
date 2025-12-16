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

import java.util.Arrays;
import java.util.Optional;

/**
 * Known plugins in the Camel project.
 */
public enum PluginType {

    KUBERNETES("kubernetes", "kubernetes", "Run Camel applications on Kubernetes", "4.8.0", null),
    GENERATE("generate", "generate", "Generate code such as DTOs", "4.8.0", null),
    EDIT("edit", "edit", "Edit Camel files with suggestions", "4.12.0", null),
    TEST("test", "test", "Manage tests for Camel applications", "4.14.0", null);

    private final String name;
    private final String command;
    private final String description;
    private final String firstVersion;
    private final String repos;

    PluginType(String name, String command, String description, String firstVersion, String repos) {
        this.name = name;
        this.command = command;
        this.description = description;
        this.firstVersion = firstVersion;
        this.repos = repos;
    }

    public static Optional<PluginType> findByName(String name) {
        return Arrays.stream(values())
                .filter(p -> p.name.equalsIgnoreCase(name))
                .findFirst();
    }

    public String getName() {
        return name;
    }

    public String getCommand() {
        return command;
    }

    public String getDescription() {
        return description;
    }

    public String getFirstVersion() {
        return firstVersion;
    }

    public String getRepos() {
        return repos;
    }
}
