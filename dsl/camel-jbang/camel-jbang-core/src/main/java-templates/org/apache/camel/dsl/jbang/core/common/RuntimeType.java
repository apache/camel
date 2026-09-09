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

import java.util.Locale;

import org.apache.camel.catalog.DefaultCamelCatalog;

public enum RuntimeType {

    /**
     * Runs in-process in the Camel CLI (jbang) JVM. Only used by {@code camel run} for fast prototyping; when exporting
     * this is the same as {@link #main}.
     */
    jbang,
    springBoot,
    quarkus,
    main;

    public static final String QUARKUS_EXTENSION_REGISTRY_BASE_URL = "${quarkus-extension-registry-base-url}";
    public static final String SPRING_BOOT_VERSION = "${spring-boot-version}";
    public static final String KAMELETS_VERSION = "${camel-kamelets-catalog-version}";

    public static RuntimeType fromValue(String value) {
        value = value.toLowerCase(Locale.ROOT);
        return switch (value) {
            case "jbang", "camel-jbang" -> RuntimeType.jbang;
            case "spring", "spring-boot", "camel-spring-boot" -> RuntimeType.springBoot;
            case "quarkus", "camel-quarkus" -> RuntimeType.quarkus;
            case "main", "camel-main", "camel" -> RuntimeType.main;
            default -> throw new IllegalArgumentException("Unsupported runtime " + value);
        };
    }

    /**
     * The runtime to use when exporting. The {@link #jbang} runtime is only for running in-process, and is exported as
     * {@link #main}.
     */
    public RuntimeType exportRuntime() {
        return this == jbang ? main : this;
    }

    public String runtime() {
        return switch (this) {
            case jbang -> "jbang";
            case springBoot -> "spring-boot";
            case quarkus -> "quarkus";
            case main -> "main";
        };
    }

    public String version() {
        return switch (this) {
            case springBoot -> SPRING_BOOT_VERSION;
            case quarkus -> throw new UnsupportedOperationException("There is no built in version for Quarkus Runtime. The caller should resolve it at runtime using QuarkusPlatformMixin.resolve()");
            case main, jbang -> new DefaultCamelCatalog().getCatalogVersion();
        };
    }

    @Override
    public String toString() {
        return runtime();
    }
}
