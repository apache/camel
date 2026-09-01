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
import java.util.LinkedHashSet;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.avro.util.ClassSecurityValidator;
import org.apache.avro.util.ClassSecurityValidator.ClassSecurityPredicate;

/**
 * Configures Apache Avro {@link ClassSecurityValidator} with Camel trusted packages.
 * <p>
 * Avro 1.12+ validates classes resolved from schemas. Camel automatically trusts the Avro IPC packages and any packages
 * derived from configured protocol or schema classes. Additional packages can be configured through the
 * {@code serializablePackages} endpoint option.
 */
public final class AvroClassSecuritySupport {

    /**
     * Camel-managed trusted packages merged across Avro components and data formats.
     */
    public static final String CAMEL_TRUSTED_PACKAGES_PROPERTY = "org.apache.camel.avro.TRUSTED_PACKAGES";

    private static final String AVRO_IPC_PACKAGES = "org.apache.avro";

    private static final ClassSecurityPredicate CAMEL_TRUSTED_PACKAGES = AvroClassSecuritySupport::isCamelTrusted;

    private AvroClassSecuritySupport() {
    }

    /**
     * Ensures Avro IPC classes are trusted. Called once when the Avro component or data format starts.
     */
    public static void ensureAvroIpcPackagesTrusted() {
        trustPackages(AVRO_IPC_PACKAGES);
    }

    /**
     * Trusts the package of the given class name.
     */
    public static void trustClassName(String className) {
        if (className == null || className.isBlank()) {
            return;
        }
        int lastDot = className.lastIndexOf('.');
        if (lastDot > 0) {
            trustPackages(className.substring(0, lastDot));
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
        synchronized (AvroClassSecuritySupport.class) {
            Set<String> merged = new LinkedHashSet<>(readTrustedPackages());
            for (String pkg : packages) {
                if (pkg != null && !pkg.isBlank()) {
                    merged.add(normalizePackage(pkg));
                }
            }
            writeTrustedPackages(merged);
            configureGlobal();
        }
    }

    private static void configureGlobal() {
        ClassSecurityValidator.setGlobal(
                ClassSecurityValidator.composite(ClassSecurityValidator.DEFAULT, CAMEL_TRUSTED_PACKAGES));
    }

    private static boolean isCamelTrusted(Class<?> clazz) {
        String className = clazz.getName();
        NavigableSet<String> packages = normalizedPackages(readTrustedPackages());
        String lower = packages.lower(className);
        return lower != null && className.startsWith(lower);
    }

    private static Set<String> readTrustedPackages() {
        String value = System.getProperty(CAMEL_TRUSTED_PACKAGES_PROPERTY);
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return parsePackages(value).stream().collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static void writeTrustedPackages(Set<String> packages) {
        if (packages.isEmpty()) {
            System.clearProperty(CAMEL_TRUSTED_PACKAGES_PROPERTY);
        } else {
            System.setProperty(CAMEL_TRUSTED_PACKAGES_PROPERTY, String.join(",", packages));
        }
    }

    private static Set<String> parsePackages(String packages) {
        return Arrays.stream(packages.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(AvroClassSecuritySupport::normalizePackage)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String normalizePackage(String pkg) {
        String normalized = pkg.trim();
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
