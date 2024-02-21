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

    CAMEL_K("camel-k", "k", "Manage Camel integrations on Kubernetes");

    private final String name;
    private final String command;
    private final String description;

    PluginType(String name, String command, String description) {
        this.name = name;
        this.command = command;
        this.description = description;
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
}
