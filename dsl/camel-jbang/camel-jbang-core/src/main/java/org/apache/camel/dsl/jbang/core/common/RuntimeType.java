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

import org.apache.camel.catalog.DefaultCamelCatalog;

public enum RuntimeType {

    springBoot,
    quarkus,
    main;

    public static final String QUARKUS_VERSION = "3.13.2";
    public static final String SPRING_BOOT_VERSION = "3.3.2";

    public static RuntimeType fromValue(String value) {
        return switch (value) {
            case "spring-boot", "camel-spring-boot" -> RuntimeType.springBoot;
            case "quarkus", "camel-quarkus" -> RuntimeType.quarkus;
            case "main", "camel-main" -> RuntimeType.main;
            default -> throw new IllegalArgumentException("Unsupported runtime " + value);
        };
    }

    public String runtime() {
        return switch (this) {
            case springBoot -> "spring-boot";
            case quarkus -> "quarkus";
            case main -> "camel-main";
        };
    }

    public String version() {
        return switch (this) {
            case springBoot -> SPRING_BOOT_VERSION;
            case quarkus -> QUARKUS_VERSION;
            case main -> new DefaultCamelCatalog().getCatalogVersion();
        };
    }
}
