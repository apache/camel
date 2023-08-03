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
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.util.CamelCaseOrderedProperties;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.commons.io.FileUtils;

class ExportCamelMain extends Export {

    public ExportCamelMain(CamelJBangMain main) {
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
        if (buildTool.equals("gradle")) {
            System.err.println("--build-tool=gradle is not support yet for camel-main runtime.");
        }

        File profile = new File(getProfile() + ".properties");

        // the settings file has information what to export
        File settings = new File(Run.WORK_DIR + "/" + Run.RUN_SETTINGS_FILE);
        if (fresh || files != null || !settings.exists()) {
            // allow to automatic build
            if (!quiet && fresh) {
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
            System.out.println("Exporting as Camel Main project to: " + exportDir);
        }

        // use a temporary work dir
        File buildDir = new File(BUILD_DIR);
        FileUtil.removeDir(buildDir);
        buildDir.mkdirs();

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
        srcCamelResourcesDir.mkdirs();
        File srcKameletsResourcesDir = new File(BUILD_DIR, "src/main/resources/kamelets");
        srcKameletsResourcesDir.mkdirs();
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
        // gather dependencies
        Set<String> deps = resolveDependencies(settings, profile);
        // copy local lib JARs
        copyLocalLibDependencies(deps);
        if ("maven".equals(buildTool)) {
            createMavenPom(settings, profile, new File(BUILD_DIR, "pom.xml"), deps, srcPackageName);
            if (mavenWrapper) {
                copyMavenWrapper();
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

    private void createMavenPom(File settings, File profile, File pom, Set<String> deps, String packageName) throws Exception {
        String[] ids = gav.split(":");

        InputStream is = ExportCamelMain.class.getClassLoader().getResourceAsStream("templates/main-pom.tmpl");
        String context = IOHelper.loadText(is);
        IOHelper.close(is);

        if (camelVersion == null) {
            CamelCatalog catalog = new DefaultCamelCatalog();
            camelVersion = catalog.getCatalogVersion();
        }

        context = context.replaceFirst("\\{\\{ \\.GroupId }}", ids[0]);
        context = context.replaceFirst("\\{\\{ \\.ArtifactId }}", ids[1]);
        context = context.replaceFirst("\\{\\{ \\.Version }}", ids[2]);
        context = context.replaceFirst("\\{\\{ \\.JavaVersion }}", javaVersion);
        context = context.replaceAll("\\{\\{ \\.CamelVersion }}", camelVersion);
        if (packageName != null) {
            context = context.replaceAll("\\{\\{ \\.MainClassname }}", packageName + "." + mainClassname);
        } else {
            context = context.replaceAll("\\{\\{ \\.MainClassname }}", mainClassname);
        }

        Properties prop = new CamelCaseOrderedProperties();
        RuntimeUtil.loadProperties(prop, settings);
        String repos = getMavenRepos(settings, prop, camelVersion);
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

        if (secretsRefresh) {
            if (secretsRefreshProviders != null) {
                List<String> providers = getSecretProviders();
                for (String provider : providers) {
                    switch (provider) {
                        case "aws":
                            sb.append("        <dependency>\n");
                            sb.append("            <groupId>").append("org.apache.camel").append("</groupId>\n");
                            sb.append("            <artifactId>").append("camel-aws-secrets-manager").append("</artifactId>\n");
                            sb.append("        </dependency>\n");
                            break;
                        case "gcp":
                            sb.append("        <dependency>\n");
                            sb.append("            <groupId>").append("org.apache.camel").append("</groupId>\n");
                            sb.append("            <artifactId>").append("camel-google-secret-manager")
                                    .append("</artifactId>\n");
                            sb.append("        </dependency>\n");
                            break;
                        case "azure":
                            sb.append("        <dependency>\n");
                            sb.append("            <groupId>").append("org.apache.camel").append("</groupId>\n");
                            sb.append("            <artifactId>").append("camel-azure-key-vault").append("</artifactId>\n");
                            sb.append("        </dependency>\n");
                            break;
                        default:
                            break;
                    }
                }
            }
        }

        context = context.replaceFirst("\\{\\{ \\.CamelDependencies }}", sb.toString());

        // include kubernetes?
        context = enrichMavenPomKubernetes(context, settings, profile);

        IOHelper.writeText(context, new FileOutputStream(pom, false));
    }

    protected String enrichMavenPomKubernetes(String context, File settings, File profile) throws Exception {
        StringBuilder sb1 = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();

        // is kubernetes included?
        Properties prop = new CamelCaseOrderedProperties();
        if (profile.exists()) {
            RuntimeUtil.loadProperties(prop, profile);
        }
        boolean jkube = prop.stringPropertyNames().stream().anyMatch(s -> s.startsWith("jkube."));
        if (jkube) {
            // include all jib/jkube/label properties
            for (String key : prop.stringPropertyNames()) {
                String value = prop.getProperty(key);
                boolean accept = key.startsWith("jkube.") || key.startsWith("jib.") || key.startsWith("label.");
                if (accept) {
                    sb1.append(String.format("        <%s>%s</%s>%n", key, value, key));
                }
            }

            InputStream is = ExportCamelMain.class.getClassLoader().getResourceAsStream("templates/main-kubernetes-pom.tmpl");
            String context2 = IOHelper.loadText(is);
            IOHelper.close(is);
            int port = httpServerPort(settings);
            if (port == -1) {
                port = 8080;
            }
            sb2.append(context2.replaceFirst("\\{\\{ \\.Port }}", String.valueOf(port)));
        }

        context = context.replace("{{ .CamelKubernetesProperties }}", sb1.toString());
        context = context.replace("{{ .CamelKubernetesPlugins }}", sb2.toString());
        return context;
    }

    @Override
    protected Set<String> resolveDependencies(File settings, File profile) throws Exception {
        Set<String> answer = super.resolveDependencies(settings, profile);

        // remove out of the box dependencies
        answer.removeIf(s -> s.contains("camel-core"));
        answer.removeIf(s -> s.contains("camel-main"));
        answer.removeIf(s -> s.contains("camel-health"));

        // if platform-http is included then we need to switch to use camel-platform-http-main as implementation
        if (answer.stream().anyMatch(s -> s.contains("camel-platform-http") && !s.contains("camel-platform-http-main"))) {
            answer.removeIf(s -> s.contains("org.apache.camel:camel-platform-http:"));
            answer.removeIf(s -> s.contains("org.apache.camel:camel-platform-http-vertx:"));
            // version does not matter
            answer.add("mvn:org.apache.camel:camel-platform-http-main:1.0-SNAPSHOT");
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

    @Override
    protected void prepareApplicationProperties(Properties properties) {
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
}
