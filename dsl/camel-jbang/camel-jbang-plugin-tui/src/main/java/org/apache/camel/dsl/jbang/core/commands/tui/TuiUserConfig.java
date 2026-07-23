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

import java.io.IOException;
import java.io.UncheckedIOException;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.Printer;

/**
 * Central read/write for the Camel TUI's per-user preferences ({@code camel.tui.*}) across the Camel CLI configuration
 * files, applying <em>per-key</em> precedence between the local ({@code ./camel-cli.properties}) and global
 * ({@code ~/.camel-cli.properties}) files.
 * <p>
 * A key's home is the file where it currently lives: a key present in the local file is treated as a project-level
 * override and is both read from and written back to the local file; every other key is personal state and is read from
 * and written to the global file. This keeps a project's deliberately committed overrides (for example a pinned
 * starting tab) intact, while never redirecting a user's personal preferences (theme, last folder, ...) into an
 * unrelated project's config just because a local file happens to exist for other CLI settings.
 * <p>
 * Note: if the same key exists in <em>both</em> files (an unusual hand-edited state), the local copy wins for reads and
 * is the one updated on writes; the stale global copy is left as-is.
 */
final class TuiUserConfig {

    private TuiUserConfig() {
    }

    /**
     * Reads a single {@code camel.tui.*} key. Returns the local value when the key is present in the local file,
     * otherwise the global value, otherwise {@code null}. Propagates a {@link RuntimeException} on an I/O failure so
     * the caller can decide whether to fall back or log.
     */
    static String read(String key) {
        boolean local = livesInLocal(key);
        String[] holder = { null };
        CommandLineHelper.loadProperties(props -> holder[0] = props.getProperty(key), local);
        return holder[0];
    }

    /**
     * Writes a single {@code camel.tui.*} key back to its home file (the local file when the key is already a local
     * override, otherwise the global file). A blank or {@code null} value removes the key. Other keys in the target
     * file are preserved. Propagates a {@link RuntimeException} on failure so the caller can log.
     */
    static void write(String key, String value) {
        boolean local = livesInLocal(key);
        try {
            CommandLineHelper.createPropertyFile(local);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String trimmed = trimToNull(value);
        CommandLineHelper.loadProperties(props -> {
            if (trimmed == null) {
                props.remove(key);
            } else {
                props.setProperty(key, trimmed);
            }
            CommandLineHelper.storeProperties(props,
                    new Printer.QuietPrinter(new Printer.SystemOutPrinter()), local);
        }, local);
    }

    private static boolean livesInLocal(String key) {
        if (!CommandLineHelper.hasLocalUserConfig()) {
            return false;
        }
        boolean[] present = { false };
        // Use getProperty (overridden by OrderedProperties) instead of containsKey
        // (inherited from Hashtable and always empty in OrderedProperties).
        CommandLineHelper.loadProperties(props -> present[0] = props.getProperty(key) != null, true);
        return present[0];
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
