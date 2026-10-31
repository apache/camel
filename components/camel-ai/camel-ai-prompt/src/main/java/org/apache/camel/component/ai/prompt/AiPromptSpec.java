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
package org.apache.camel.component.ai.prompt;

import java.util.List;
import java.util.Objects;

import org.apache.camel.support.DefaultConsumer;

/**
 * Framework-neutral metadata for a prompt exposed from a Camel route.
 *
 * @since 4.24
 */
public final class AiPromptSpec {

    private final String name;
    private final String description;
    private final String title;
    private final List<AiPromptArgument> arguments;
    private final DefaultConsumer consumer;

    public AiPromptSpec(
                        String name, String description, String title, List<AiPromptArgument> arguments,
                        DefaultConsumer consumer) {
        this.name = name;
        this.description = description;
        this.title = title;
        this.arguments = arguments != null ? List.copyOf(arguments) : List.of();
        this.consumer = consumer;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getTitle() {
        return title;
    }

    public List<AiPromptArgument> getArguments() {
        return arguments;
    }

    public DefaultConsumer getConsumer() {
        return consumer;
    }

    public java.util.Set<String> getDeclaredArgumentNames() {
        return arguments.stream().map(AiPromptArgument::name).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public java.util.Set<String> getRequiredArgumentNames() {
        return arguments.stream().filter(AiPromptArgument::required).map(AiPromptArgument::name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        AiPromptSpec that = (AiPromptSpec) other;
        return Objects.equals(name, that.name) && Objects.equals(description, that.description)
                && Objects.equals(title, that.title) && Objects.equals(arguments, that.arguments)
                && Objects.equals(consumer, that.consumer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, title, arguments, consumer);
    }

    @Override
    public String toString() {
        return "AiPromptSpec{name=" + name + '}';
    }
}
