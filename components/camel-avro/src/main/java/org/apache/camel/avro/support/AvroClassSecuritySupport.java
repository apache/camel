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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.avro.Protocol;
import org.apache.avro.Schema;
import org.apache.avro.util.ClassSecurityValidator;
import org.apache.avro.util.ClassSecurityValidator.ClassSecurityPredicate;

/**
 * Configures Apache Avro {@link ClassSecurityValidator} with Camel trusted packages.
 * <p>
 * Avro 1.12+ validates classes resolved from schemas. Camel automatically trusts packages derived from configured
 * protocol or schema classes. Additional packages can be configured through the {@code serializablePackages} endpoint
 * option.
 * <p>
 * Trusted packages are stored in a JVM-wide registry shared by all Camel contexts in the process. Trust is cumulative
 * and cannot be revoked in production.
 */
public final class AvroClassSecuritySupport {

    private static final Set<String> TRUSTED_PACKAGES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final Set<String> TRUSTED_CLASSES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final Object LOCK = new Object();

    private static final ClassSecurityPredicate CAMEL_TRUSTED = AvroClassSecuritySupport::isCamelTrusted;

    private static volatile ClassSecurityPredicate baseValidator = ClassSecurityValidator.DEFAULT;

    private static volatile ClassSecurityPredicate installedGlobal;

    private static volatile List<String> normalizedPackagePrefixes = List.of();

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
            if (!applyClassNameTrust(className, true)) {
                return;
            }
            commitTrustChanges();
        }
    }

    /**
     * Trusts only the exact class name without trusting its whole package.
     */
    public static void trustClassNameOnly(String className) {
        if (className == null || className.isBlank()) {
            return;
        }
        synchronized (LOCK) {
            if (!applyClassNameTrust(className, false)) {
                return;
            }
            commitTrustChanges();
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
            boolean changed = false;
            for (String pkg : packages) {
                if (pkg != null && !pkg.isBlank()) {
                    String normalized = normalizePackage(pkg);
                    if (!isSystemPackage(normalized)) {
                        changed |= TRUSTED_PACKAGES.add(normalized);
                    }
                }
            }
            if (!changed) {
                return;
            }
            commitTrustChanges();
        }
    }

    /**
     * Trusts all named types reachable from the given schema graph.
     */
    public static void trustSchema(Schema schema) {
        if (schema == null) {
            return;
        }
        synchronized (LOCK) {
            Set<Schema> visited = new HashSet<>();
            boolean changed = collectSchemaTrust(schema, visited);
            if (!changed) {
                return;
            }
            commitTrustChanges();
        }
    }

    /**
     * Trusts all named types reachable from the given RPC protocol.
     */
    public static void trustProtocol(Protocol protocol) {
        if (protocol == null) {
            return;
        }
        synchronized (LOCK) {
            boolean changed = false;
            String namespace = protocol.getNamespace();
            if (namespace != null && !namespace.isBlank() && !isSystemPackage(namespace)) {
                changed |= TRUSTED_PACKAGES.add(normalizePackage(namespace));
            }
            Set<Schema> visited = new HashSet<>();
            for (Schema type : protocol.getTypes()) {
                if (!type.isError()) {
                    changed |= collectSchemaTrust(type, visited);
                }
            }
            for (Protocol.Message message : protocol.getMessages().values()) {
                changed |= collectSchemaTrust(message.getRequest(), visited);
                changed |= collectSchemaTrust(message.getResponse(), visited);
                changed |= collectSchemaTrust(message.getErrors(), visited);
            }
            if (!changed) {
                return;
            }
            commitTrustChanges();
        }
    }

    /**
     * Clears Camel-managed trusted classes and packages. Intended for tests.
     */
    public static void resetForTesting() {
        synchronized (LOCK) {
            TRUSTED_PACKAGES.clear();
            TRUSTED_CLASSES.clear();
            normalizedPackagePrefixes = List.of();
            baseValidator = ClassSecurityValidator.DEFAULT;
            installedGlobal = null;
            ClassSecurityValidator.setGlobal(ClassSecurityValidator.DEFAULT);
        }
    }

    private static boolean applyClassNameTrust(String className, boolean trustPackage) {
        boolean changed = TRUSTED_CLASSES.add(className);
        if (trustPackage) {
            int lastDot = className.lastIndexOf('.');
            if (lastDot > 0) {
                String pkg = normalizePackage(className.substring(0, lastDot));
                if (!isSystemPackage(pkg)) {
                    changed |= TRUSTED_PACKAGES.add(pkg);
                }
            }
        }
        return changed;
    }

    private static boolean collectSchemaTrust(Schema schema, Set<Schema> visited) {
        if (schema == null || !visited.add(schema)) {
            return false;
        }
        boolean changed = false;
        switch (schema.getType()) {
            case RECORD, ENUM, FIXED -> {
                String namespace = schema.getNamespace();
                if (namespace != null && !namespace.isBlank() && !isSystemPackage(namespace)) {
                    changed |= TRUSTED_PACKAGES.add(normalizePackage(namespace));
                }
                String fullName = schema.getFullName();
                if (fullName != null && !fullName.isBlank()) {
                    changed |= TRUSTED_CLASSES.add(fullName);
                }
                if (schema.getType() == Schema.Type.RECORD) {
                    for (Schema.Field field : schema.getFields()) {
                        changed |= collectSchemaTrust(field.schema(), visited);
                    }
                }
            }
            case ARRAY -> changed |= collectSchemaTrust(schema.getElementType(), visited);
            case MAP -> changed |= collectSchemaTrust(schema.getValueType(), visited);
            case UNION -> {
                for (Schema branch : schema.getTypes()) {
                    changed |= collectSchemaTrust(branch, visited);
                }
            }
            default -> {
                // primitives and other non-named roots
            }
        }
        return changed;
    }

    private static void commitTrustChanges() {
        rebuildNormalizedPackagePrefixes();
        refreshGlobal();
    }

    private static void refreshGlobal() {
        ClassSecurityPredicate current = ClassSecurityValidator.getGlobal();
        if (installedGlobal == null) {
            if (current != null && current != ClassSecurityValidator.DEFAULT) {
                baseValidator = current;
            }
        } else if (current != installedGlobal) {
            baseValidator = current;
        }
        installedGlobal = ClassSecurityValidator.composite(baseValidator, CAMEL_TRUSTED);
        ClassSecurityValidator.setGlobal(installedGlobal);
    }

    private static boolean isCamelTrusted(Class<?> clazz) {
        String className = clazz.getName();
        if (TRUSTED_CLASSES.contains(className)) {
            return true;
        }
        for (String prefix : normalizedPackagePrefixes) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static void rebuildNormalizedPackagePrefixes() {
        normalizedPackagePrefixes = TRUSTED_PACKAGES.stream()
                .map(pkg -> normalizePackage(pkg) + ".")
                .sorted()
                .toList();
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
        if ("*".equals(normalized)) {
            throw new IllegalArgumentException(
                    "Wildcard '*' is not supported in serializablePackages because it disables Avro class-loading protection");
        }
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    static boolean isSystemPackage(String pkg) {
        if (pkg == null || pkg.isBlank()) {
            return true;
        }
        return pkg.startsWith("java.")
                || pkg.startsWith("javax.")
                || pkg.startsWith("jdk.")
                || pkg.startsWith("sun.");
    }
}
