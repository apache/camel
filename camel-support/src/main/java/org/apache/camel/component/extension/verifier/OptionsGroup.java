/**
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
package org.apache.camel.component.extension.verifier;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A named group of options. A group of options requires that a set of
 * component parameters is given as a whole.
 *
 * <a id="#syntax">The option syntax can be
 * {@code "propertyName"} to denote required property and
 * {@code "!propertyName"} to denote required absence of a property.
 */
public final class OptionsGroup implements Serializable {
    private final String name;
    private final Set<String> options;

    /**
     * Creates new named {@link OptionsGroup}.
     *
     * @param name the name of the group
     */
    public OptionsGroup(String name) {
        this.name = name;
        this.options = new HashSet<>();
    }

    /**
     * Creates new named {@link OptionsGroup} with a set of option
     * definitions.
     *
     * @param name the name of the group
     * @param options names of properties in the syntax mentioned in {@link OptionsGroup}
     */
    public OptionsGroup(String name, Collection<String> options) {
        this.name = name;
        this.options = new LinkedHashSet<>(options);
    }

    /**
     * Adds a option definition to this group. The option syntax can be
     * {@code "propertyName"} to denote required property and
     * {@code "!propertyName"} to denote required absence of a property.
     *
     * @param option definition.
     */
    public void addOption(String option) {
        this.options.add(option);
    }

    /**
     * The name of the group.
     */
    public String getName() {
        return name;
    }

    /**
     * The option definitions in this group.
     */
    public Set<String> getOptions() {
        return this.options;
    }

    /**
     * Adds a option definition to this group. The option syntax can be
     * {@code "propertyName"} to denote required property and
     * {@code "!propertyName"} to denote required absence of a property.
     *
     * @param option definition.
     */
    public OptionsGroup option(String option) {
        this.options.add(option);
        return this;
    }

    /**
     * Adds a number of option definitions to this group. The option
     * syntax can be {@code "propertyName"} to denote required
     * property and {@code "!propertyName"} to denote required absence
     * of a property.
     *
     * @param options options definition
     */
    public OptionsGroup options(String... options) {
        for (String option : options) {
            addOption(option);
        }

        return this;
    }

    /**
     * Creates new group with the specified name.
     *
     * @param name the name of the group
     */
    public static OptionsGroup withName(String name) {
        return new OptionsGroup(name);
    }

    /**
     * Creates new group with the specified name of the given
     * {@link Enum} name.
     *
     * @param enumItem the name of the group
     * @see Enum#name()
     */
    public static OptionsGroup withName(Enum<?> enumItem) {
        return new OptionsGroup(enumItem.name());
    }

    /**
     * Creates new group with the specified name and option definitions.
     *
     * @param name the name of the group
     * @param options options definition 
     */
    public static OptionsGroup withNameAndOptions(String name, String... options) {
        return new OptionsGroup(name, Arrays.asList(options));
    }
}
