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
package org.apache.camel.avro.support;

import java.util.Arrays;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.avro.util.ClassSecurityValidator;
import org.apache.avro.util.ClassSecurityValidator.ClassSecurityPredicate;

/**
 * Configures Apache Avro {@link ClassSecurityValidator} with Camel trusted packages.
 * <p>
 * Avro 1.12+ validates classes resolved from schemas. Camel automatically trusts packages derived from configured
 * protocol or schema classes. Additional packages can be configured through the {@code serializablePackages} endpoint
 * option.
 */
public final class AvroClassSecuritySupport {

    private static final Set<String> TRUSTED_PACKAGES = ConcurrentHashMap.newKeySet();

    private static final Set<String> TRUSTED_CLASSES = ConcurrentHashMap.newKeySet();

    private static final Object LOCK = new Object();

    private static final ClassSecurityPredicate CAMEL_TRUSTED = AvroClassSecuritySupport::isCamelTrusted;

    private AvroClassSecuritySupport() {
    }

    /**
     * Trusts Avro IPC classes required for camel-avro-rpc handshake.
     */
    public static void ensureAvroIpcPackagesTrusted() {
        trustPackages("org.apache.avro.ipc");
    }

    /**
     * Trusts the exact class name and its package for schema resolution.
     */
    public static void trustClassName(String className) {
        if (className == null || className.isBlank()) {
            return;
        }
        synchronized (LOCK) {
            TRUSTED_CLASSES.add(className);
            int lastDot = className.lastIndexOf('.');
            if (lastDot > 0) {
                TRUSTED_PACKAGES.add(normalizePackage(className.substring(0, lastDot)));
            }
            refreshGlobal();
        }
    }

    /**
     * Trusts the comma-separated list of packages.
     */
    public static void trustPackages(String packages) {
        if (packages == null || packages.isBlank()) {
            return;
        }
        trustPackages(parsePackages(packages).toArray(String[]::new));
    }

    /**
     * Trusts the given packages.
     */
    public static void trustPackages(String... packages) {
        if (packages == null || packages.length == 0) {
            return;
        }
        synchronized (LOCK) {
            for (String pkg : packages) {
                if (pkg != null && !pkg.isBlank()) {
                    TRUSTED_PACKAGES.add(normalizePackage(pkg));
                }
            }
            refreshGlobal();
        }
    }

    /**
     * Clears Camel-managed trusted classes and packages. Intended for tests.
     */
    public static void resetForTesting() {
        synchronized (LOCK) {
            TRUSTED_PACKAGES.clear();
            TRUSTED_CLASSES.clear();
            ClassSecurityValidator.setGlobal(ClassSecurityValidator.DEFAULT);
        }
    }

    private static void refreshGlobal() {
        ClassSecurityValidator.setGlobal(
                ClassSecurityValidator.composite(ClassSecurityValidator.DEFAULT, CAMEL_TRUSTED));
    }

    private static boolean isCamelTrusted(Class<?> clazz) {
        String className = clazz.getName();
        if (TRUSTED_CLASSES.contains(className)) {
            return true;
        }
        NavigableSet<String> packages = normalizedPackages(TRUSTED_PACKAGES);
        String lower = packages.lower(className);
        return lower != null && className.startsWith(lower);
    }

    private static Set<String> parsePackages(String packages) {
        return Arrays.stream(packages.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(AvroClassSecuritySupport::normalizePackage)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private static String normalizePackage(String pkg) {
        String normalized = pkg.trim();
        if ("*".equals(normalized)) {
            throw new IllegalArgumentException(
                    "Wildcard '*' is not supported in serializablePackages because it disables Avro class-loading protection");
        }
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static NavigableSet<String> normalizedPackages(Set<String> packages) {
        NavigableSet<String> normalized = new TreeSet<>();
        for (String pkg : packages) {
            normalized.add(normalizePackage(pkg) + ".");
        }
        return normalized;
    }
}
