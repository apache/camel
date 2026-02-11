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

import org.apache.camel.spi.Metadata;

/**
 * Common set of camel.jbang.xxx configuration options that can be defined in application.properties to control running
 * and exporting commands.
 */
public final class CamelJBangConstants {

    // internal options which is not intended for Camel users
    public static final String BACKGROUND = "camel.jbang.background";
    public static final String BACKGROUND_WAIT = "camel.jbang.backgroundWait";
    public static final String JVM_DEBUG = "camel.jbang.jvmDebug";
    public static final String TRANSFORM = "camel.jbang.transform";
    public static final String EXPORT = "camel.jbang.export";
    public static final String DEBUG = "camel.jbang.debug";

    @Metadata(description = "Additional files to add to classpath (Use commas to separate multiple files).",
              javaType = "String")
    public static final String CLASSPATH_FILES = "camel.jbang.classpathFiles";

    @Metadata(description = "Local file directory for loading custom Kamelets",
              javaType = "String")
    public static final String LOCAL_KAMELET_DIR = "camel.jbang.localKameletDir";

    @Metadata(description = "Additional groovy source files to export to src/main/resources/camel-groovy directory (Use commas to separate multiple files)",
              javaType = "String")
    public static final String GROOVY_FILES = "camel.jbang.groovyFiles";

    @Metadata(description = "Additional shell script files to export to src/main/scripts directory",
              javaType = "String")
    public static final String SCRIPT_FILES = "camel.jbang.scriptFiles";

    @Metadata(description = "Additional SSL/TLS files to export to src/main/tls directory",
              javaType = "String")
    public static final String TLS_FILES = "camel.jbang.tlsFiles";

    @Metadata(description = "Resource YAML fragments for Kubernetes using Eclipse JKube tool (Use commas to separate multiple files).",
              javaType = "String", label = "kubernetes")
    public static final String JKUBE_FILES = "camel.jbang.jkubeFiles";

    @Metadata(description = "Which runtime to use (camel-main, spring-boot, quarkus)",
              javaType = "String", enums = "camel-main,spring-boot,quarkus")
    public static final String RUNTIME = "camel.jbang.runtime";

    @Metadata(description = "Maven coordinate (groupId:artifactId:version)",
              javaType = "String")
    public static final String GAV = "camel.jbang.gav";

    @Metadata(description = "Java version (17 or 21)",
              javaType = "String", enums = "17,21", defaultValue = "21")
    public static final String JAVA_VERSION = "camel.jbang.javaVersion";

    @Metadata(description = "Apache Camel Kamelets version. By default the Kamelets are the same version as Camel.",
              javaType = "String")
    public static final String KAMELETS_VERSION = "camel.jbang.kameletsVersion";

    @Metadata(description = "Quarkus Platform Maven groupId",
              javaType = "String", label = "quarkus")
    public static final String QUARKUS_GROUP_ID = "camel.jbang.quarkusGroupId";

    @Metadata(description = "Quarkus Platform Maven artifactId",
              javaType = "String", label = "quarkus")
    public static final String QUARKUS_ARTIFACT_ID = "camel.jbang.quarkusArtifactId";

    @Metadata(description = "Quarkus Platform version",
              javaType = "String", label = "quarkus")
    public static final String QUARKUS_VERSION = "camel.jbang.quarkusVersion";

    @Metadata(description = "Spring Boot version",
              javaType = "String", label = "spring-boot")
    public static final String SPRING_BOOT_VERSION = "camel.jbang.springBootVersion";

    @Metadata(description = "Include Maven Wrapper files in the exported project",
              javaType = "boolean", defaultValue = "true")
    public static final String MAVEN_WRAPPER = "camel.jbang.mavenWrapper";

    @Metadata(description = "Include Gradle Wrapper files in the exported project",
              javaType = "boolean", defaultValue = "true")
    public static final String GRADLE_WRAPPER = "camel.jbang.gradleWrapper";

    @Metadata(description = "Build tool to use (Maven or Gradle)",
              javaType = "String", defaultValue = "Maven")
    public static final String BUILD_TOOL = "camel.jbang.buildTool";

    @Metadata(description = "Directory where the project will be exported",
              javaType = "String", defaultValue = ".")
    public static final String EXPORT_DIR = "camel.jbang.exportDir";

    @Metadata(description = "File name of open-api spec file (JSON or YAML) to generate routes from the swagger/openapi API spec file.",
              javaType = "String")
    public static final String OPEN_API = "camel.jbang.openApi";

    @Metadata(description = "Additional Maven repositories for download on-demand (Use commas to separate multiple repositories)",
              javaType = "String", label = "maven")
    public static final String REPOS = "camel.jbang.repos";

    @Metadata(description = "Optional location of Maven settings.xml file to configure servers, repositories, mirrors, and proxies. If set to false, not even the default ~/.m2/settings.xml will be used.",
              javaType = "String", label = "maven")
    public static final String MAVEN_SETTINGS = "camel.jbang.maven-settings";

    @Metadata(description = "Optional location of Maven settings-security.xml file to decrypt Maven Settings (settings.xml) file",
              javaType = "String", label = "maven")
    public static final String MAVEN_SETTINGS_SECURITY = "camel.jbang.maven-settings-security";

    @Metadata(description = "Whether downloading JARs from Maven Central repository is enabled",
              javaType = "boolean", defaultValue = "true", label = "maven")
    public static final String MAVEN_CENTRAL_ENABLED = "camel.jbang.maven-central-enabled";

