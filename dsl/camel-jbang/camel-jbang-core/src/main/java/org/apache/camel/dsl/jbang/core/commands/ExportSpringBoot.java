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
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.catalog.RuntimeProvider;
import org.apache.camel.catalog.VersionManager;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.main.KameletMain;
import org.apache.camel.main.download.DependencyDownloaderClassLoader;
import org.apache.camel.main.download.MavenArtifact;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.main.download.MavenGav;
import org.apache.camel.tooling.model.ArtifactModel;
import org.apache.camel.util.CamelCaseOrderedProperties;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.commons.io.FileUtils;

class ExportSpringBoot extends Export {

    private static final String DEFAULT_CAMEL_CATALOG = "org.apache.camel.catalog.DefaultCamelCatalog";
    private static final String SPRING_BOOT_CATALOG_PROVIDER = "org.apache.camel.springboot.catalog.SpringBootRuntimeProvider";

    public ExportSpringBoot(CamelJBangMain main) {
        super(main);
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

        File profile = new File(getProfile() + ".properties");

        // the settings file has information what to export
        File settings = new File(Run.WORK_DIR + "/" + Run.RUN_SETTINGS_FILE);
        if (fresh || !settings.exists()) {
            // allow to automatic build
            if (!quiet) {
                System.out.println("Generating fresh run data");
            }
            int silent = runSilently();
            if (silent != 0) {
                return silent;
            }
        } else {
            if (!quiet) {
                System.out.println("Reusing existing run data");
            }
        }

        if (!quiet) {
            System.out.println("Exporting as Spring Boot project to: " + exportDir);
        }

        // use a temporary work dir
        File buildDir = new File(BUILD_DIR);
        FileUtil.removeDir(buildDir);
        buildDir.mkdirs();

        // copy source files
        String packageName = exportPackageName(ids[0], ids[1]);
        File srcJavaDir = new File(BUILD_DIR, "src/main/java/" + packageName.replace('.', '/'));
        srcJavaDir.mkdirs();
        File srcResourcesDir = new File(BUILD_DIR, "src/main/resources");
        srcResourcesDir.mkdirs();
        File srcCamelResourcesDir = new File(BUILD_DIR, "src/main/resources/camel");
        srcCamelResourcesDir.mkdirs();
        copySourceFiles(settings, profile, srcJavaDir, srcResourcesDir, srcCamelResourcesDir, packageName);
        // copy from settings to profile
        copySettingsAndProfile(settings, profile, srcResourcesDir, null);
        // create main class
        createMainClassSource(srcJavaDir, packageName, mainClassname);
        // gather dependencies
        Set<String> deps = resolveDependencies(settings, profile);
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

        if (!exportDir.equals(".")) {
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

        String context = readResourceTemplate("templates/spring-boot-pom.tmpl");

        CamelCatalog catalog = loadSpringBootCatalog(camelSpringBootVersion);
        String camelVersion = catalog.getLoadedVersion();

        context = context.replaceFirst("\\{\\{ \\.GroupId }}", ids[0]);
        context = context.replaceFirst("\\{\\{ \\.ArtifactId }}", ids[1]);
        context = context.replaceFirst("\\{\\{ \\.Version }}", ids[2]);
        context = context.replaceAll("\\{\\{ \\.SpringBootVersion }}", springBootVersion);
        context = context.replaceFirst("\\{\\{ \\.JavaVersion }}", javaVersion);
        context = context.replaceFirst("\\{\\{ \\.CamelVersion }}", camelVersion);

        // Convert jkube properties to maven properties
        Properties allProps = new CamelCaseOrderedProperties();
        if (profile != null && profile.exists()) {
            RuntimeUtil.loadProperties(allProps, profile);
        }
        StringBuilder sbJKube = new StringBuilder();
        allProps.stringPropertyNames().stream().filter(p -> p.startsWith("jkube")).forEach(key -> {
            String value = allProps.getProperty(key);
            sbJKube.append("        <").append(key).append(">").append(value).append("</").append(key).append(">\n");
        });
        context = context.replaceFirst(Pattern.quote("{{ .jkubeProperties }}"),
                Matcher.quoteReplacement(sbJKube.toString()));

        Properties prop = new CamelCaseOrderedProperties();
        RuntimeUtil.loadProperties(prop, settings);

        String repos = prop.getProperty("camel.jbang.repos");
        if (repos == null) {
            context = context.replaceFirst("\\{\\{ \\.MavenRepositories }}", "");
        } else {
            int i = 1;
            StringBuilder sb = new StringBuilder();
            sb.append("    <repositories>\n");
            for (String repo : repos.split(",")) {
                sb.append("        <repository>\n");
                sb.append("            <id>custom").append(i++).append("</id>\n");
                sb.append("            <url>").append(repo).append("</url>\n");
                sb.append("        </repository>\n");
            }
            sb.append("    </repositories>\n");
            context = context.replaceFirst("\\{\\{ \\.MavenRepositories }}", sb.toString());
        }

        List<MavenGav> gavs = new ArrayList<>();
        for (String dep : deps) {
            MavenGav gav = MavenGav.parseGav(dep);
            String gid = gav.getGroupId();
            String aid = gav.getArtifactId();
            String v = gav.getVersion();

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
            sb.append("        </dependency>\n");
        }
        context = context.replaceFirst("\\{\\{ \\.CamelDependencies }}", sb.toString());

        // add apache snapshot repository if camel version ends with SNAPSHOT
        String contextSnapshot = "";
        if (camelVersion.endsWith("SNAPSHOT")) {
            contextSnapshot = readResourceTemplate("templates/apache-snapshot-maven.tmpl");
        }
        context = context.replaceFirst(Pattern.quote("{{ .snapshotRepository }}"), Matcher.quoteReplacement(contextSnapshot));

        // add jkube profiles if there is jkube version property
        String jkubeProfiles = "";
        if (allProps.getProperty("jkube.version") != null) {
            jkubeProfiles = readResourceTemplate("templates/jkube-profiles.tmpl");
        }
        context = context.replaceFirst(Pattern.quote("{{ .jkubeProfiles }}"), Matcher.quoteReplacement(jkubeProfiles));

        IOHelper.writeText(context, new FileOutputStream(pom, false));
    }

    private void createBuildGradle(File settings, File gradleBuild, Set<String> deps) throws Exception {
        String[] ids = gav.split(":");

        String context = readResourceTemplate("templates/spring-boot-build-gradle.tmpl");

        CamelCatalog catalog = loadSpringBootCatalog(camelSpringBootVersion);
        String camelVersion = catalog.getLoadedVersion();

        context = context.replaceFirst("\\{\\{ \\.GroupId }}", ids[0]);
        context = context.replaceFirst("\\{\\{ \\.ArtifactId }}", ids[1]);
        context = context.replaceFirst("\\{\\{ \\.Version }}", ids[2]);
        context = context.replaceAll("\\{\\{ \\.SpringBootVersion }}", springBootVersion);
        context = context.replaceFirst("\\{\\{ \\.JavaVersion }}", javaVersion);
        context = context.replaceAll("\\{\\{ \\.CamelVersion }}", camelVersion);

        Properties prop = new CamelCaseOrderedProperties();
        RuntimeUtil.loadProperties(prop, settings);
        String repos = prop.getProperty("camel.jbang.repos");
        if (repos == null) {
            context = context.replaceFirst("\\{\\{ \\.MavenRepositories }}", "");
        } else {
            StringBuilder sb = new StringBuilder();
            for (String repo : repos.split(",")) {
                sb.append("    maven {\n");
                sb.append("        url: '").append(repo).append("'\n");
                sb.append("    }\n");
            }
            context = context.replaceFirst("\\{\\{ \\.MavenRepositories }}", sb.toString());
        }

        List<MavenGav> gavs = new ArrayList<>();
        for (String dep : deps) {
            MavenGav gav = MavenGav.parseGav(dep);
            String gid = gav.getGroupId();
            String aid = gav.getArtifactId();
            String v = gav.getVersion();

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
            sb.append("    implementation '").append(gav.toString()).append("'\n");
        }
        context = context.replaceFirst("\\{\\{ \\.CamelDependencies }}", sb.toString());

        IOHelper.writeText(context, new FileOutputStream(gradleBuild, false));
    }

    @Override
    protected Set<String> resolveDependencies(File settings, File profile) throws Exception {
        Set<String> answer = super.resolveDependencies(settings, profile);

        answer.removeIf(s -> s.contains("camel-core"));
        answer.removeIf(s -> s.contains("camel-dsl-modeline"));

        // if platform-http is included then we need servlet as implementation
        if (answer.stream().anyMatch(s -> s.contains("camel-platform-http") && !s.contains("camel-servlet"))) {
            // version does not matter
            answer.add("mvn:org.apache.camel:camel-servlet:1.0-SNAPSHOT");
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
        // camel.main.x should be renamed to camel.springboot.x
        if (key.startsWith("camel.main.")) {
            key = "camel.springboot." + key.substring(11);
        }
        return super.applicationPropertyLine(key, value);
    }

    private CamelCatalog loadSpringBootCatalog(String version) throws Exception {
        CamelCatalog answer = new DefaultCamelCatalog();
        if (version == null) {
            version = answer.getCatalogVersion();
        }

        // use kamelet-main to dynamic download dependency via maven
        KameletMain main = new KameletMain();
        try {
            main.start();

            // wrap downloaded catalog files in an isolated classloader
            DependencyDownloaderClassLoader cl
                    = new DependencyDownloaderClassLoader(main.getCamelContext().getApplicationContextClassLoader());

            // download camel-catalog for that specific version
            MavenDependencyDownloader downloader = main.getCamelContext().hasService(MavenDependencyDownloader.class);
            MavenArtifact ma = downloader.downloadArtifact("org.apache.camel", "camel-catalog", version);
            if (ma != null) {
                cl.addFile(ma.getFile());
            } else {
                throw new IOException("Cannot download org.apache.camel:camel-catalog:" + version);
            }
            ma = downloader.downloadArtifact("org.apache.camel.springboot", "camel-catalog-provider-springboot", version);
            if (ma != null) {
                cl.addFile(ma.getFile());
            } else {
                throw new IOException(
                        "Cannot download org.apache.camel.springboot:camel-catalog-provider-springboot:" + version);
            }

            answer.setVersionManager(new SpringBootCatalogVersionManager(version, cl));
            Class<RuntimeProvider> clazz = (Class<RuntimeProvider>) cl.loadClass(SPRING_BOOT_CATALOG_PROVIDER);
            if (clazz != null) {
                RuntimeProvider provider = main.getCamelContext().getInjector().newInstance(clazz);
                if (provider != null) {
                    answer.setRuntimeProvider(provider);
                }
            }
            answer.enableCache();

        } finally {
            main.stop();
        }

        return answer;
    }

    private String readResourceTemplate(String name) throws IOException {
        InputStream is = ExportSpringBoot.class.getClassLoader().getResourceAsStream(name);
        String text = IOHelper.loadText(is);
        IOHelper.close(is);
        return text;
    }

    private final class SpringBootCatalogVersionManager implements VersionManager {

        private ClassLoader classLoader;
        private final String version;

        public SpringBootCatalogVersionManager(String version, ClassLoader classLoader) {
            this.version = version;
            this.classLoader = classLoader;
        }

        @Override
        public void setClassLoader(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        public String getLoadedVersion() {
            return version;
        }

        @Override
        public boolean loadVersion(String version) {
            return this.version.equals(version);
        }

        @Override
        public String getRuntimeProviderLoadedVersion() {
            return version;
        }

        @Override
        public boolean loadRuntimeProviderVersion(String groupId, String artifactId, String version) {
            return true;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            return classLoader.getResourceAsStream(name);
        }
    }

}
