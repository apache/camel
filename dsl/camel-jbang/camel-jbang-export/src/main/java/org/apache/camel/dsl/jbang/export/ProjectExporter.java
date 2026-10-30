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

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import org.apache.camel.dsl.jbang.export.common.CamelJBangConstants;
import org.apache.camel.dsl.jbang.export.common.ExportHelper;
import org.apache.camel.dsl.jbang.export.common.MavenResolver;
import org.apache.camel.dsl.jbang.export.common.Printer;
import org.apache.camel.dsl.jbang.export.common.PropertyResolver;
import org.apache.camel.dsl.jbang.export.common.QuarkusPlatform;
import org.apache.camel.dsl.jbang.export.common.RuntimeType;
import org.apache.camel.dsl.jbang.export.common.RuntimeUtil;
import org.apache.camel.dsl.jbang.export.common.SourceScheme;
import org.apache.camel.util.CamelCaseOrderedProperties;
import org.apache.camel.util.FileUtil;

import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.CAMEL_SPRING_BOOT_VERSION;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.CAMEL_VERSION;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.CAMEL_WRAPPER;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.EXCLUDES;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.EXPORT_DIR;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.GAV;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.JAVA_VERSION;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.KAMELETS_VERSION;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.LOCAL_KAMELET_DIR;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.MAVEN_APACHE_SNAPSHOTS;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.MAVEN_CENTRAL_ENABLED;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.MAVEN_SETTINGS;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.MAVEN_SETTINGS_SECURITY;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.MAVEN_WRAPPER;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.OPEN_API;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.PARENT_POM;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.SPRING_BOOT_VERSION;

/**
 * Exports Camel files to a Maven project of Camel Main, Spring Boot or Quarkus: the Java API of camel export.
 *
 * <pre>
 * ExportRequest request = new ExportRequest();
 * request.files.add("hello.camel.yaml");
 * request.runtime = RuntimeType.main;
 * request.exportDir = "target/hello";
 * int exit = new ProjectExporter().export(request);
 * </pre>
 *
 * @since 4.24
 */
public class ProjectExporter {

    private final ExportContext context;

    public ProjectExporter() {
        this(new ExportContext());
    }

    public ProjectExporter(ExportContext context) {
        this.context = context;
    }

    public ExportContext getContext() {
        return context;
    }

    /**
     * Exports the project: {@link #prepare(ExportRequest) prepares} the request, gives it a default GAV when it has
     * none, and {@link #export(Path, ExportRequest) exports} it.
     *
     * @param  request the request, which is completed by the preparation
     * @return         0 when the project was exported, or the exit code of the failure
     */
    public int export(ExportRequest request) throws Exception {
        Path baseDir = prepare(request);

        if (request.runtime == null) {
            printer().printErr("The runtime option must be specified");
            return 1;
        }

        if (request.gav == null) {
            String pn = projectName(request.name, request.gav, request.files);
            if (pn == null) {
                printer().printErr("Failed to resolve project name: Please provide --name, --gav or source file");
                return 1;
            }
            request.gav = "org.example.project:%s:%s".formatted(pn, projectVersion(request.gav));
        }

        return export(baseDir, request);
    }

    /**
     * Prepares the request: a single directory as the file to export is the directory of the files to export, the
     * application.properties (and the one of the profile) of that directory are included, and the options they (and the
     * system properties) configure are set on the request.
     *
     * @param  request the request, which is completed
     * @return         the base directory of the files to export
     */
    public Path prepare(ExportRequest request) throws Exception {
        Path baseDir = request.exportBaseDir != null ? request.exportBaseDir : Path.of(".");

        // special if user type: camel run . or camel run dirName
        if (request.files != null && request.files.size() == 1) {
            String name = FileUtil.stripTrailingSeparator(request.files.get(0));
            if (name.indexOf(':') == -1) {
                Path first = Path.of(name);
                if (Files.isDirectory(first)) {
                    baseDir = first;
                    ExportHelper.dirToFiles(name, request.files);
                }
            }
        }

        // auto-detect application.properties and include it
        {
            String name = baseDir.resolve("application.properties").toString();
            if (Files.exists(Paths.get(name)) && !request.files.contains(name)) {
                request.files.add(name);
            }
        }
        if (request.profile != null) {
            // need to include profile application properties if exists
            String name = baseDir.resolve("application-" + request.profile + ".properties").toString();
            if (Files.exists(Paths.get(name)) && !request.files.contains(name)) {
                request.files.add(name);
            }
        }

        // application.properties
        doLoadAndInitProfileProperties(request, baseDir.resolve("application.properties"));
        if (request.profile != null) {
            // override from profile specific configuration
            doLoadAndInitProfileProperties(request, baseDir.resolve("application-" + request.profile + ".properties"));
        }

        // property overrides from system properties for supported properties
        overrideFromSystemProperties(request);

        return baseDir;
    }

    /**
     * Exports the prepared request to the project of its runtime.
     *
     * @param  baseDir the base directory of the files to export
     * @param  request the prepared request, which must have a runtime and a GAV
     * @return         0 when the project was exported, or the exit code of the failure
     */
    public int export(Path baseDir, ExportRequest request) throws Exception {
        if (request.runtime == null) {
            printer().printErr("The runtime option must be specified");
            return 1;
        }

        try {
            verifyExportFiles(request.files);
        } catch (FileNotFoundException ex) {
            printer().println(ex.getMessage());
            return 1;
        }

        ExportRequest copy = new ExportRequest(request);
        copy.exportBaseDir = baseDir;
        Exporter exporter = createExporter(copy);
        if (exporter == null) {
            printer().printErr("Unknown runtime: " + request.runtime);
            return 1;
        }
        return exporter.export();
    }