    @Metadata(description = "Whether downloading JARs from ASF Maven Snapshot repository is enabled",
              javaType = "boolean", defaultValue = "true", label = "maven")
    public static final String MAVEN_APACHE_SNAPSHOTS = "camel.jbang.maven-apache-snapshot-enabled";

    @Metadata(description = "Exclude files by name or pattern (Use commas to separate multiple files)",
              javaType = "String")
    public static final String EXCLUDES = "camel.jbang.excludes";

    @Metadata(description = "Additional dependencies (Use commas to separate multiple dependencies).",
              javaType = "String")
    public static final String DEPENDENCIES = "camel.jbang.dependencies";

    @Metadata(description = "Additional dependencies for Camel Main runtime only", javaType = "String")
    public static final String DEPENDENCIES_MAIN = "camel.jbang.dependencies.main";

    @Metadata(description = "Additional dependencies for Spring Boot runtime only", javaType = "String")
    public static final String DEPENDENCIES_SPRING_BOOT = "camel.jbang.dependencies.spring-boot";

    @Metadata(description = "Additional dependencies for Quarkus runtime only", javaType = "String")
    public static final String DEPENDENCIES_QUARKUS = "camel.jbang.dependencies.quarkus";

    @Metadata(description = "Version to use for jib-maven-plugin if exporting to camel-main and have Kubernetes enabled (jkube.xxx options)",
              javaType = "String", defaultValue = "3.4.5", label = "kubernetes")
    public static final String JIB_MAVEN_PLUGIN_VERSION = "camel.jbang.jib-maven-plugin-version";

    @Metadata(description = "Version to use for jkube-maven-plugin if exporting to camel-main and have Kubernetes enabled (jkube.xxx options)",
              javaType = "String", defaultValue = "1.19.0", label = "kubernetes")
    public static final String JKUBE_MAVEN_PLUGIN_VERSION = "camel.jbang.jkube-maven-plugin-version";

    @Metadata(description = "Stubs all the matching endpoint with the given component name or pattern. Multiple names can be separated by comma. (all = everything).",
              javaType = "String")
    public static final String STUB = "camel.jbang.stub";

    @Metadata(description = "Source directory for dynamically loading Camel file(s) to run. When using this, then files cannot be specified at the same time.",
              javaType = "String", label = "advanced")
    public static final String SOURCE_DIR = "camel.jbang.sourceDir";

    @Metadata(description = "Whether to ignore route loading and compilation errors (use this with care!)",
              javaType = "boolean", label = "advanced")
    public static final String IGNORE_LOADING_ERROR = "camel.jbang.ignoreLoadingError";

    @Metadata(description = "Whether to use lazy bean initialization (can help with complex classloading issues)",
              javaType = "boolean", label = "advanced")
    public static final String LAZY_BEAN = "camel.jbang.lazyBean";

    @Metadata(description = "Allow user to type in required parameters in prompt if not present in application",
              javaType = "boolean", label = "advanced")
    public static final String PROMPT = "camel.jbang.prompt";

    @Metadata(description = "Work directory for compiler. Can be used to write compiled classes or other resources.",
              javaType = "String", defaultValue = ".camel-jbang/compile", label = "advanced")
    public static final String COMPILE_WORK_DIR = "camel.jbang.compileWorkDir";

    @Deprecated
    @Metadata(description = "Health check at /observe/health on local HTTP server (port 8080 by default)",
              javaType = "boolean", defaultValue = ".camel-jbang/compile", deprecationNote = "Deprecated: use observe instead")
    public static final String HEALTH = "camel.jbang.health";

    @Deprecated
    @Metadata(description = "Metrics (Micrometer and Prometheus) at /observe/metrics on local HTTP server (port 8080 by default) when running standalone Camel",
              javaType = "boolean", defaultValue = ".camel-jbang/compile", deprecationNote = "Deprecated: use observe instead")
    public static final String METRICS = "camel.jbang.metrics";

    @Metadata(description = "Developer console at /q/dev on local HTTP server (port 8080 by default)",
              javaType = "boolean")
    public static final String CONSOLE = "camel.jbang.console";

    @Metadata(description = "Verbose output of startup activity (dependency resolution and downloading",
              javaType = "boolean")
    public static final String VERBOSE = "camel.jbang.verbose";

    @Metadata(description = "The version of Apache Camel to use",
              javaType = "String")
    public static final String CAMEL_VERSION = "camel.jbang.camel-version";

    @Metadata(description = "Enables Java Flight Recorder saving recording to disk on exit",
              javaType = "boolean")
    public static final String JFR = "camel.jbang.jfr";

    @Metadata(description = "Java Flight Recorder profile to use (such as default or profile)",
              javaType = "String", defaultValue = "default")
    public static final String JFR_PROFILE = "camel.jbang.jfr-profile";

    @Metadata(description = "Whether to allow automatic downloading JAR dependencies (over the internet)",
              javaType = "boolean", defaultValue = "true")
    public static final String DOWNLOAD = "camel.jbang.download";

    @Metadata(description = "Whether to automatic package scan JARs for custom Spring or Quarkus beans making them available for Camel JBang",
              javaType = "boolean", label = "advanced")
    public static final String PACKAGE_SCAN_JARS = "camel.jbang.packageScanJars";

    @Metadata(description = "To use a custom Camel version when running or export to Spring Boot",
              javaType = "String", label = "spring-boot")
    public static final String CAMEL_SPRING_BOOT_VERSION = "camel.jbang.camelSpringBootVersion";

    private CamelJBangConstants() {
    }

}
