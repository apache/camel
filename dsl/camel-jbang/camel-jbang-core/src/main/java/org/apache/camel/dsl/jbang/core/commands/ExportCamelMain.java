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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.util.CamelCaseOrderedProperties;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.io.FileUtils;

class ExportCamelMain extends Export {

    public ExportCamelMain(CamelJBangMain main) {
        super(main);
        pomTemplateName = "main-pom.tmpl";
    }

    @Override
    public Integer export() throws Exception {
        String[] ids = gav.split(":");
        if (ids.length != 3) {
            System.err.println("--gav must be in syntax: groupId:artifactId:version");
            return 1;
        }
        if (!buildTool.equals("maven") && !buildTool.equals("gradle")) {
            System.err.println("--build-tool must either be maven or gradle, was: " + buildTool);
            return 1;
        }
        if (buildTool.equals("gradle")) {
            System.err.println("--build-tool=gradle is not support yet for camel-main runtime.");
        }

        // the settings file has information what to export
        File settings = new File(CommandLineHelper.getWorkDir(), Run.RUN_SETTINGS_FILE);
        if (fresh || !files.isEmpty() || !settings.exists()) {
            // allow to automatic build
            if (!quiet && fresh) {
                printer().println("Generating fresh run data");
            }
            int silent = runSilently(ignoreLoadingError, lazyBean);
            if (silent != 0) {
                return silent;
            }
        } else {
            if (!quiet) {
                printer().println("Reusing existing run data");
            }
        }

        if (!quiet) {
            printer().println("Exporting as Camel Main project to: " + exportDir);
        }

        File profile = new File("application.properties");

        // use a temporary work dir
        File buildDir = new File(BUILD_DIR);
        FileUtil.removeDir(buildDir);
        buildDir.mkdirs();

        // gather dependencies
        Set<String> deps = resolveDependencies(settings, profile);

        // compute source folders
        File srcJavaDirRoot = new File(BUILD_DIR, "src/main/java");
        String srcPackageName = exportPackageName(ids[0], ids[1], packageName);
        File srcJavaDir;
        if (srcPackageName == null) {
            srcJavaDir = srcJavaDirRoot;
        } else {
            srcJavaDir = new File(srcJavaDirRoot, srcPackageName.replace('.', File.separatorChar));
        }
        srcJavaDir.mkdirs();
        File srcResourcesDir = new File(BUILD_DIR, "src/main/resources");
        srcResourcesDir.mkdirs();
        File srcCamelResourcesDir = new File(BUILD_DIR, "src/main/resources/camel");
        File srcKameletsResourcesDir = new File(BUILD_DIR, "src/main/resources/kamelets");
        // copy application properties files
        copyApplicationPropertiesFiles(srcResourcesDir);
        // copy source files
        copySourceFiles(settings, profile, srcJavaDirRoot, srcJavaDir, srcResourcesDir, srcCamelResourcesDir,
                srcKameletsResourcesDir, srcPackageName);
        // copy from settings to profile
        copySettingsAndProfile(settings, profile, srcResourcesDir, prop -> {
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
            if (port == -1 && deps.stream().anyMatch(d -> d.contains("camel-platform-http") || d.contains("camel-rest"))) {
                port = 8080;
            }
            if (port != -1 && !prop.containsKey("camel.server.enabled")) {
                prop.put("camel.server.enabled", "true");
                if (port != 8080 && !prop.containsKey("camel.server.port")) {
                    prop.put("camel.server.port", port);
                }
                if (!prop.containsKey("camel.server.health-check-enabled")) {
                    prop.put("camel.server.health-check-enabled", "true");
                }
            }
            return prop;
        });
        // create main class
        createMainClassSource(srcJavaDir, srcPackageName, mainClassname);
        // copy local lib JARs
        copyLocalLibDependencies(deps);
        // copy agent JARs and remove as dependency
        copyAgentDependencies(deps);
        deps.removeIf(d -> d.startsWith("agent:"));
        if ("maven".equals(buildTool)) {
            createMavenPom(settings, profile, new File(BUILD_DIR, "pom.xml"), deps, srcPackageName);
            if (mavenWrapper) {
                copyMavenWrapper();
            }
        }
        copyDockerFiles(BUILD_DIR);
        String appJar = "target" + File.separator + ids[1] + "-" + ids[2] + ".jar";
        copyReadme(BUILD_DIR, appJar);
        if (cleanExportDir || !exportDir.equals(".")) {
            // cleaning current dir can be a bit dangerous so only clean if explicit enabled
            // otherwise always clean export-dir to avoid stale data
            CommandHelper.cleanExportDir(exportDir);
        }
        // copy to export dir and remove work dir
        FileUtils.copyDirectory(new File(BUILD_DIR), new File(exportDir));
        FileUtil.removeDir(new File(BUILD_DIR));

        return 0;
    }

