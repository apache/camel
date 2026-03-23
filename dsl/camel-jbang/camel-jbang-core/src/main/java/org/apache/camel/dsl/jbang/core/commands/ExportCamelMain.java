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
package org.apache.camel.dsl.jbang.core.commands;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.dsl.jbang.core.common.TemplateHelper;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.util.CamelCaseOrderedProperties;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.dsl.jbang.core.commands.ExportHelper.exportPackageName;

class ExportCamelMain extends Export {

    protected static final String GROOVY_COMPILE_DIR = CommandLineHelper.CAMEL_JBANG_WORK_DIR + "/compile/groovy";

    public ExportCamelMain(CamelJBangMain main) {
        super(main);
        pomTemplateName = "main-pom.ftl";
    }

    @Override
    public Integer export() throws Exception {
        String[] ids = gav.split(":");
        if (ids.length != 3) {
            printer().printErr("--gav must be in syntax: groupId:artifactId:version");
            return 1;
        }

        // the settings file has information what to export
        Path settings = CommandLineHelper.getWorkDir().resolve(Run.RUN_SETTINGS_FILE);
        if (fresh || !files.isEmpty() || !Files.exists(settings)) {
            // allow to automatic build
            printer().println("Generating fresh run data");
            int silent = runSilently(ignoreLoadingError, lazyBean, verbose);
            if (silent != 0) {
                return silent;
            }
        } else {
            printer().println("Reusing existing run data");
        }

        printer().println("Exporting as Camel Main project to: " + exportDir);

        Path profile = exportBaseDir.resolve("application.properties");

        // use a temporary work dir
        Path buildDir = Path.of(BUILD_DIR);
        PathUtils.deleteDirectory(buildDir);
        Files.createDirectories(buildDir);

        // gather dependencies
        Set<String> deps = resolveDependencies(settings, profile);

        // compute source folders
        Path srcJavaDirRoot = buildDir.resolve("src/main/java");
        String srcPackageName = exportPackageName(ids[0], ids[1], packageName);
        Path srcJavaDir;
        if (srcPackageName == null) {
            srcJavaDir = srcJavaDirRoot;
        } else {
            srcJavaDir = srcJavaDirRoot.resolve(srcPackageName.replace('.', '/'));
        }
        Files.createDirectories(srcJavaDir);
        Path srcResourcesDir = buildDir.resolve("src/main/resources");
        Files.createDirectories(srcResourcesDir);
        Path srcCamelResourcesDir = buildDir.resolve("src/main/resources/camel");
        Path srcKameletsResourcesDir = buildDir.resolve("src/main/resources/kamelets");
        // copy application properties files
        copyApplicationPropertiesFiles(srcResourcesDir);
        // copy source files
        copySourceFiles(settings, profile,
                srcJavaDirRoot, srcJavaDir,
                srcResourcesDir, srcCamelResourcesDir,
                srcKameletsResourcesDir, srcPackageName);
        // copy from settings to profile
        copySettingsAndProfile(settings, profile,
                srcResourcesDir, prop -> {
                    if (groovyPrecompiled && !prop.containsKey("camel.main.groovyPreloadCompiled")) {
                        prop.put("camel.main.groovyPreloadCompiled", "true");
                    }
                    if (!prop.containsKey("camel.main.basePackageScan")
                            && !prop.containsKey("camel.main.base-package-scan")) {
                        // use dot as root package if no package are in use
                        prop.put("camel.main.basePackageScan", srcPackageName == null ? "." : srcPackageName);
                    }
                    if (!hasModeline(settings)) {
                        prop.remove("camel.main.modeline");
                    }
                    // are we using http then enable embedded HTTP server (if not explicit configured already)
                    int port = httpServerPort(settings);
                    if (port == -1
                            && deps.stream().anyMatch(d -> d.contains("camel-platform-http") || d.contains("camel-rest"))) {
                        port = 8080;
                    }
                    if (port != -1 && !prop.containsKey("camel.server.enabled")) {
                        prop.put("camel.server.enabled", "true");
                        if (port != 8080 && !prop.containsKey("camel.server.port")) {
                            prop.put("camel.server.port", port);
                        }
                        if (!prop.containsKey("camel.server.health-check-enabled")) {
                            if (VersionHelper.isGE(camelVersion, "4.14.0")) {
                                prop.put("camel.management.enabled", "true");
                                prop.put("camel.management.health-check-enabled", "true");
                            } else {
                                // old option name for Camel 4.13 and older
                                prop.put("camel.server.health-check-enabled", "true");
                            }
                        }
                    }
                    port = httpManagementPort(settings);
                    if (port != -1) {
                        prop.put("camel.management.enabled", "true");
                        prop.put("camel.management.port", port);
                    }
                    return prop;
                });
        // create main class
        createMainClassSource(srcJavaDir, srcPackageName, mainClassname);
        // copy local lib JARs
        copyLocalLibDependencies(deps);
        // copy local lib JARs
        if (groovyPrecompiled) {
            copyGroovyPrecompiled(srcResourcesDir);
        }
        // copy agent JARs and remove as dependency
        copyAgentDependencies(deps);
        deps.removeIf(d -> d.startsWith("agent:"));
        createMavenPom(settings, profile,
                buildDir.resolve("pom.xml"), deps, srcPackageName);
        if (mavenWrapper) {
            copyMavenWrapper();
        }
        copyDockerFiles(BUILD_DIR);
        String appJar = Paths.get("target", ids[1] + "-" + ids[2] + ".jar").toString();
        copyReadme(BUILD_DIR, appJar);
        if (cleanExportDir || !exportDir.equals(".")) {
            // cleaning current dir can be a bit dangerous so only clean if explicit enabled
            // otherwise always clean export-dir to avoid stale data
            if (cleanExportDir) {
                String absPath = Path.of(exportDir).toAbsolutePath().toString();
                if (!CommandHelper.confirmOperation("Are you sure you want to delete " + absPath + "?", yes)) {
                    return 1;
                }
            }
            CommandHelper.cleanExportDir(exportDir);
        }
        // copy to export dir and remove work dir
        PathUtils.copyDirectory(buildDir, Path.of(exportDir));
        PathUtils.deleteDirectory(buildDir);

        return 0;
    }

