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
package org.apache.camel.dsl.jbang.export;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.dsl.jbang.export.common.CamelHome;
import org.apache.camel.dsl.jbang.export.common.MavenResolver;
import org.apache.camel.dsl.jbang.export.common.QuarkusPlatform;
import org.apache.camel.dsl.jbang.export.common.RuntimeType;

/**
 * What to export: the Camel files and the options of the Maven project to create, the same as the options of camel
 * export.
 * <p>
 * The fields are public and mutable, as the request is filled in by the caller and then completed by
 * {@link ProjectExporter#prepare(ExportRequest)} with the values of application.properties and the profile. The copy
 * constructor is shallow: the copy shares the lists of the original.
 */
public class ExportRequest {

    /**
     * The base directory of the files to export (the current directory when not set).
     */
    public Path exportBaseDir;

    /**
     * The Camel file(s) to export.
     */
    public List<String> files = new ArrayList<>();

    /**
     * Add additional dependencies.
     */
    public List<String> dependencies = new ArrayList<>();

    /**
     * Runtime.
     */
    public RuntimeType runtime;

    /**
     * The integration name. Use this when the name should not get derived otherwise.
     */
    public String name;

    /**
     * Embeds a local HTTP server on this port.
     */
    public int port = -1;

    /**
     * To use a dedicated port for HTTP management.
     */
    public int managementPort = -1;

    /**
     * The Maven group:artifact:version.
     */
    public String gav;

    /**
     * The Maven parent POM group:artifact:version.
     */
    public String parentPom;

    /**
     * Exclude files by name or pattern.
     */
    public List<String> excludes = new ArrayList<>();

    /**
     * Optional location of Maven settings.xml file to configure servers, repositories, mirrors and proxies. If set to
     * \"false\", not even the default ~/.m2/settings.xml will be used.
     */
    public String mavenSettings;

    /**
     * Optional location of Maven settings-security.xml file to decrypt settings.xml.
     */
    public String mavenSettingsSecurity;

    /**
     * Whether downloading JARs from Maven Central repository is enabled.
     */
    public boolean mavenCentralEnabled = true;

    /**
     * Whether downloading JARs from ASF Maven Snapshot repository is enabled.
     */
    public boolean mavenApacheSnapshotEnabled = true;

    /**
     * The class name of the Camel Main application class.
     */
    public String mainClassname = "CamelApplication";

    /**
     * Java version.
     */
    public String javaVersion = "21";

    /**
     * To export using a different Camel version than the default version.
     */
    public String camelVersion;

    /**
     * Apache Camel Kamelets version (auto-detected from classpath if not set).
     */
    public String kameletsVersion;

    /**
     * Profile to export.
     */
    public String profile;

    /**
     * Local directory for loading Kamelets (takes precedence).
     */
    public String localKameletDir;

    /**
     * Spring Boot version.
     */
    public String springBootVersion = RuntimeType.SPRING_BOOT_VERSION;

    /**
     * Camel version to use with Spring Boot.
     */
    public String camelSpringBootVersion;

    /**
     * The Quarkus Platform to export to.
     */
    public QuarkusPlatform quarkusPlatform = new QuarkusPlatform();

    /**
     * Deprecated: Quarkus always uses fast-jar (layered) packaging.
     */
    @Deprecated
    public String quarkusPackageType = "fast-jar";

    /**
     * Include Maven Wrapper files in exported project.
     */
    public boolean mavenWrapper = true;

    /**
     * Include Camel Wrapper scripts in exported project for version pinning.
     */
    public boolean camelWrapper;

    /**
     * Include Docker files in exported project.
     */
    public boolean docker = true;

    /**
     * Adds an OpenAPI spec from the given file (json or yaml file).
     */
    public String openapi;

    /**
     * Enable observability services.
     */
    public boolean observe;

    /**
     * Set by camel run: the exported project is temporary and only used for that run, so the developer console is
     * enabled in its application.properties regardless of the profile.
     */
    public boolean consoleForRun;

    /**
     * Developer console on the local HTTP server (port 8080 by default): /q/dev with Camel Main, /actuator/camel with
     * Spring Boot, and /q/camel/dev-console with Quarkus. The exported project only has the console with the dev
     * profile.
     */
    public boolean console;

    /**
     * Directory where the project will be exported.
     */
    public String exportDir = ".";

    /**
     * The working directory of the export: the run settings of the dependency resolution and the project being built
     * are kept there, and the directory is deleted and created again on each export. The default is the .camel-jbang
     * directory in the current directory, as with camel export; use a directory of its own for each export that runs at
     * the same time.
     */
    public Path workDir = CamelHome.getWorkDir();

    /**
     * If exporting to current directory (default) then all existing files are preserved. Enabling this option will
     * force cleaning current directory including all sub dirs (use this with care).
     */
    public boolean cleanExportDir;

    /**
     * Automatically answer yes to confirmation prompts (e.g. when using --clean-dir).
     */
    public boolean yes;

    /**
     * Logging level.
     */
    public String loggingLevel = "info";

