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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.common.CatalogLoader;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.tooling.model.ArtifactModel;
import org.apache.camel.util.CamelCaseOrderedProperties;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.commons.io.FileUtils;

class ExportQuarkus extends Export {

    public ExportQuarkus(CamelJBangMain main) {
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
        if (fresh || files != null || !settings.exists()) {
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
            System.out.println("Exporting as Quarkus project to: " + exportDir);
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
        srcCamelResourcesDir.mkdirs();
        File srcKameletsResourcesDir = new File(BUILD_DIR, "src/main/resources/kamelets");
        srcKameletsResourcesDir.mkdirs();
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
        // copy docker files
        copyDockerFiles();
        // gather dependencies
        Set<String> deps = resolveDependencies(settings, profile);
        // copy local lib JARs
        copyLocalLibDependencies(deps);
        if ("maven".equals(buildTool)) {
            createMavenPom(settings, new File(BUILD_DIR, "pom.xml"), deps);
            if (mavenWrapper) {
                copyMavenWrapper();
            }
        } else if ("gradle".equals(buildTool)) {
            createGradleProperties(new File(BUILD_DIR, "gradle.properties"));
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

    @Override
    protected void prepareApplicationProperties(Properties properties) {
        // quarkus native compilation only works if we specify each resource explicit

        StringJoiner sj = new StringJoiner(",");
        StringJoiner sj2 = new StringJoiner(",");
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String k = entry.getKey().toString();
            String v = entry.getValue().toString();

            if ("camel.main.routesIncludePattern".equals(k)) {
                v = Arrays.stream(v.split(","))
                        .filter(d -> !d.endsWith(".java")) // skip .java as they are in the src/main/java folder
                        .map(ExportQuarkus::removeScheme) // remove scheme and routes are in camel sub-folder
                        .map(s -> "camel/" + s)
                        .collect(Collectors.joining(","));
                sj.add(v);
            }
            // extra classpath files
            if ("camel.jbang.classpathFiles".equals(k)) {
                v = Arrays.stream(v.split(","))
                        .filter(d -> !d.endsWith(".jar")) // skip local lib JARs
                        .map(ExportQuarkus::removeScheme) // remove scheme
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
            if (sj.length() > 0) {
                properties.setProperty("quarkus.native.resources.includes", sj.toString());
            }
            if (routes != null) {
                properties.setProperty("camel.main.routes-include-pattern", routes);
            }
        }
        if (secretsRefresh) {
            if (secretsRefreshProviders != null) {
                List<String> providers = getSecretProviders();

                for (String provider : providers) {
                    switch (provider) {
                        case "aws":
                            exportAwsSecretsRefreshProp(properties);
                            break;
                        case "gcp":
                            exportGcpSecretsRefreshProp(properties);
                            break;
                        case "azure":
                            exportAzureSecretsRefreshProp(properties);
                            break;
                        default:
                            break;
                    }
                }
            }
        }
    }

    private static String removeScheme(String s) {
        if (s.contains(":")) {
            s = StringHelper.after(s, ":");
        }
        s = s.replace(".camel-jbang/", "");
        return s;
    }

    private void createGradleProperties(File output) throws Exception {
        InputStream is = ExportQuarkus.class.getClassLoader().getResourceAsStream("templates/quarkus-gradle-properties.tmpl");
        String context = IOHelper.loadText(is);
        IOHelper.close(is);

        context = context.replaceFirst("\\{\\{ \\.QuarkusGroupId }}", quarkusGroupId);
        context = context.replaceFirst("\\{\\{ \\.QuarkusArtifactId }}", quarkusArtifactId);
        context = context.replaceAll("\\{\\{ \\.QuarkusVersion }}", quarkusVersion);

        IOHelper.writeText(context, new FileOutputStream(output, false));
    }

    private void createSettingsGradle(File output) throws Exception {
        String[] ids = gav.split(":");

        InputStream is = ExportQuarkus.class.getClassLoader().getResourceAsStream("templates/quarkus-settings-gradle.tmpl");
        String context = IOHelper.loadText(is);
        IOHelper.close(is);

        context = context.replaceFirst("\\{\\{ \\.GroupId }}", ids[0]);
        context = context.replaceFirst("\\{\\{ \\.ArtifactId }}", ids[1]);
        context = context.replaceFirst("\\{\\{ \\.Version }}", ids[2]);

        IOHelper.writeText(context, new FileOutputStream(output, false));
    }

    private void createBuildGradle(File settings, File gradleBuild, Set<String> deps) throws Exception {
        String[] ids = gav.split(":");

        InputStream is = ExportSpringBoot.class.getClassLoader().getResourceAsStream("templates/quarkus-build-gradle.tmpl");
        String context = IOHelper.loadText(is);
        IOHelper.close(is);

        Properties prop = new CamelCaseOrderedProperties();
        RuntimeUtil.loadProperties(prop, settings);
        // quarkus controls the camel version
        String repos = getMavenRepos(settings, prop, quarkusVersion);

        CamelCatalog catalog = CatalogLoader.loadQuarkusCatalog(repos, quarkusVersion);
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

        if (secretsRefresh) {
            if (secretsRefreshProviders != null) {
                List<String> providers = getSecretProviders();
                for (String provider : providers) {
                    switch (provider) {
                        case "aws":
                            MavenGav awsGav = new MavenGav();
                            awsGav.setGroupId("org.apache.camel.quarkus");
                            awsGav.setArtifactId("camel-quarkus-aws-secrets-manager");
                            awsGav.setVersion(null);
                            gavs.add(awsGav);
                            break;
                        case "gcp":
                            MavenGav gcpGav = new MavenGav();
                            gcpGav.setGroupId("org.apache.camel.quarkus");
                            gcpGav.setArtifactId("camel-quarkus-google-secret-manager");
                            gcpGav.setVersion(null);
                            gavs.add(gcpGav);
                            break;
                        case "azure":
                            MavenGav azureGav = new MavenGav();
                            azureGav.setGroupId("org.apache.camel.quarkus");
                            azureGav.setArtifactId("camel-quarkus-azure-key-vault");
                            azureGav.setVersion(null);
                            gavs.add(azureGav);
                            break;
                        default:
                            break;
                    }
                }
            }
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
    protected String applicationPropertyLine(String key, String value) {
        // quarkus use dash cased properties and lets turn camel into dash as well
        if (key.startsWith("quarkus.") || key.startsWith("camel.")) {
            key = StringHelper.camelCaseToDash(key);
        }
        return super.applicationPropertyLine(key, value);
    }

    private void copyDockerFiles() throws Exception {
        File docker = new File(BUILD_DIR, "src/main/docker");
        docker.mkdirs();
        // copy files
        InputStream is = ExportQuarkus.class.getClassLoader().getResourceAsStream("quarkus-docker/Dockerfile.jvm");
        IOHelper.copyAndCloseInput(is, new FileOutputStream(new File(docker, "Dockerfile.jvm")));
        is = ExportQuarkus.class.getClassLoader().getResourceAsStream("quarkus-docker/Dockerfile.legacy-jar");
        IOHelper.copyAndCloseInput(is, new FileOutputStream(new File(docker, "Dockerfile.legacy-jar")));
        is = ExportQuarkus.class.getClassLoader().getResourceAsStream("quarkus-docker/Dockerfile.native");
        IOHelper.copyAndCloseInput(is, new FileOutputStream(new File(docker, "Dockerfile.native")));
        is = ExportQuarkus.class.getClassLoader().getResourceAsStream("quarkus-docker/Dockerfile.native-micro");
        IOHelper.copyAndCloseInput(is, new FileOutputStream(new File(docker, "Dockerfile.native-micro")));
    }

    private void createMavenPom(File settings, File pom, Set<String> deps) throws Exception {
        String[] ids = gav.split(":");

        InputStream is = ExportQuarkus.class.getClassLoader().getResourceAsStream("templates/quarkus-pom.tmpl");
        String context = IOHelper.loadText(is);
        IOHelper.close(is);

        Properties prop = new CamelCaseOrderedProperties();
        RuntimeUtil.loadProperties(prop, settings);
        // quarkus controls the camel version
        String repos = getMavenRepos(settings, prop, quarkusVersion);

        CamelCatalog catalog = CatalogLoader.loadQuarkusCatalog(repos, quarkusVersion);
        if (camelVersion == null) {
            camelVersion = catalog.getCatalogVersion();
        }

        context = context.replaceFirst("\\{\\{ \\.GroupId }}", ids[0]);
        context = context.replaceFirst("\\{\\{ \\.ArtifactId }}", ids[1]);
        context = context.replaceFirst("\\{\\{ \\.Version }}", ids[2]);
        context = context.replaceFirst("\\{\\{ \\.QuarkusGroupId }}", quarkusGroupId);
        context = context.replaceFirst("\\{\\{ \\.QuarkusArtifactId }}", quarkusArtifactId);
        context = context.replaceAll("\\{\\{ \\.QuarkusVersion }}", quarkusVersion);
        context = context.replaceFirst("\\{\\{ \\.JavaVersion }}", javaVersion);
        context = context.replaceFirst("\\{\\{ \\.CamelVersion }}", camelVersion);

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

        // sort artifacts
        gavs.sort(mavenGavComparator());

        StringBuilder sb = new StringBuilder();
        for (MavenGav gav : gavs) {
            //Special case, quarkus-pom.tmpl already have them included.
            if ("camel-quarkus-core".equals(gav.getArtifactId()) || "camel-quarkus-platform-http".equals(gav.getArtifactId())
                    || "camel-quarkus-microprofile-health".equals(gav.getArtifactId())) {
                continue;
            }
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
        if (secretsRefresh) {
            if (secretsRefreshProviders != null) {
                List<String> providers = getSecretProviders();
                for (String provider : providers) {
                    switch (provider) {
                        case "aws":
                            sb.append("        <dependency>\n");
                            sb.append("            <groupId>").append("org.apache.camel.quarkus").append("</groupId>\n");
                            sb.append("            <artifactId>").append("camel-quarkus-aws-secrets-manager")
                                    .append("</artifactId>\n");
                            sb.append("        </dependency>\n");
                            break;
                        case "gcp":
                            sb.append("        <dependency>\n");
                            sb.append("            <groupId>").append("org.apache.camel.quarkus").append("</groupId>\n");
                            sb.append("            <artifactId>").append("camel-quarkus-google-secret-manager")
                                    .append("</artifactId>\n");
                            sb.append("        </dependency>\n");
                            break;
                        case "azure":
                            sb.append("        <dependency>\n");
                            sb.append("            <groupId>").append("org.apache.camel.quarkus").append("</groupId>\n");
                            sb.append("            <artifactId>").append("camel-quarkus-azure-key-vault")
                                    .append("</artifactId>\n");
                            sb.append("        </dependency>\n");
                            break;
                        default:
                            break;
                    }
                }
            }
        }
        context = context.replaceFirst("\\{\\{ \\.CamelDependencies }}", sb.toString());

        IOHelper.writeText(context, new FileOutputStream(pom, false));
    }

    @Override
    protected Set<String> resolveDependencies(File settings, File profile) throws Exception {
        Set<String> answer = super.resolveDependencies(settings, profile);

        // remove out of the box dependencies
        answer.removeIf(s -> s.contains("camel-core"));
        answer.removeIf(s -> s.contains("camel-platform-http"));
        answer.removeIf(s -> s.contains("camel-microprofile-health"));

        return answer;
    }

}
