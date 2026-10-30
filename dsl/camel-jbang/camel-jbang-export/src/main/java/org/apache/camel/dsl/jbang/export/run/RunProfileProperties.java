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
package org.apache.camel.dsl.jbang.export.run;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Consumer;

import org.apache.camel.dsl.jbang.export.common.MavenResolver;
import org.apache.camel.dsl.jbang.export.common.QuarkusPlatform;
import org.apache.camel.dsl.jbang.export.common.RuntimeUtil;
import org.apache.camel.util.CamelCaseOrderedProperties;

import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.CAMEL_VERSION;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.DATA_DIR;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.EXCLUDES;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.GAV;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.JAVA_VERSION;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.KAMELETS_VERSION;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.MAVEN_APACHE_SNAPSHOTS;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.MAVEN_CENTRAL_ENABLED;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.MAVEN_SETTINGS;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.MAVEN_SETTINGS_SECURITY;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.OPEN_API;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.PACKAGE_SCAN_JARS;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.SPRING_BOOT_VERSION;
import static org.apache.camel.dsl.jbang.export.common.CamelJBangConstants.STUB;

/**
 * The application properties of a run: application.properties of the files (or of the source directory), and the one of
 * the profile, which overrides it. They configure the run as its options do.
 *
 * @since 4.24
 */
public final class RunProfileProperties {

    private RunProfileProperties() {
    }

    /**
     * Loads application.properties and application-profile.properties.
     *
     * @param  baseDir     the directory of the files
     * @param  sourceDir   the source directory, which has the properties when given
     * @param  profile     the profile, null for none
     * @param  loadedFiles where to add the files that exist
     * @param  eachFile    takes the properties of each file that exists, in order (the profile last)
     * @return             the properties, the profile overriding application.properties; null when none of the files
     *                     exists
     */
    public static Properties load(
            Path baseDir, String sourceDir, String profile, Set<Path> loadedFiles, Consumer<Properties> eachFile)
            throws Exception {
        // application.properties
        Path profilePropertiesPath;
        if (sourceDir != null) {
            profilePropertiesPath = Paths.get(sourceDir).resolve("application.properties");
        } else {
            profilePropertiesPath = baseDir.resolve("application.properties");
        }
        // based application-profile.properties
        Properties answer = loadExisting(profilePropertiesPath, loadedFiles, eachFile);

        if (profile != null) {
            if (sourceDir != null) {
                profilePropertiesPath = Paths.get(sourceDir).resolve("application-" + profile + ".properties");
            } else {
                profilePropertiesPath = Paths.get("application-" + profile + ".properties");
            }
            // application-profile.properties should override standard application.properties
            Properties override = loadExisting(profilePropertiesPath, loadedFiles, eachFile);
            if (override != null) {
                if (answer == null) {
                    answer = override;
                } else {
                    answer.putAll(override);
                }
            }
        }
        return answer;
    }

    private static Properties loadExisting(Path file, Set<Path> loadedFiles, Consumer<Properties> eachFile)
            throws Exception {
        Properties answer = null;
        if (Files.exists(file)) {
            loadedFiles.add(file.toAbsolutePath().normalize());
            answer = loadFile(file);
            eachFile.accept(answer);
        }
        return answer;
    }

    /**
     * Loads a properties file of a run; a route of camel.main.routesIncludePattern without a scheme is a file.
     */
    public static Properties loadFile(Path file) throws Exception {
        Properties prop = new CamelCaseOrderedProperties();
        if (Files.exists(file)) {
            try (InputStream is = Files.newInputStream(file)) {
                prop.load(is);
            }
        }

        // special for routes include pattern that we need to "fix" after reading from properties
        // to make this work in run command
        String value = prop.getProperty("camel.main.routesIncludePattern");
        if (value != null) {
            // if not scheme then must use file: as this is what run command expects
            StringJoiner sj = new StringJoiner(",");
            for (String part : value.split(",")) {
                if (!part.contains(":")) {
                    part = "file:" + part;
                }
                sj.add(part);
            }
            value = sj.toString();
            prop.setProperty("camel.main.routesIncludePattern", value);
        }

        return prop;
    }

    /**
     * Configures the request with what the properties of a file set, as camel run does with its options.
     */
    public static void applyTo(RunRequest request, Properties properties) {
        request.mavenResolver = MavenResolver.of(properties, request.mavenResolver);
        request.mavenSettings = properties.getProperty(MAVEN_SETTINGS, request.mavenSettings);
        request.mavenSettingsSecurity = properties.getProperty(MAVEN_SETTINGS_SECURITY, request.mavenSettingsSecurity);
        request.mavenCentralEnabled = "true"
                .equals(properties.getProperty(MAVEN_CENTRAL_ENABLED, request.mavenCentralEnabled ? "true" : "false"));
        request.mavenApacheSnapshotEnabled = "true".equals(properties.getProperty(MAVEN_APACHE_SNAPSHOTS,
                request.mavenApacheSnapshotEnabled ? "true" : "false"));
        request.openapi = properties.getProperty(OPEN_API, request.openapi);
        request.packageScanJars
                = "true".equals(properties.getProperty(PACKAGE_SCAN_JARS, request.packageScanJars ? "true" : "false"));
        request.camelVersion = properties.getProperty(CAMEL_VERSION, request.camelVersion);
        request.kameletsVersion = properties.getProperty(KAMELETS_VERSION, request.kameletsVersion);
        request.springBootVersion = properties.getProperty(SPRING_BOOT_VERSION, request.springBootVersion);
        request.javaVersion = properties.getProperty(JAVA_VERSION, request.javaVersion);
        request.quarkusPlatform = QuarkusPlatform.of(properties,
                request.quarkusPlatform != null ? request.quarkusPlatform : new QuarkusPlatform());
        request.gav = properties.getProperty(GAV, request.gav);
        request.stub = properties.getProperty(STUB, request.stub);
        request.dataDir = properties.getProperty(DATA_DIR, request.dataDir);
        request.excludes = RuntimeUtil.getCommaSeparatedPropertyAsList(properties, EXCLUDES, request.excludes);
    }
}