    private void createMavenPom(Path settings, Path profile, Path pom, Set<String> deps, String packageName) throws Exception {
        String[] ids = gav.split(":");

        CamelCatalog catalog = new DefaultCamelCatalog();
        if (ObjectHelper.isEmpty(camelVersion)) {
            camelVersion = catalog.getLoadedVersion();
        }
        if (ObjectHelper.isEmpty(camelVersion)) {
            camelVersion = VersionHelper.extractCamelVersion();
        }

        Properties prop = new CamelCaseOrderedProperties();
        RuntimeUtil.loadProperties(prop, settings);
        String repos = getMavenRepositories(settings, prop, camelVersion);

        // build the template data model
        Map<String, Object> model = new HashMap<>();
        model.put("GroupId", ids[0]);
        model.put("ArtifactId", ids[1]);
        model.put("Version", ids[2]);
        model.put("JavaVersion", javaVersion);
        model.put("CamelVersion", camelVersion);
        if (packageName != null) {
            model.put("MainClassname", packageName + "." + mainClassname);
        } else {
            model.put("MainClassname", mainClassname);
        }
        model.put("ProjectBuildOutputTimestamp", this.getBuildMavenProjectDate());
        model.put("BuildProperties", formatBuildProperties());
        model.put("Repositories", buildRepositoryList(repos));
        model.put("Dependencies", buildDependencyList(deps));

        // kubernetes/docker properties
        enrichKubernetesModel(model, settings, profile);

        String context = TemplateHelper.processTemplate(pomTemplateName, model);
        Files.writeString(pom, context);
    }

    private void enrichKubernetesModel(Map<String, Object> model, Path settings, Path profile) throws Exception {
        // is kubernetes included?
        Properties prop = new CamelCaseOrderedProperties();
        if (Files.exists(profile)) {
            RuntimeUtil.loadProperties(prop, profile);
        }
        boolean jib = prop.stringPropertyNames().stream().anyMatch(s -> s.startsWith("jib."));
        boolean jkube = prop.stringPropertyNames().stream().anyMatch(s -> s.startsWith("jkube."));

        List<Map<String, String>> kubernetesProperties = new ArrayList<>();
        if (jib || jkube) {
            String fromImage = null;
            for (String key : prop.stringPropertyNames()) {
                String value = prop.getProperty(key);
                if ("jib.from.image".equals(key)) {
                    fromImage = value;
                }
                boolean accept = key.startsWith("jkube.") || key.startsWith("jib.") || key.startsWith("label.");
                if (accept) {
                    kubernetesProperties.add(Map.of("key", key, "value", value));
                }
            }
            if (fromImage == null) {
                fromImage = "mirror.gcr.io/library/eclipse-temurin:" + javaVersion + "-jre";
                kubernetesProperties.add(Map.of("key", "jib.from.image", "value", fromImage));
            }
        }
        model.put("KubernetesProperties", kubernetesProperties);

        model.put("hasJib", jib || jkube);
        model.put("hasJkube", jkube);
        if (jib || jkube) {
            model.put("JibMavenPluginVersion", jibMavenPluginVersion(settings, prop));
            model.put("hasJibFromAuth",
                    prop.stringPropertyNames().stream().anyMatch(s -> s.startsWith("jib.from.auth.")));
            model.put("hasJibToAuth",
                    prop.stringPropertyNames().stream().anyMatch(s -> s.startsWith("jib.to.auth.")));
            int port = httpServerPort(settings);
            if (port == -1) {
                port = 8080;
            }
            model.put("Port", String.valueOf(port));
        }
        if (jkube) {
            model.put("JkubeMavenPluginVersion", jkubeMavenPluginVersion(settings, prop));
        }
    }

