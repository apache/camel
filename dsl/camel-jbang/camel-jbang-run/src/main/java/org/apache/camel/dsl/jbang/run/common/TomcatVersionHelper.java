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
package org.apache.camel.dsl.jbang.run.common;

/**
 * Tomcat image selection and version compatibility for Spring Boot WAR exports.
 */
public final class TomcatVersionHelper {

    private static final String SPRING_BOOT_4_CAMEL_VERSION = "4.19.0";

    private TomcatVersionHelper() {
    }

    public static boolean supportsSpringBootVersion(String version) {
        return version != null
                && (version.startsWith("3.") || version.startsWith("4."))
                && version.length() > 2
                && Character.isDigit(version.charAt(2));
    }

    public static String tomcatVersion(String springBootVersion) {
        // Spring Boot 4 is the default when no explicit version is supplied.
        if (springBootVersion == null) {
            return "11.0";
        }

        if (springBootVersion.startsWith("3.")) {
            return "10.1";
        }
        if (springBootVersion.startsWith("4.")) {
            return "11.0";
        }

        throw new IllegalArgumentException(
                "Unsupported Spring Boot version for Tomcat: " + springBootVersion);
    }

    public static String defaultBaseImage(String springBootVersion, String javaVersion) {
        return "mirror.gcr.io/library/tomcat:%s-jdk%s-temurin"
                .formatted(tomcatVersion(springBootVersion), javaVersion);
    }

    /**
     * Camel Spring Boot 4.19 moved to Spring Boot 4. Spring Boot 3 therefore requires the preceding Camel Spring Boot
     * line.
     */
    public static String compatibilityError(
            String springBootVersion, String camelSpringBootVersion) {

        if (springBootVersion == null) {
            return "A Spring Boot version must be specified.";
        }

        if (camelSpringBootVersion == null || camelSpringBootVersion.isBlank()) {
            if (springBootVersion.startsWith("3.")) {
                return "Spring Boot 3.x requires an explicitly selected compatible Camel version. "
                       + "Use --camel-spring-boot-version=4.18.1 or specify a compatible "
                       + "--camel-version=4.18.1.";
            }
            return "A compatible Camel Spring Boot version must be specified.";
        }

        int comparison = VersionHelper.compare(
                camelSpringBootVersion, SPRING_BOOT_4_CAMEL_VERSION);

        if (springBootVersion.startsWith("3.") && comparison >= 0) {
            return "Spring Boot 3.x requires Camel Spring Boot earlier than 4.19.0. "
                   + "Use --camel-spring-boot-version=4.18.1 or another compatible 4.18.x release.";
        }

        if (springBootVersion.startsWith("4.") && comparison < 0) {
            return "Spring Boot 4.x requires Camel Spring Boot 4.19.0 or later.";
        }

        return null;
    }
}
