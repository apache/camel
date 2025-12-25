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
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.util.CamelCaseOrderedProperties;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.dsl.jbang.core.commands.ExportHelper.exportPackageName;

class ExportCamelMain extends Export {

    protected static final String GROOVY_COMPILE_DIR = CommandLineHelper.CAMEL_JBANG_WORK_DIR + "/compile/groovy";

    public ExportCamelMain(CamelJBangMain main) {
        super(main);
        pomTemplateName = "main-pom.tmpl";
    }

    @Override
    public Integer export() throws Exception {
        String[] ids = gav.split(":");
        if (ids.length != 3) {
            printer().printErr("--gav must be in syntax: groupId:artifactId:version");
            return 1;
        }
        if (!buildTool.equals("maven") && !buildTool.equals("gradle")) {
            printer().printErr("--build-tool must either be maven or gradle, was: " + buildTool);
            return 1;
        }
        if (buildTool.equals("gradle")) {
            printer().printErr("--build-tool=gradle is not support yet for camel-main runtime.");
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
        if ("maven".equals(buildTool)) {
            createMavenPom(settings, profile,
                    buildDir.resolve("pom.xml"), deps, srcPackageName);
            if (mavenWrapper) {
                copyMavenWrapper();
            }
        }
        copyDockerFiles(BUILD_DIR);
        String appJar = Paths.get("target", ids[1] + "-" + ids[2] + ".jar").toString();
        copyReadme(BUILD_DIR, appJar);
        if (cleanExportDir || !exportDir.equals(".")) {
            // cleaning current dir can be a bit dangerous so only clean if explicit enabled
            // otherwise always clean export-dir to avoid stale data
            CommandHelper.cleanExportDir(exportDir);
        }
        // copy to export dir and remove work dir
        PathUtils.copyDirectory(buildDir, Path.of(exportDir));
        PathUtils.deleteDirectory(buildDir);

        return 0;
    }

    private void createMavenPom(Path settings, Path profile, Path pom, Set<String> deps, String packageName) throws Exception {
        String[] ids = gav.split(":");

        InputStream is = ExportCamelMain.class.getClassLoader().getResourceAsStream("templates/" + pomTemplateName);
        String context = IOHelper.loadText(is);
        IOHelper.close(is);

        CamelCatalog catalog = new DefaultCamelCatalog();
        if (ObjectHelper.isEmpty(camelVersion)) {
            camelVersion = catalog.getLoadedVersion();
        }
        if (ObjectHelper.isEmpty(camelVersion)) {
            camelVersion = VersionHelper.extractCamelVersion();
        }

        context = context.replaceAll("\\{\\{ \\.GroupId }}", ids[0]);
        context = context.replaceAll("\\{\\{ \\.ArtifactId }}", ids[1]);
        context = context.replaceAll("\\{\\{ \\.Version }}", ids[2]);
        context = context.replaceAll("\\{\\{ \\.JavaVersion }}", javaVersion);
        context = context.replaceAll("\\{\\{ \\.CamelVersion }}", camelVersion);
        if (packageName != null) {
            context = context.replaceAll("\\{\\{ \\.MainClassname }}", packageName + "." + mainClassname);
        } else {
            context = context.replaceAll("\\{\\{ \\.MainClassname }}", mainClassname);
        }
        context = context.replaceFirst("\\{\\{ \\.ProjectBuildOutputTimestamp }}", this.getBuildMavenProjectDate());

        Properties prop = new CamelCaseOrderedProperties();
        RuntimeUtil.loadProperties(prop, settings);
        String repos = getMavenRepositories(settings, prop, camelVersion);

        context = replaceBuildProperties(context);

        if (repos == null || repos.isEmpty()) {
            context = context.replaceFirst("\\{\\{ \\.MavenRepositories }}", "");
        } else {
            String s = mavenRepositoriesAsPomXml(repos);
            context = context.replaceFirst("\\{\\{ \\.MavenRepositories }}", s);
        }

        List<MavenGav> gavs = new ArrayList<>();
        for (String dep : deps) {
            MavenGav gav = parseMavenGav(dep);
            String gid = gav.getGroupId();
            if ("org.apache.camel".equals(gid)) {
                // uses BOM so version should not be included
                gav.setVersion(null);
            }
            gavs.add(gav);
        }

        // sort artifacts
        gavs.sort(mavenGavComparator());

        StringBuilder sb = new StringBuilder();
        for (MavenGav gav : gavs) {
            sb.append("        <dependency>\n");
            sb.append("            <groupId>").append(gav.getGroupId()).append("</groupId>\n");
            sb.append("            <artifactId>").append(gav.getArtifactId()).append("</artifactId>\n");
            if (gav.getVersion() != null) {
                sb.append("            <version>").append(gav.getVersion()).append("</version>\n");
            }
            if (gav.getScope() != null) {
                sb.append("            <scope>").append(gav.getScope()).append("</scope>\n");
            }
            // special for lib JARs
            if ("lib".equals(gav.getPackaging())) {
                sb.append("            <scope>system</scope>\n");
                sb.append("            <systemPath>\\$\\{project.basedir}/lib/").append(gav.getArtifactId()).append("-")
                        .append(gav.getVersion()).append(".jar</systemPath>\n");
            }
            if ("camel-kamelets-utils".equals(gav.getArtifactId())) {
                // special for camel-kamelets-utils
                sb.append("            <exclusions>\n");
                sb.append("                <exclusion>\n");
                sb.append("                    <groupId>org.apache.camel</groupId>\n");
                sb.append("                    <artifactId>*</artifactId>\n");
                sb.append("                </exclusion>\n");
                sb.append("            </exclusions>\n");
            }
            sb.append("        </dependency>\n");
        }

        context = context.replaceFirst("\\{\\{ \\.CamelDependencies }}", sb.toString());

        // include docker/kubernetes with jib/jkube
        context = enrichMavenPomJib(context, settings, profile);

        Files.writeString(pom, context);
    }

    protected String enrichMavenPomJib(String context, Path settings, Path profile) throws Exception {
        StringBuilder sb1 = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();

        // is kubernetes included?
        Properties prop = new CamelCaseOrderedProperties();
        if (Files.exists(profile)) {
            RuntimeUtil.loadProperties(prop, profile);
        }
        // include additional build properties
        boolean jib = prop.stringPropertyNames().stream().anyMatch(s -> s.startsWith("jib."));
        boolean jkube = prop.stringPropertyNames().stream().anyMatch(s -> s.startsWith("jkube."));
        // jib is used for docker and kubernetes, jkube is only used for kubernetes
        if (jib || jkube) {
            // include all jib/jkube/label properties
            String fromImage = null;
            for (String key : prop.stringPropertyNames()) {
                String value = prop.getProperty(key);
                if ("jib.from.image".equals(key)) {
                    fromImage = value;
                }
                boolean accept = key.startsWith("jkube.") || key.startsWith("jib.") || key.startsWith("label.");
                if (accept) {
                    sb1.append(String.format("        <%s>%s</%s>%n", key, value, key));
                }
            }
            // from image is mandatory so use a default image if none provided
            if (fromImage == null) {
                fromImage = "mirror.gcr.io/library/eclipse-temurin:" + javaVersion + "-jre";
                sb1.append(String.format("        <%s>%s</%s>%n", "jib.from.image", fromImage, "jib.from.image"));
            }

            InputStream is = ExportCamelMain.class.getClassLoader().getResourceAsStream("templates/main-docker-pom.tmpl");
            String context2 = IOHelper.loadText(is);
            IOHelper.close(is);

            context2 = context2.replaceFirst("\\{\\{ \\.JibMavenPluginVersion }}",
                    jibMavenPluginVersion(settings, prop));

            // image from/to auth
            String auth = "";
            if (prop.stringPropertyNames().stream().anyMatch(s -> s.startsWith("jib.from.auth."))) {
                is = ExportCamelMain.class.getClassLoader().getResourceAsStream("templates/main-docker-from-auth-pom.tmpl");
                auth = IOHelper.loadText(is);
                IOHelper.close(is);
            }
            context2 = context2.replace("{{ .JibFromImageAuth }}", auth);
            auth = "";
            if (prop.stringPropertyNames().stream().anyMatch(s -> s.startsWith("jib.to.auth."))) {
                is = ExportCamelMain.class.getClassLoader().getResourceAsStream("templates/main-docker-to-auth-pom.tmpl");
                auth = IOHelper.loadText(is);
                IOHelper.close(is);
            }
            context2 = context2.replace("{{ .JibToImageAuth }}", auth);
            // http port setting
            int port = httpServerPort(settings);
            if (port == -1) {
                port = 8080;
            }
            context2 = context2.replaceFirst("\\{\\{ \\.Port }}", String.valueOf(port));
            sb2.append(context2);
            // jkube is only used for kubernetes
            if (jkube) {
                is = ExportCamelMain.class.getClassLoader().getResourceAsStream("templates/main-jkube-pom.tmpl");
                String context3 = IOHelper.loadText(is);
                IOHelper.close(is);
                context3 = context3.replaceFirst("\\{\\{ \\.JkubeMavenPluginVersion }}",
                        jkubeMavenPluginVersion(settings, prop));
                sb2.append(context3);
            }
        }

        // remove empty lines
        String s1 = sb1.toString().replaceAll("(\\r?\\n){2,}", "\n");
        String s2 = sb2.toString().replaceAll("(\\r?\\n){2,}", "\n");

        context = context.replace("{{ .CamelKubernetesProperties }}", s1);
        context = context.replace("{{ .CamelKubernetesPlugins }}", s2);
        return context;
    }

    @Override
    protected Set<String> resolveDependencies(Path settings, Path profile) throws Exception {
        Set<String> answer = super.resolveDependencies(settings, profile);

        if (profile != null && Files.exists(profile)) {
            Properties prop = new CamelCaseOrderedProperties();
            RuntimeUtil.loadProperties(prop, profile);
            // if metrics is defined then include camel-micrometer-prometheus for camel-main runtime
            if (prop.getProperty("camel.metrics.enabled") != null
                    || prop.getProperty("camel.management.metricsEnabled") != null
                    || prop.getProperty("camel.server.metricsEnabled") != null) {
                answer.add("mvn:org.apache.camel:camel-micrometer-prometheus");
            }
        }

        // remove out of the box dependencies
        answer.removeIf(s -> s.contains("camel-core"));
        answer.removeIf(s -> s.contains("camel-main"));
        answer.removeIf(s -> s.contains("camel-health"));

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
        InputStream is = ExportCamelMain.class.getClassLoader().getResourceAsStream("templates/main.tmpl");
        String context = IOHelper.loadText(is);
        IOHelper.close(is);

        if (packageName != null) {
            context = context.replaceFirst("\\{\\{ \\.PackageName }}", "package " + packageName + ";");
        } else {
            context = context.replaceFirst("\\{\\{ \\.PackageName }}", "");
        }
        context = context.replaceAll("\\{\\{ \\.MainClassname }}", mainClassname);
        Path outputFile = srcJavaDir.resolve(mainClassname + ".java");
        Files.writeString(outputFile, context);
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
        // assembly for runner jar
        is = ExportCamelMain.class.getResourceAsStream("/assembly/runner.xml");
        ExportHelper.safeCopy(is, srcResourcesDir.resolve("assembly/runner.xml"));
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
