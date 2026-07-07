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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Canonical dark/light palette selector for the Camel TUI. */
enum ThemeMode {

    DARK("dark"),
    LIGHT("light");

    private final String id;

    ThemeMode(String id) {
        this.id = id;
    }

    /** Lowercase config / CLI value ({@code dark} or {@code light}). */
    String id() {
        return id;
    }

    ThemeMode toggle() {
        return this == DARK ? LIGHT : DARK;
    }

    static Optional<ThemeMode> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (ThemeMode mode : values()) {
            if (mode.id.equals(normalized)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }

    static ThemeMode parseOrDefault(String value) {
        return parse(value).orElse(DARK);
    }

    static List<String> ids() {
        return List.of(DARK.id, LIGHT.id);
    }
}
