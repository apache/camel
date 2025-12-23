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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.common.CatalogLoader;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.tooling.model.ArtifactModel;
import org.apache.camel.util.CamelCaseOrderedProperties;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;

import static org.apache.camel.dsl.jbang.core.commands.ExportHelper.exportPackageName;
import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.*;

class ExportQuarkus extends Export {

    public ExportQuarkus(CamelJBangMain main) {
        super(main);
        pomTemplateName = "quarkus-pom.tmpl";
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

        exportBaseDir = exportBaseDir != null ? exportBaseDir : Path.of(".");
        Path profile = exportBaseDir.resolve("application.properties");

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

        printer().println("Exporting as Quarkus project to: " + exportDir);

        // use a temporary work dir
        Path buildDir = Path.of(BUILD_DIR);
        PathUtils.deleteDirectory(buildDir);
        Files.createDirectories(buildDir);

        Path srcJavaDirRoot = buildDir.resolve("src/main/java");
        String srcPackageName = exportPackageName(ids[0], ids[1], packageName);
        Path srcJavaDir;
        if (srcPackageName == null) {
            srcJavaDir = srcJavaDirRoot;
        } else {
            srcJavaDir = srcJavaDirRoot.resolve(srcPackageName.replace('.', File.separatorChar));
        }
        Files.createDirectories(srcJavaDir);
        Path srcResourcesDir = buildDir.resolve("src/main/resources");
        Files.createDirectories(srcResourcesDir);
        Path srcCamelResourcesDir = buildDir.resolve("src/main/resources/camel");
        Path srcKameletsResourcesDir = buildDir.resolve("src/main/resources/kamelets");
        // copy source files
        copySourceFiles(settings, profile, srcJavaDirRoot, srcJavaDir,
                srcResourcesDir, srcCamelResourcesDir,
                srcKameletsResourcesDir, srcPackageName);
        // copy from settings to profile
        copySettingsAndProfile(settings, profile, srcResourcesDir, prop -> {
            if (!hasModeline(settings)) {
                prop.remove("camel.main.modeline");
            }
            // are we using http then enable embedded HTTP server (if not explicit configured already)
            if (!prop.containsKey("quarkus.http.port")) {
                int port = httpServerPort(settings);
                if (port == -1) {
                    port = 8080;
                }
                if (port != 8080) {
                    prop.put("quarkus.http.port", port);
                }
            }
            if (!prop.containsKey("quarkus.management.port")) {
                port = httpManagementPort(settings);
                if (port != -1) {
                    prop.put("quarkus.management.port", port);
                }
            }
            if (hawtio) {
                prop.setProperty("quarkus.hawtio.authenticationEnabled", "false");
            }
            return prop;
        });
        // copy docker files
        copyDockerFiles(BUILD_DIR);
        String appJar = "target" + File.separator + "quarkus-app" + File.separator + "quarkus-run.jar";
        copyReadme(BUILD_DIR, appJar);
        // gather dependencies
        Set<String> deps = resolveDependencies(settings, profile);
        // copy local lib JARs
        copyLocalLibDependencies(deps);
        if ("maven".equals(buildTool)) {
            createMavenPom(settings, buildDir.resolve("pom.xml"), deps);
            if (mavenWrapper) {
                copyMavenWrapper();
            }
        } else if ("gradle".equals(buildTool)) {
            createGradleProperties(buildDir.resolve("gradle.properties"));
            createSettingsGradle(buildDir.resolve("settings.gradle"));
            createBuildGradle(settings, buildDir.resolve("build.gradle"), deps);
            if (gradleWrapper) {
                copyGradleWrapper();
            }
        }

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

    @Override
    protected void prepareApplicationProperties(Properties properties) {
        super.prepareApplicationProperties(properties);
        // quarkus native compilation only works if we specify each resource explicit

        StringJoiner sj = new StringJoiner(",");
        StringJoiner sj2 = new StringJoiner(",");
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String k = entry.getKey().toString();
            String v = entry.getValue().toString();

            if ("camel.main.routesIncludePattern".equals(k)) {
                v = Arrays.stream(v.split(","))
                        .filter(d -> !d.endsWith(".java")) // skip .java as they are in the src/main/java folder
                        .map(ExportQuarkus::stripPath) // remove scheme and routes are in camel sub-folder
                        .map(s -> {
                            if (s.endsWith("kamelet.yaml")) {
                                return "kamelets/" + s;
                            } else {
                                return "camel/" + s;
                            }
                        })
                        .collect(Collectors.joining(","));
                sj.add(v);
            }
            // extra classpath files
            if (CLASSPATH_FILES.equals(k)) {
                v = Arrays.stream(v.split(","))
                        .filter(d -> !d.endsWith(".jar")) // skip local lib JARs
                        .map(ExportQuarkus::stripPath) // remove scheme
                        .collect(Collectors.joining(","));
                sj2.add(v);
            }
        }

        String routes = sj.length() > 0 ? sj.toString() : null;
        String extra = sj2.length() > 0 ? sj2.toString() : null;

        if (routes != null || extra != null) {
            sj = new StringJoiner(",");
            String e = properties.getProperty("quarkus.native.resources.includes");
            if (e != null) {
                sj.add(e);
            }
            if (routes != null) {
                sj.add(routes);
            }
            if (extra != null) {
                sj.add(extra);
            }
            if (extra != null || VersionHelper.isLE(quarkusVersion, "3.21.0")) {
                // quarkus 3.21 or older need to have quarkus.native.resources.includes configured
                if (sj.length() > 0) {
                    properties.setProperty("quarkus.native.resources.includes", sj.toString());
                }
            }
        }

        // CAMEL-20911 workaround due to a bug in CEQ 3.11 and 3.12
        if (VersionHelper.isBetween(quarkusVersion, "3.11.0", "3.13.0")) {
            if (!properties.containsKey("quarkus.camel.openapi.codegen.model-package")) {
                properties.put("quarkus.camel.openapi.codegen.model-package", "org.apache.camel.quarkus");
            }
        }
    }