    /**
     * For Java source files should they have the given package name. By default the package name is computed from the
     * Maven GAV. Use false to turn off and not include package name in the Java source files.
     */
    public String packageName;

    /**
     * How Maven artifacts are downloaded.
     */
    public MavenResolver mavenResolver = new MavenResolver();

    /**
     * Whether to automatic package scan JARs for custom Spring or Quarkus beans making them available for Camel CLI.
     */
    public boolean packageScanJars;

    /**
     * Additional resource directories to include in src/main/resources (recursive, preserving structure).
     */
    public List<String> resourceDirs = new ArrayList<>();

    /**
     * Maven build properties, ex. --build-property=prop1=foo.
     */
    public List<String> buildProperties = new ArrayList<>();

    /**
     * Camel application properties, ex. --property=prop1=foo.
     */
    public String[] applicationProperties;

    /**
     * Can be used to turn on logging to console (logs by default to file in &lt;user home&gt;/.camel directory).
     */
    public boolean logging;

    /**
     * Will be quiet, only print when error occurs.
     */
    public boolean quiet;

    /**
     * Verbose output of startup activity (dependency resolution and downloading.
     */
    public boolean verbose;

    /**
     * Preview export without writing files.
     */
    public boolean dryRun;

    /**
     * Whether to ignore route loading and compilation errors (use this with care!).
     */
    public boolean ignoreLoadingError;

    /**
     * Whether to use lazy bean initialization (can help with complex classloading issues.
     */
    public boolean lazyBean = true;

    /**
     * Skip plugins during export.
     */
    public boolean skipPlugins;

    /**
     * Whether to include pre-compiled Groovy classes in the export (only supported with runtime=camel-main).
     */
    public boolean groovyPrecompiled;

    /**
     * Whether to include Hawtio web console (only available for exporting to Spring Boot or Quarkus).
     */
    public boolean hawtio;

    /**
     * Version of the Hawtio web console.
     */
    public String hawtioVersion;

    /**
     * Copy the source files using symbolic links.
     */
    public boolean symbolicLink;

    /**
     * Reload Java code in dev mode.
     */
    public boolean javaLiveReload;

    /**
     * The name of the pom template to use (for specialised pom templates), or null for the one of the runtime.
     */
    public String pomTemplateName;

    public ExportRequest() {
    }

    /**
     * A shallow copy of the given request.
     */
    public ExportRequest(ExportRequest other) {
        this.exportBaseDir = other.exportBaseDir;
        this.files = other.files;
        this.dependencies = other.dependencies;
        this.runtime = other.runtime;
        this.name = other.name;
        this.port = other.port;
        this.managementPort = other.managementPort;
        this.gav = other.gav;
        this.parentPom = other.parentPom;
        this.excludes = other.excludes;
        this.mavenSettings = other.mavenSettings;
        this.mavenSettingsSecurity = other.mavenSettingsSecurity;
        this.mavenCentralEnabled = other.mavenCentralEnabled;
        this.mavenApacheSnapshotEnabled = other.mavenApacheSnapshotEnabled;
        this.mainClassname = other.mainClassname;
        this.javaVersion = other.javaVersion;
        this.camelVersion = other.camelVersion;
        this.kameletsVersion = other.kameletsVersion;
        this.profile = other.profile;
        this.localKameletDir = other.localKameletDir;
        this.springBootVersion = other.springBootVersion;
        this.camelSpringBootVersion = other.camelSpringBootVersion;
        this.quarkusPlatform = other.quarkusPlatform;
        this.quarkusPackageType = other.quarkusPackageType;
        this.mavenWrapper = other.mavenWrapper;
        this.camelWrapper = other.camelWrapper;
        this.docker = other.docker;
        this.openapi = other.openapi;
        this.observe = other.observe;
        this.consoleForRun = other.consoleForRun;
        this.console = other.console;
        this.exportDir = other.exportDir;
        this.workDir = other.workDir;
        this.cleanExportDir = other.cleanExportDir;
        this.yes = other.yes;
        this.loggingLevel = other.loggingLevel;
        this.packageName = other.packageName;
        this.mavenResolver = other.mavenResolver;
        this.packageScanJars = other.packageScanJars;
        this.resourceDirs = other.resourceDirs;
        this.buildProperties = other.buildProperties;
        this.applicationProperties = other.applicationProperties;
        this.logging = other.logging;
        this.quiet = other.quiet;
        this.verbose = other.verbose;
        this.dryRun = other.dryRun;
        this.ignoreLoadingError = other.ignoreLoadingError;
        this.lazyBean = other.lazyBean;
        this.skipPlugins = other.skipPlugins;
        this.groovyPrecompiled = other.groovyPrecompiled;
        this.hawtio = other.hawtio;
        this.hawtioVersion = other.hawtioVersion;
        this.symbolicLink = other.symbolicLink;
        this.javaLiveReload = other.javaLiveReload;
        this.pomTemplateName = other.pomTemplateName;
    }
}