    /**
     * The exporter of the runtime of the request, or null for a runtime that cannot be exported to.
     */
    public Exporter createExporter(ExportRequest request) {
        return switch (request.runtime) {
            case springBoot -> new SpringBootExporter(request, context);
            case quarkus -> new QuarkusExporter(request, context);
            case main -> new CamelMainExporter(request, context);
            default -> null;
        };
    }

    /**
     * The name of the project: the given name, else the artifactId of the GAV, else the name of the first file.
     */
    public static String projectName(String name, String gav, List<String> files) {
        if (name != null) {
            return name;
        }

        if (gav != null) {
            String[] ids = gav.split(":");
            if (ids.length > 1) {
                return ids[1]; // artifactId
            }
        }

        if (files != null && !files.isEmpty()) {
            return FileUtil.onlyName(SourceScheme.onlyName(files.get(0)));
        }

        return null;
    }

    /**
     * The version of the project: the version of the GAV, else 1.0-SNAPSHOT.
     */
    public static String projectVersion(String gav) {
        if (gav != null) {
            String[] ids = gav.split(":");
            if (ids.length > 2) {
                return ids[2]; // g:a:v version
            }
        }

        return "1.0-SNAPSHOT";
    }

    private Printer printer() {
        return context.getPrinter();
    }

    private static void verifyExportFiles(List<String> files) throws FileNotFoundException {
        for (var fn : files) {
            if (fn.indexOf(':') < 0 || fn.startsWith("file:")) {
                if (fn.startsWith("file:")) {
                    fn = fn.substring(5);
                    if (fn.startsWith("//")) {
                        fn = fn.substring(2);
                    }
                }
                if (fn.endsWith("/*")) {
                    fn = fn.substring(0, fn.length() - 2);
                }
                if (!Files.exists(Paths.get(fn))) {
                    throw new FileNotFoundException("Path does not exist: " + fn);
                }
            }
        }
    }

    private static void doLoadAndInitProfileProperties(ExportRequest request, Path path) throws Exception {
        if (Files.exists(path)) {
            Properties props = new CamelCaseOrderedProperties();
            RuntimeUtil.loadProperties(props, path);
            // read runtime and gav from profile if not configured
            String rt = props.getProperty(CamelJBangConstants.RUNTIME);
            if (rt != null) {
                request.runtime = RuntimeType.fromValue(rt).exportRuntime();
            }
            request.gav = props.getProperty(GAV, request.gav);
            request.parentPom = props.getProperty(PARENT_POM, request.parentPom);
            // allow configuring versions from profile
            request.javaVersion = props.getProperty(JAVA_VERSION, request.javaVersion);
            request.camelVersion = props.getProperty(CAMEL_VERSION, request.camelVersion);
            request.kameletsVersion = props.getProperty(KAMELETS_VERSION, request.kameletsVersion);
            request.localKameletDir = props.getProperty(LOCAL_KAMELET_DIR, request.localKameletDir);
            request.quarkusPlatform = QuarkusPlatform.of(props, request.quarkusPlatform);
            request.camelSpringBootVersion = props.getProperty(CAMEL_SPRING_BOOT_VERSION, request.camelSpringBootVersion);
            request.springBootVersion = props.getProperty(SPRING_BOOT_VERSION, request.springBootVersion);
            request.mavenWrapper
                    = "true".equals(props.getProperty(MAVEN_WRAPPER, request.mavenWrapper ? "true" : "false"));
            request.camelWrapper
                    = "true".equals(props.getProperty(CAMEL_WRAPPER, request.camelWrapper ? "true" : "false"));
            request.exportDir = props.getProperty(EXPORT_DIR, request.exportDir);
            request.openapi = props.getProperty(OPEN_API, request.openapi);
            request.mavenResolver = MavenResolver.of(props, request.mavenResolver);
            request.mavenSettings = props.getProperty(MAVEN_SETTINGS, request.mavenSettings);
            request.mavenSettingsSecurity = props.getProperty(MAVEN_SETTINGS_SECURITY, request.mavenSettingsSecurity);
            request.mavenCentralEnabled = "true"
                    .equals(props.getProperty(MAVEN_CENTRAL_ENABLED, request.mavenCentralEnabled ? "true" : "false"));
            request.mavenApacheSnapshotEnabled = "true".equals(props.getProperty(MAVEN_APACHE_SNAPSHOTS,
                    request.mavenApacheSnapshotEnabled ? "true" : "false"));
            request.excludes = RuntimeUtil.getCommaSeparatedPropertyAsList(props, EXCLUDES, request.excludes);
        }
    }

    /**
     * For supported fields prefer the values from system properties if they are defined.
     */
    private static void overrideFromSystemProperties(ExportRequest request) {
        request.quarkusPlatform = QuarkusPlatform.of(System.getProperties(), request.quarkusPlatform);
        request.camelSpringBootVersion
                = PropertyResolver.fromSystemProperty(CAMEL_SPRING_BOOT_VERSION, () -> request.camelSpringBootVersion);
    }
}
