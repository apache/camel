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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.common.CatalogLoader;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.tooling.model.ArtifactModel;
import org.apache.camel.util.CamelCaseOrderedProperties;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.io.FileUtils;

class ExportSpringBoot extends Export {

    public ExportSpringBoot(CamelJBangMain main) {
        super(main);
        pomTemplateName = "spring-boot-pom.tmpl";
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

        File profile = new File("application.properties");

        // the settings file has information what to export
        File settings = new File(CommandLineHelper.getWorkDir(), Run.RUN_SETTINGS_FILE);
        if (fresh || !files.isEmpty() || !settings.exists()) {
            // allow to automatic build
            if (!quiet) {
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
            printer().println("Exporting as Spring Boot project to: " + exportDir);
        }

        // use a temporary work dir
        File buildDir = new File(BUILD_DIR);
        FileUtil.removeDir(buildDir);
        buildDir.mkdirs();

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
            if (!hasModeline(settings)) {
                prop.remove("camel.main.modeline");
            }
            return prop;
        });
        // create main class
        createMainClassSource(srcJavaDir, srcPackageName, mainClassname);
        // gather dependencies
        Set<String> deps = resolveDependencies(settings, profile);
        // copy local lib JARs
        copyLocalLibDependencies(deps);
        if ("maven".equals(buildTool)) {
            createMavenPom(settings, profile, new File(BUILD_DIR, "pom.xml"), deps);
            if (mavenWrapper) {
                copyMavenWrapper();
            }
        } else if ("gradle".equals(buildTool)) {
            createSettingsGradle(new File(BUILD_DIR, "settings.gradle"));
            createBuildGradle(settings, new File(BUILD_DIR, "build.gradle"), deps);
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
        FileUtils.copyDirectory(new File(BUILD_DIR), new File(exportDir));
        FileUtil.removeDir(new File(BUILD_DIR));

        return 0;
    }

    private void createSettingsGradle(File file) throws Exception {
        String[] ids = gav.split(":");

        String text = String.format("rootProject.name = '%s'", ids[1]);
        IOHelper.writeText(text, new FileOutputStream(file, false));
    }

    private void createMavenPom(File settings, File profile, File pom, Set<String> deps) throws Exception {
        String[] ids = gav.split(":");

        Properties prop = new CamelCaseOrderedProperties();
        RuntimeUtil.loadProperties(prop, settings);
        String repos = getMavenRepositories(settings, prop, camelSpringBootVersion);

        CamelCatalog catalog = CatalogLoader.loadSpringBootCatalog(repos, camelSpringBootVersion);
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

        IOHelper.writeText(context, new FileOutputStream(pom, false));
    }

    private void createBuildGradle(File settings, File gradleBuild, Set<String> deps) throws Exception {
        String[] ids = gav.split(":");

        String context = readResourceTemplate("templates/spring-boot-build-gradle.tmpl");

        Properties prop = new CamelCaseOrderedProperties();
        RuntimeUtil.loadProperties(prop, settings);
        String repos = getMavenRepositories(settings, prop, camelSpringBootVersion);

        CamelCatalog catalog = CatalogLoader.loadSpringBootCatalog(repos, camelSpringBootVersion);
        String camelVersion = catalog.getLoadedVersion();

        context = context.replaceFirst("\\{\\{ \\.GroupId }}", ids[0]);
        context = context.replaceFirst("\\{\\{ \\.ArtifactId }}", ids[1]);
        context = context.replaceFirst("\\{\\{ \\.Version }}", ids[2]);
        context = context.replaceAll("\\{\\{ \\.SpringBootVersion }}", springBootVersion);
        context = context.replaceFirst("\\{\\{ \\.JavaVersion }}", javaVersion);
        context = context.replaceAll("\\{\\{ \\.CamelVersion }}", camelVersion);
        if (camelSpringBootVersion != null) {
            context = context.replaceFirst("\\{\\{ \\.CamelSpringBootVersion }}", camelSpringBootVersion);
        } else {
            context = context.replaceFirst("\\{\\{ \\.CamelSpringBootVersion }}", camelVersion);
        }

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

        IOHelper.writeText(context, new FileOutputStream(gradleBuild, false));
    }

    @Override
    protected Set<String> resolveDependencies(File settings, File profile) throws Exception {
        Set<String> answer = super.resolveDependencies(settings, profile);

        // remove out of the box dependencies
        answer.removeIf(s -> s.contains("camel-core"));

        boolean http = answer.stream().anyMatch(s -> s.contains("mvn:org.apache.camel:camel-platform-http"));
        if (hasOpenapi(answer) && !http) {
            // include http server if using openapi
            answer.add("mvn:org.apache.camel:camel-platform-http");
        }

        return answer;
    }

    private void createMainClassSource(File srcJavaDir, String packageName, String mainClassname) throws Exception {
        String context = readResourceTemplate("templates/spring-boot-main.tmpl");

        context = context.replaceFirst("\\{\\{ \\.PackageName }}", packageName);
        context = context.replaceAll("\\{\\{ \\.MainClassname }}", mainClassname);
        IOHelper.writeText(context, new FileOutputStream(srcJavaDir + "/" + mainClassname + ".java", false));
    }

    @Override
    protected void adjustJavaSourceFileLine(String line, FileOutputStream fos) throws Exception {
        if (line.startsWith("public class")
                && (line.contains("RouteBuilder") || line.contains("EndpointRouteBuilder"))) {
            fos.write("import org.springframework.stereotype.Component;\n\n"
                    .getBytes(StandardCharsets.UTF_8));
            fos.write("@Component\n".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override
    protected String applicationPropertyLine(String key, String value) {
        if (key.startsWith("camel.server.")) {
            // skip "camel.server." as this is for camel-main only
            return null;
        }
        boolean camel44orOlder = camelSpringBootVersion != null && VersionHelper.isLE("4.4", camelSpringBootVersion);
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
