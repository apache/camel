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
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pure, side-effect-focused file-management operations used by the Source tab's file-actions menu (new file / new
 * folder / rename / duplicate / delete). Kept free of any UI so the logic can be unit tested in isolation.
 */
final class SourceFileOps {

    private SourceFileOps() {
    }

    /**
     * Validates a proposed file or folder name.
     *
     * @return {@code null} when the name is acceptable, otherwise a human-readable error message
     */
    static String validateName(String name) {
        if (name == null || name.isBlank()) {
            return "Name must not be empty";
        }
        String trimmed = name.trim();
        if (trimmed.equals(".") || trimmed.equals("..")) {
            return "Invalid name: " + trimmed;
        }
        // A name is a single path segment: reject anything that could turn it into a path (absolute paths such as
        // /usr/evil, parent traversal, drive letters like C:\).
        if (trimmed.indexOf('/') >= 0 || trimmed.indexOf('\\') >= 0) {
            return "Name must not contain path separators";
        }
        if (trimmed.indexOf(':') >= 0) {
            return "Name must not contain ':'";
        }
        if (trimmed.length() > 255) {
            return "Name is too long (max 255 characters)";
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                return "Name must not contain control characters";
            }
        }
        return null;
    }

    /**
     * Suggests a duplicate name for the given file name, e.g. {@code route.camel.yaml} becomes
     * {@code route-copy.camel.yaml}, preserving a compound extension.
     */
    static String suggestDuplicateName(String name) {
        if (name == null || name.isBlank()) {
            return "copy";
        }
        // preserve compound extensions such as .camel.yaml by splitting at the first dot
        int dot = name.indexOf('.');
        if (dot <= 0) {
            return name + "-copy";
        }
        return name.substring(0, dot) + "-copy" + name.substring(dot);
    }

    static Path createFile(Path dir, String name) throws IOException {
        Path target = resolveNew(dir, name);
        Files.createFile(target);
        return target;
    }

    static Path createFolder(Path dir, String name) throws IOException {
        Path target = resolveNew(dir, name);
        Files.createDirectory(target);
        return target;
    }

    static Path rename(Path source, String newName) throws IOException {
        String err = validateName(newName);
        if (err != null) {
            throw new IllegalArgumentException(err);
        }
        Path target = source.resolveSibling(newName.trim());
        ensureChildOf(source.getParent(), target);
        if (Files.exists(target)) {
            throw new IOException("Already exists: " + target.getFileName());
        }
        Files.move(source, target);
        return target;
    }

    static Path copy(Path source, String newName) throws IOException {
        String err = validateName(newName);
        if (err != null) {
            throw new IllegalArgumentException(err);
        }
        Path target = source.resolveSibling(newName.trim());
        ensureChildOf(source.getParent(), target);
        if (Files.exists(target)) {
            throw new IOException("Already exists: " + target.getFileName());
        }
        Files.copy(source, target);
        return target;
    }

    /**
     * Deletes a file or an empty directory. Refuses to delete a non-empty directory to avoid accidental recursive loss
     * of work.
     */
    static void delete(Path target) throws IOException {
        if (Files.isDirectory(target)) {
            try (var stream = Files.list(target)) {
                if (stream.findAny().isPresent()) {
                    throw new IOException("Directory is not empty: " + target.getFileName());
                }
            }
        }
        Files.delete(target);
    }

    private static Path resolveNew(Path dir, String name) throws IOException {
        String err = validateName(name);
        if (err != null) {
            throw new IllegalArgumentException(err);
        }
        Path target = dir.resolve(name.trim());
        ensureChildOf(dir, target);
        if (Files.exists(target)) {
            throw new IOException("Already exists: " + target.getFileName());
        }
        return target;
    }

    /**
     * Defense-in-depth guard: verifies {@code target} resolves to a direct child of {@code parent} after normalization.
     * {@link #validateName} already blocks separators and traversal, but this ensures the framework never operates
     * outside the intended directory even if a name slips past validation.
     */
    private static void ensureChildOf(Path parent, Path target) throws IOException {
        if (parent == null) {
            throw new IOException("Cannot resolve target directory");
        }
        Path normParent = parent.toAbsolutePath().normalize();
        Path normTarget = target.toAbsolutePath().normalize();
        if (!normParent.equals(normTarget.getParent())) {
            throw new IOException("Refusing to operate outside " + normParent);
        }
    }
}