    private static String stripPath(String fileName) {
        if (fileName.contains(":")) {
            fileName = StringHelper.after(fileName, ":");
        }
        fileName = FileUtil.stripPath(fileName);
        fileName = fileName.replace(CommandLineHelper.CAMEL_JBANG_WORK_DIR + "/", "");
        return fileName;
    }

    private void createGradleProperties(Path output) throws Exception {
        InputStream is = ExportQuarkus.class.getClassLoader().getResourceAsStream("templates/quarkus-gradle-properties.tmpl");
        String context = IOHelper.loadText(is);
        IOHelper.close(is);

        context = context.replaceFirst("\\{\\{ \\.QuarkusGroupId }}", quarkusGroupId);
        context = context.replaceFirst("\\{\\{ \\.QuarkusArtifactId }}", quarkusArtifactId);
        context = context.replaceAll("\\{\\{ \\.QuarkusVersion }}", quarkusVersion);

        Files.writeString(output, context);
    }

    private void createSettingsGradle(Path output) throws Exception {
        String[] ids = gav.split(":");

        InputStream is = ExportQuarkus.class.getClassLoader().getResourceAsStream("templates/quarkus-settings-gradle.tmpl");
        String context = IOHelper.loadText(is);
        IOHelper.close(is);

        context = context.replaceFirst("\\{\\{ \\.GroupId }}", ids[0]);
        context = context.replaceFirst("\\{\\{ \\.ArtifactId }}", ids[1]);
        context = context.replaceFirst("\\{\\{ \\.Version }}", ids[2]);

        Files.writeString(output, context);
    }