    @Override
    protected Set<String> resolveDependencies(Path settings, Path profile) throws Exception {
        Set<String> answer = super.resolveDependencies(settings, profile);

        // remove out of the box dependencies
        answer.removeIf(s -> s.contains("camel-core"));
        answer.removeIf(s -> s.contains("camel-main"));
        answer.removeIf(s -> s.contains("camel-health"));

        if (profile != null && Files.exists(profile)) {
            Properties prop = new CamelCaseOrderedProperties();
            RuntimeUtil.loadProperties(prop, profile);
            // if metrics is defined then include camel-micrometer-prometheus for camel-main runtime
            if (prop.getProperty("camel.metrics.enabled") != null
                    || prop.getProperty("camel.management.metricsEnabled") != null
                    || prop.getProperty("camel.server.metricsEnabled") != null) {
                answer.add("mvn:org.apache.camel:camel-micrometer-prometheus");
            }
            // if health-check is defined then include camel-health for camel-main runtime
            if (prop.getProperty("camel.management.healthCheckEnabled") != null) {
                answer.add("mvn:org.apache.camel:camel-health");
            }
        }

        boolean main = answer.stream().anyMatch(s -> s.contains("mvn:org.apache.camel:camel-platform-http-main"));
        if (hasOpenapi(answer) && !main) {
            // include http server if using openapi
            answer.add("mvn:org.apache.camel:camel-platform-http-main");
        }
        // if platform-http is included then we need to switch to use camel-platform-http-main as implementation
        if (!main && answer.stream().anyMatch(s -> s.contains("camel-platform-http"))) {
            answer.add("mvn:org.apache.camel:camel-platform-http-main");
            main = true;
        }
        if (main) {
            answer.removeIf(s -> s.contains("org.apache.camel:camel-platform-http:"));
            answer.removeIf(s -> s.contains("org.apache.camel:camel-platform-http-vertx:"));
        }

        return answer;
    }

    private void createMainClassSource(Path srcJavaDir, String packageName, String mainClassname) throws Exception {
        Map<String, Object> model = new HashMap<>();
        if (packageName != null) {
            model.put("PackageName", "package " + packageName + ";");
        }
        model.put("MainClassname", mainClassname);

        String content = TemplateHelper.processTemplate("main.ftl", model);
        Path outputFile = srcJavaDir.resolve(mainClassname + ".java");
        Files.writeString(outputFile, content);
    }

    @Override
    protected void copySourceFiles(
            Path settings, Path profile, Path srcJavaDirRoot, Path srcJavaDir, Path srcResourcesDir, Path srcCamelResourcesDir,
            Path srcKameletsResourcesDir, String packageName)
            throws Exception {

        super.copySourceFiles(settings, profile, srcJavaDirRoot, srcJavaDir, srcResourcesDir, srcCamelResourcesDir,
                srcKameletsResourcesDir, packageName);

        // log4j configuration
        InputStream is = ExportCamelMain.class.getResourceAsStream("/log4j2-main.properties");
        ExportHelper.safeCopy(is, srcResourcesDir.resolve("log4j2.properties"));
        is = ExportCamelMain.class.getResourceAsStream("/log4j2.component.properties");
        ExportHelper.safeCopy(is, srcResourcesDir.resolve("log4j2.component.properties"));
    }

    protected void copyGroovyPrecompiled(Path srcResourcesDir) throws Exception {
        // are there any pre-compiled groovy code
        File gc = new File(GROOVY_COMPILE_DIR);
        if (gc.exists() && gc.isDirectory()) {
            File[] files = gc.listFiles();
            if (files != null) {
                Path targetDir = srcResourcesDir.resolve("camel-groovy-compiled");
                for (File file : files) {
                    // skip anonymous scripts
                    if (file.getName().endsWith(".class") && !file.getName().startsWith("Script_")) {
                        Files.createDirectories(targetDir);
                        Path out = targetDir.resolve(file.getName());
                        ExportHelper.safeCopy(file.toPath(), out, true);
                    }
                }
            }
        }
    }

}
