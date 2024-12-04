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

    KUBERNETES("kubernetes", "kubernetes", "Run Camel applications on Kubernetes", "4.8.0"),
    GENERATE("generate", "generate", "Generate code such as DTOs", "4.8.0");

    private final String name;
    private final String command;
    private final String description;
    private final String firstVersion;

    PluginType(String name, String command, String description, String firstVersion) {
        this.name = name;
        this.command = command;
        this.description = description;
        this.firstVersion = firstVersion;
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
}