    private void createBuildGradle(Path settings, Path gradleBuild, Set<String> deps) throws Exception {
        String[] ids = gav.split(":");

        InputStream is = ExportSpringBoot.class.getClassLoader().getResourceAsStream("templates/quarkus-build-gradle.tmpl");
        String context = IOHelper.loadText(is);
        IOHelper.close(is);

        Properties prop = new CamelCaseOrderedProperties();
        RuntimeUtil.loadProperties(prop, settings.toFile());
        // quarkus controls the camel version
        String repos = getMavenRepositories(settings, prop, quarkusVersion);

        CamelCatalog catalog = CatalogLoader.loadQuarkusCatalog(repos, quarkusVersion, quarkusGroupId, download);
        if (camelVersion == null) {
            camelVersion = catalog.getCatalogVersion();
        }

        context = context.replaceFirst("\\{\\{ \\.GroupId }}", ids[0]);
        context = context.replaceFirst("\\{\\{ \\.ArtifactId }}", ids[1]);
        context = context.replaceFirst("\\{\\{ \\.Version }}", ids[2]);
        context = context.replaceFirst("\\{\\{ \\.QuarkusGroupId }}", quarkusGroupId);
        context = context.replaceFirst("\\{\\{ \\.QuarkusArtifactId }}", quarkusArtifactId);
        context = context.replaceAll("\\{\\{ \\.QuarkusVersion }}", quarkusVersion);
        context = context.replaceAll("\\{\\{ \\.JavaVersion }}", javaVersion);
        context = context.replaceAll("\\{\\{ \\.CamelVersion }}", camelVersion);

        if (repos == null || repos.isEmpty()) {
            context = context.replaceFirst("\\{\\{ \\.MavenRepositories }}", "");
        } else {
            StringBuilder sb = new StringBuilder();
            for (String repo : repos.split(",")) {
                sb.append("    maven {\n");
                sb.append("        url '").append(repo).append("'\n");
                if (repo.contains("snapshots")) {
                    sb.append("        mavenContent {\n");
                    sb.append("            snapshotsOnly()\n");
                    sb.append("        }\n");
                }
                sb.append("    }\n");
            }
            context = context.replaceFirst("\\{\\{ \\.MavenRepositories }}", sb.toString());
        }

        List<MavenGav> gavs = new ArrayList<>();
        for (String dep : deps) {
            MavenGav gav = parseMavenGav(dep);
            String gid = gav.getGroupId();
            String aid = gav.getArtifactId();
            // transform to camel-quarkus extension GAV
            if ("org.apache.camel".equals(gid)) {
                String qaid = aid.replace("camel-", "camel-quarkus-");
                ArtifactModel<?> am = catalog.modelFromMavenGAV("org.apache.camel.quarkus", qaid, null);
                if (am != null) {
                    // use quarkus extension
                    gav.setGroupId(am.getGroupId());
                    gav.setArtifactId(am.getArtifactId());
                    gav.setVersion(null); // uses BOM so version should not be included
                } else {
                    // there is no quarkus extension so use plain camel
                    gav.setVersion(camelVersion);
                }
            }
            gavs.add(gav);
        }

        // replace dependencies with special quarkus dependencies if we can find any
        replaceQuarkusDependencies(gavs);

        // sort artifacts
        gavs.sort(mavenGavComparator());

        StringBuilder sb = new StringBuilder();
        for (MavenGav gav : gavs) {
            if ("lib".equals(gav.getPackaging())) {
                // special for lib JARs
                sb.append("    implementation files('lib/").append(gav.getArtifactId())
                        .append("-").append(gav.getVersion()).append(".jar')\n");
            } else if ("camel-kamelets-utils".equals(gav.getArtifactId())) {
                // special for camel-kamelets-utils
                sb.append("    implementation ('").append(gav).append("') {\n");
                sb.append("        exclude group: 'org.apache.camel', module: '*'\n");
                sb.append("    }\n");
            } else {
                sb.append("    implementation '").append(gav).append("'\n");
            }
        }
        context = context.replaceFirst("\\{\\{ \\.CamelDependencies }}", sb.toString());

        Files.writeString(gradleBuild, context);
    }

