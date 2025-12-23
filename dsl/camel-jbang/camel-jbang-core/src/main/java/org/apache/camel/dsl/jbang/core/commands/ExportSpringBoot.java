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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.common.CatalogLoader;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.tooling.model.ArtifactModel;
import org.apache.camel.util.CamelCaseOrderedProperties;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.dsl.jbang.core.commands.ExportHelper.exportPackageName;

class ExportSpringBoot extends Export {

    public ExportSpringBoot(CamelJBangMain main) {
        super(main);
        pomTemplateName = "spring-boot-pom.tmpl";
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

        printer().println("Exporting as Spring Boot project to: " + exportDir);

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
        // copy application properties files
        copyApplicationPropertiesFiles(srcResourcesDir);

        // copy source files
        copySourceFiles(settings, profile, srcJavaDirRoot, srcJavaDir,
                srcResourcesDir, srcCamelResourcesDir,
                srcKameletsResourcesDir, srcPackageName);

        // create main class
        createMainClassSource(srcJavaDir, srcPackageName, mainClassname);
        // gather dependencies
        final Set<String> deps = resolveDependencies(settings, profile);
        // copy local lib JARs
        copyLocalLibDependencies(deps);
        // copy from settings to profile
        copySettingsAndProfile(settings, profile, srcResourcesDir, prop -> {
            if (!hasModeline(settings)) {
                prop.remove("camel.main.modeline");
            }
            // ensure spring-boot keeps running if no HTTP server included
            boolean http = deps.stream().anyMatch(s -> s.contains("mvn:org.apache.camel:camel-platform-http"));
            if (!http) {
                prop.put("camel.main.run-controller", "true");
            }
            // are we using http then enable embedded HTTP server (if not explicit configured already)
            if (!prop.containsKey("server.port")) {
                int port = httpServerPort(settings);
                if (port == -1 && http) {
                    port = 8080;
                }
                if (port != -1 && port != 8080) {
                    prop.put("server.port", port);
                }
            }
            if (!prop.containsKey("management.server.port")) {
                port = httpManagementPort(settings);
                if (port != -1) {
                    prop.put("management.server.port", port);
                }
            }
            if (hawtio) {
                // spring boot needs these options configured to support hawtio
                String s = prop.getProperty("management.endpoints.web.exposure.include");
                if (s == null) {
                    s = "hawtio,jolokia";
                } else {
                    s = s + ",hawtio,jolokia";
                }
                prop.setProperty("management.endpoints.web.exposure.include", s);
                prop.setProperty("spring.jmx.enabled", "true");
                prop.setProperty("hawtio.authenticationEnabled", "false");
            }
            return prop;
        });
        if ("maven".equals(buildTool)) {
            createMavenPom(settings, profile, buildDir.resolve("pom.xml"), deps);
            if (mavenWrapper) {
                copyMavenWrapper();
            }
        } else if ("gradle".equals(buildTool)) {
            createSettingsGradle(buildDir.resolve("settings.gradle"));
            createBuildGradle(settings, buildDir.resolve("build.gradle"), deps);
            if (gradleWrapper) {
                copyGradleWrapper();
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
        PathUtils.copyDirectory(buildDir, Paths.get(exportDir));
        PathUtils.deleteDirectory(buildDir);

        return 0;
    }

    private void createSettingsGradle(Path file) throws Exception {
        String[] ids = gav.split(":");

        String text = String.format("rootProject.name = '%s'", ids[1]);
        IOHelper.writeText(text, Files.newOutputStream(file));
    }

    private void createMavenPom(Path settings, Path profile, Path pom, Set<String> deps) throws Exception {
        String[] ids = gav.split(":");

        Properties prop = new CamelCaseOrderedProperties();
        RuntimeUtil.loadProperties(prop, settings);
        String repos = getMavenRepositories(settings, prop, camelSpringBootVersion);

        CamelCatalog catalog = CatalogLoader.loadSpringBootCatalog(repos, camelSpringBootVersion, download);
        if (ObjectHelper.isEmpty(camelVersion)) {
            camelVersion = catalog.getLoadedVersion();
        }
        if (ObjectHelper.isEmpty(camelVersion)) {
            camelVersion = VersionHelper.extractCamelVersion();
        }

        // First try to load a specialized template from the catalog, if the catalog does not provide it
        // fallback to the template defined in camel-jbang-core
        String context;
        InputStream template = catalog.loadResource("camel-jbang", pomTemplateName);
        if (template != null) {
            context = IOHelper.loadText(template);
        } else {
            context = readResourceTemplate("templates/" + pomTemplateName);
        }

        context = context.replaceAll("\\{\\{ \\.GroupId }}", ids[0]);
        context = context.replaceAll("\\{\\{ \\.ArtifactId }}", ids[1]);
        context = context.replaceAll("\\{\\{ \\.Version }}", ids[2]);
        context = context.replaceAll("\\{\\{ \\.SpringBootVersion }}", springBootVersion);
        context = context.replaceAll("\\{\\{ \\.JavaVersion }}", javaVersion);
        context = context.replaceAll("\\{\\{ \\.CamelVersion }}", camelVersion);
        context = context.replaceAll("\\{\\{ \\.CamelSpringBootVersion }}",
                Objects.requireNonNullElseGet(camelSpringBootVersion, () -> camelVersion));
        context = context.replaceFirst("\\{\\{ \\.ProjectBuildOutputTimestamp }}", this.getBuildMavenProjectDate());

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

            // transform to camel-spring-boot starter GAV
            if ("org.apache.camel".equals(gid)) {
                ArtifactModel<?> am = catalog.modelFromMavenGAV("org.apache.camel.springboot", aid + "-starter", null);
                if (am != null) {
                    // use spring-boot starter
                    gav.setGroupId(am.getGroupId());
                    gav.setArtifactId(am.getArtifactId());
                    gav.setVersion(null); // uses BOM so version should not be included
                } else {
                    // there is no spring boot starter so use plain camel
                    gav.setVersion(camelVersion);
                }
            }
            // use spring-boot version from BOM
            if ("org.springframework.boot".equals(gid)) {
                gav.setVersion(null); // uses BOM so version should not be included
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

        IOHelper.writeText(context, Files.newOutputStream(pom));
    }

    private void createBuildGradle(Path settings, Path gradleBuild, Set<String> deps) throws Exception {
        String[] ids = gav.split(":");

        String context = readResourceTemplate("templates/spring-boot-build-gradle.tmpl");

        Properties prop = new CamelCaseOrderedProperties();
        RuntimeUtil.loadProperties(prop, settings);
        String repos = getMavenRepositories(settings, prop, camelSpringBootVersion);

        CamelCatalog catalog = CatalogLoader.loadSpringBootCatalog(repos, camelSpringBootVersion, download);
        String camelVersion = catalog.getLoadedVersion();

        context = context.replaceFirst("\\{\\{ \\.GroupId }}", ids[0]);
        context = context.replaceFirst("\\{\\{ \\.ArtifactId }}", ids[1]);
        context = context.replaceFirst("\\{\\{ \\.Version }}", ids[2]);
        context = context.replaceAll("\\{\\{ \\.SpringBootVersion }}", springBootVersion);
        context = context.replaceFirst("\\{\\{ \\.JavaVersion }}", javaVersion);
        context = context.replaceAll("\\{\\{ \\.CamelVersion }}", camelVersion);
        context = context.replaceFirst("\\{\\{ \\.CamelSpringBootVersion }}",
                Objects.requireNonNullElse(camelSpringBootVersion, camelVersion));

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

            // transform to camel-spring-boot starter GAV
            if ("org.apache.camel".equals(gid)) {
                ArtifactModel<?> am = catalog.modelFromMavenGAV("org.apache.camel.springboot", aid + "-starter", null);
                if (am != null) {
                    // use spring-boot starter
                    gav.setGroupId(am.getGroupId());
                    gav.setArtifactId(am.getArtifactId());
                    gav.setVersion(am.getVersion());
                } else {
                    // there is no spring boot starter so use plain camel
                    gav.setVersion(camelVersion);
                }
            }
            gavs.add(gav);
        }

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

        IOHelper.writeText(context, Files.newOutputStream(gradleBuild));
    }

    @Override
    protected Set<String> resolveDependencies(Path settings, Path profile) throws Exception {
        Set<String> answer = super.resolveDependencies(settings, profile);

        // remove out of the box dependencies
        answer.removeIf(s -> s.contains("camel-core"));

        boolean http = answer.stream().anyMatch(s -> s.contains("mvn:org.apache.camel:camel-platform-http"));
        if (hasOpenapi(answer) && !http) {
            // include http server if using openapi
            answer.add("mvn:org.apache.camel:camel-platform-http");
        }
        if (hawtio) {
            answer.add("mvn:org.apache.camel:camel-management");
            answer.add("mvn:io.hawt:hawtio-springboot:" + hawtioVersion);
        }

        return answer;
    }

    private void createMainClassSource(Path srcJavaDir, String packageName, String mainClassname) throws Exception {
        String context = readResourceTemplate("templates/spring-boot-main.tmpl");

        context = context.replaceFirst("\\{\\{ \\.PackageName }}", packageName);
        context = context.replaceAll("\\{\\{ \\.MainClassname }}", mainClassname);
        IOHelper.writeText(context,
                Files.newOutputStream(srcJavaDir.resolve(mainClassname + ".java")));
    }

    @Override
    protected void adjustJavaSourceFileLine(String line, OutputStream fos) throws Exception {
        if (line.startsWith("public class")
                && (line.contains("RouteBuilder") || line.contains("EndpointRouteBuilder"))) {
            fos.write("import org.springframework.stereotype.Component;\n\n"
                    .getBytes(StandardCharsets.UTF_8));
            fos.write("@Component\n".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override
    protected String applicationPropertyLine(String key, String value) {
        if (key.startsWith("camel.server.") || key.startsWith("camel.management.")) {
            // skip "camel.server." or "camel.management." as this is for camel-main only
            return null;
        }
        boolean camel44orOlder = camelSpringBootVersion != null && VersionHelper.isGE("4.4.0", camelSpringBootVersion);
        if (camel44orOlder) {
            // camel.main.x should be renamed to camel.springboot.x (for camel 4.4.x or older)
            if (key.startsWith("camel.main.")) {
                key = "camel.springboot." + key.substring(11);
            }
        }
        return super.applicationPropertyLine(key, value);
    }

    private String readResourceTemplate(String name) throws IOException {
        InputStream is = ExportSpringBoot.class.getClassLoader().getResourceAsStream(name);
        String text = IOHelper.loadText(is);
        IOHelper.close(is);
        return text;
    }

}