    private void createMavenPom(File settings, File profile, File pom, Set<String> deps, String packageName) throws Exception {
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

        IOHelper.writeText(context, new FileOutputStream(pom, false));
    }

    protected String enrichMavenPomJib(String context, File settings, File profile) throws Exception {
        StringBuilder sb1 = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();

        // is kubernetes included?
        Properties prop = new CamelCaseOrderedProperties();
        if (profile.exists()) {
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
                fromImage = "eclipse-temurin:" + javaVersion + "-jre";
                sb1.append(String.format("        <%s>%s</%s>%n", "jib.from.image", fromImage, "jib.from.image"));
            }

            InputStream is = ExportCamelMain.class.getClassLoader().getResourceAsStream("templates/main-docker-pom.tmpl");
            String context2 = IOHelper.loadText(is);
            IOHelper.close(is);

            context2 = context2.replaceFirst("\\{\\{ \\.JibMavenPluginVersion }}", jibMavenPluginVersion(settings, prop));

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
    protected Set<String> resolveDependencies(File settings, File profile) throws Exception {
        Set<String> answer = super.resolveDependencies(settings, profile);

        if (profile != null && profile.exists()) {
            Properties prop = new CamelCaseOrderedProperties();
            RuntimeUtil.loadProperties(prop, profile);
            // if metrics is defined then include camel-micrometer-prometheus for camel-main runtime
            if (prop.getProperty("camel.metrics.enabled") != null || prop.getProperty("camel.server.metricsEnabled") != null) {
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

    private void createMainClassSource(File srcJavaDir, String packageName, String mainClassname) throws Exception {
        InputStream is = ExportCamelMain.class.getClassLoader().getResourceAsStream("templates/main.tmpl");
        String context = IOHelper.loadText(is);
        IOHelper.close(is);

        if (packageName != null) {
            context = context.replaceFirst("\\{\\{ \\.PackageName }}", "package " + packageName + ";");
        } else {
            context = context.replaceFirst("\\{\\{ \\.PackageName }}", "");
        }
        context = context.replaceAll("\\{\\{ \\.MainClassname }}", mainClassname);
        IOHelper.writeText(context, new FileOutputStream(srcJavaDir + "/" + mainClassname + ".java", false));
    }

    @Override
    protected void copySourceFiles(
            File settings, File profile, File srcJavaDirRoot, File srcJavaDir, File srcResourcesDir, File srcCamelResourcesDir,
            File srcKameletsResourcesDir, String packageName)
            throws Exception {

        super.copySourceFiles(settings, profile, srcJavaDirRoot, srcJavaDir, srcResourcesDir, srcCamelResourcesDir,
                srcKameletsResourcesDir, packageName);

        // log4j configuration
        InputStream is = ExportCamelMain.class.getResourceAsStream("/log4j2.properties");
        safeCopy(is, new File(srcResourcesDir, "log4j2.properties"));
        is = ExportCamelMain.class.getResourceAsStream("/log4j2.component.properties");
        safeCopy(is, new File(srcResourcesDir, "log4j2.component.properties"));
        // assembly for runner jar
        is = ExportCamelMain.class.getResourceAsStream("/assembly/runner.xml");
        safeCopy(is, new File(srcResourcesDir, "assembly/runner.xml"));
    }
}
