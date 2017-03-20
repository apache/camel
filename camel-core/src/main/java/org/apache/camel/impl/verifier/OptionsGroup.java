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
package org.apache.camel.impl.verifier;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class OptionsGroup implements Serializable {
    private final String name;
    private final Set<String> options;

    public OptionsGroup(String name) {
        this.name = name;
        this.options = new HashSet<>();
    }

    public OptionsGroup(String name, Collection<String> options) {
        this.name = name;
        this.options = new HashSet<>(options);
    }

    public void addOption(String option) {
        this.options.add(option);
    }

    public String getName() {
        return name;
    }

    public Set<String> getOptions() {
        return this.options;
    }

    public OptionsGroup option(String option) {
        this.options.add(option);
        return this;
    }

    public OptionsGroup options(String... options) {
        for (String option : options) {
            addOption(option);
        }

        return this;
    }

    public static OptionsGroup withName(String name) {
        return new OptionsGroup(name);
    }

    public static OptionsGroup withName(Enum<?> enumItem) {
        return new OptionsGroup(enumItem.name());
    }

    public static OptionsGroup withNameAndOptions(String name, String... options) {
        return new OptionsGroup(name, Arrays.asList(options));
    }
}