    private void replaceQuarkusDependencies(List<MavenGav> gavs) {
        // load information about dependencies that should be replaced
        Map<MavenGav, MavenGav> replace = new HashMap<>();
        try {
            InputStream is = ExportQuarkus.class.getClassLoader().getResourceAsStream("quarkus-dependencies.properties");
            if (is != null) {
                Properties prop = new Properties();
                prop.load(is);
                for (String k : prop.stringPropertyNames()) {
                    String v = prop.getProperty(k);
                    MavenGav from = parseMavenGav(k);
                    MavenGav to = parseMavenGav(v);
                    if (from != null && to != null) {
                        replace.put(from, to);
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }

        // find and replace dependencies from the pom JARs
        for (MavenGav gav : gavs) {
            replace.keySet().stream().filter(q -> compareGav(q, gav)).findFirst().ifPresent(q -> {
                MavenGav to = replace.get(q);
                gav.setGroupId(to.getGroupId());
                gav.setArtifactId(to.getArtifactId());
                gav.setVersion(to.getVersion());
                gav.setScope(to.getScope());
            });
        }
    }

    @Override
    protected String applicationPropertyLine(String key, String value) {
        if (key.startsWith("camel.server.") || key.startsWith("camel.management.")) {
            // skip "camel.server." or "camel.management." as this is for camel-main only
            return null;
        }
        // quarkus use dash cased properties and lets turn camel into dash as well (skip hawtio)
        boolean dash = key.startsWith("camel.") || (key.startsWith("quarkus.") && !key.startsWith("quarkus.hawtio."));
        if (dash) {
            key = StringHelper.camelCaseToDash(key);
        }
        return super.applicationPropertyLine(key, value);
    }

    @Override
    protected void copyDockerFiles(String buildDir) throws Exception {
        super.copyDockerFiles(buildDir);
        Path docker = Path.of(buildDir).resolve("src/main/docker");
        Files.createDirectories(docker);
        // copy files
        InputStream is = ExportQuarkus.class.getClassLoader().getResourceAsStream("quarkus-docker/Dockerfile.native");
        PathUtils.copyFromStream(is, docker.resolve("Dockerfile.native"), true);
    }

    @Override
    protected void copyReadme(String buildDir, String appJar) throws Exception {
        String[] ids = gav.split(":");
        InputStream is = ExportCamelMain.class.getClassLoader().getResourceAsStream("templates/readme.native.md.tmpl");
        String context = IOHelper.loadText(is);
        IOHelper.close(is);

        context = context.replaceAll("\\{\\{ \\.ArtifactId }}", ids[1]);
        context = context.replaceAll("\\{\\{ \\.Version }}", ids[2]);
        context = context.replaceAll("\\{\\{ \\.AppRuntimeJar }}", appJar);
        Files.writeString(Path.of(buildDir).resolve("readme.md"), context);
    }

    private void createMavenPom(Path settings, Path pom, Set<String> deps) throws Exception {
        String[] ids = gav.split(":");

        InputStream is = ExportQuarkus.class.getClassLoader().getResourceAsStream("templates/" + pomTemplateName);
        String context = IOHelper.loadText(is);
        IOHelper.close(is);

        Properties prop = new CamelCaseOrderedProperties();
        RuntimeUtil.loadProperties(prop, settings);
        // quarkus controls the camel version
        String repos = getMavenRepositories(settings, prop, quarkusVersion);

        CamelCatalog catalog = CatalogLoader.loadQuarkusCatalog(repos, quarkusVersion, quarkusGroupId, download);
        if (camelVersion == null) {
            camelVersion = catalog.getCatalogVersion();
        }

        String mp = prop.getProperty("quarkus.management.port");
        if (mp == null) {
            mp = "9876";
        }

        context = context.replaceAll("\\{\\{ \\.GroupId }}", ids[0]);
        context = context.replaceAll("\\{\\{ \\.ArtifactId }}", ids[1]);
        context = context.replaceAll("\\{\\{ \\.Version }}", ids[2]);
        context = context.replaceAll("\\{\\{ \\.QuarkusGroupId }}", quarkusGroupId);
        context = context.replaceAll("\\{\\{ \\.QuarkusArtifactId }}", quarkusArtifactId);
        context = context.replaceAll("\\{\\{ \\.QuarkusVersion }}", quarkusVersion);
        context = context.replaceAll("\\{\\{ \\.QuarkusManagementPort }}", mp);
        context = context.replaceAll("\\{\\{ \\.JavaVersion }}", javaVersion);
        context = context.replaceAll("\\{\\{ \\.CamelVersion }}", camelVersion);
        context = context.replaceAll("\\{\\{ \\.ProjectBuildOutputTimestamp }}", this.getBuildMavenProjectDate());

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
            String aid = gav.getArtifactId();
            // transform to camel-quarkus extension GAV
            if ("org.apache.camel".equals(gid)) {
                String qaid = aid.replace("camel-", "camel-quarkus-");
                ArtifactModel<?> am = catalog.modelFromMavenGAV("org.apache.camel.quarkus", qaid, null);
                if (am != null) {
                    // use quarkus extension
                    gav.setGroupId(am.getGroupId());
                    gav.setArtifactId(am.getArtifactId());
                    gav.setVersion(null); // uses BOM so version should not be included
                } else {
                    // there is no quarkus extension so use plain camel
                    gav.setVersion(camelVersion);
                }
            }
            gavs.add(gav);
        }

        // replace dependencies with special quarkus dependencies if we can find any
        replaceQuarkusDependencies(gavs);

        // sort artifacts
        gavs.sort(mavenGavComparator());

        StringBuilder sb = new StringBuilder();
        for (MavenGav gav : gavs) {
            //Special case, quarkus-pom.tmpl already have them included.
            if ("camel-quarkus-core".equals(gav.getArtifactId())
                    || "camel-quarkus-microprofile-health".equals(gav.getArtifactId())) {
                continue;
            }
            sb.append("        <dependency>\n");
            sb.append("            <groupId>").append(gav.getGroupId()).append("</groupId>\n");
            sb.append("            <artifactId>").append(gav.getArtifactId()).append("</artifactId>\n");
            if (gav.getVersion() != null) {
                sb.append("            <version>").append(gav.getVersion()).append("</version>\n");
            }
            if (gav.getScope() != null) {
                sb.append("            <scope>").append(gav.getScope()).append("</scope>\n");
            }
            if ("lib".equals(gav.getPackaging())) {
                // special for lib JARs
                sb.append("            <scope>system</scope>\n");
                sb.append("            <systemPath>\\$\\{project.basedir}/lib/").append(gav.getArtifactId()).append("-")
                        .append(gav.getVersion()).append(".jar</systemPath>\n");
            } else if ("camel-kamelets-utils".equals(gav.getArtifactId())) {
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

        Files.writeString(pom, context);
    }

    @Override
    protected Set<String> resolveDependencies(Path settings, Path profile) throws Exception {
        Set<String> answer = super.resolveDependencies(settings, profile);

        // remove out of the box dependencies
        answer.removeIf(s -> s.contains("camel-core"));
        answer.removeIf(s -> s.contains("camel-microprofile-health"));

        if (hawtio) {
            answer.add("mvn:org.apache.camel:camel-management");
            answer.add("mvn:io.hawt:hawtio-quarkus:" + hawtioVersion);
        }

        return answer;
    }

    private static boolean compareGav(MavenGav g1, MavenGav g2) {
        // only check for groupId and artifactId
        return g1.getGroupId().equals(g2.getGroupId()) && g1.getArtifactId().equals(g2.getArtifactId());
    }

}
