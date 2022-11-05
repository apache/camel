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
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.catalog.RuntimeProvider;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.dsl.jbang.core.common.XmlHelper;
import org.apache.camel.main.KameletMain;
import org.apache.camel.main.download.MavenArtifact;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.main.download.MavenGav;
import org.apache.camel.tooling.model.ArtifactModel;
import org.apache.camel.util.CamelCaseOrderedProperties;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.commons.io.FileUtils;

class ExportQuarkus extends Export {

    private static final String DEFAULT_CAMEL_CATALOG = "org.apache.camel.catalog.DefaultCamelCatalog";
    private static final String QUARKUS_CATALOG_PROVIDER = "org.apache.camel.catalog.quarkus.QuarkusRuntimeProvider";

    private String camelVersion;
    private String camelQuarkusVersion;

    public ExportQuarkus(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer export() throws Exception {
        String[] ids = gav.split(":");
        if (ids.length != 3) {
            System.out.println("--gav must be in syntax: groupId:artifactId:version");
            return 1;
        }

        File profile = new File(getProfile() + ".properties");

        // the settings file has information what to export
        File settings = new File(Run.WORK_DIR + "/" + Run.RUN_SETTINGS_FILE);
        if (fresh || !settings.exists()) {
            // allow to automatic build
            System.out.println("Generating fresh run data");
            int silent = runSilently();
            if (silent != 0) {
                return silent;
            }
        } else {
            System.out.println("Reusing existing run data");
        }

        System.out.println("Exporting as Quarkus project to: " + exportDir);

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
        copySettingsAndProfile(settings, profile, srcResourcesDir, prop -> {
            // turn off modeline as it is not supported in quarkus
            prop.remove("camel.main.modeline");
            return prop;
        });
        // copy docker files
        copyDockerFiles();
        // gather dependencies
        Set<String> deps = resolveDependencies(settings, profile);
        // create pom
        createPom(settings, new File(BUILD_DIR, "pom.xml"), deps);
        // maven wrapper
        if (mavenWrapper) {
            copyMavenWrapper();
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

    private void createPom(File settings, File pom, Set<String> deps) throws Exception {
        String[] ids = gav.split(":");

        InputStream is = ExportQuarkus.class.getClassLoader().getResourceAsStream("templates/quarkus-pom.tmpl");
        String context = IOHelper.loadText(is);
        IOHelper.close(is);

        CamelCatalog catalog = loadQuarkusCatalog();
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

        StringBuilder sb = new StringBuilder();
        for (String dep : deps) {
            MavenGav gav = MavenGav.parseGav(dep);
            String gid = gav.getGroupId();
            String aid = gav.getArtifactId();
            String v = gav.getVersion();
            // transform to camel-quarkus extension GAV
            if ("org.apache.camel".equals(gid)) {
                String qaid = aid.replace("camel-", "camel-quarkus-");
                ArtifactModel<?> am = catalog.modelFromMavenGAV("org.apache.camel.quarkus", qaid, null);
                if (am != null) {
                    // use quarkus extension
                    gid = am.getGroupId();
                    aid = am.getArtifactId();
                    v = null; // uses BOM so version should not be included
                } else {
                    // there is no quarkus extension so use plain camel
                    v = camelVersion;
                }
            }
            sb.append("        <dependency>\n");
            sb.append("            <groupId>").append(gid).append("</groupId>\n");
            sb.append("            <artifactId>").append(aid).append("</artifactId>\n");
            if (v != null) {
                sb.append("            <version>").append(v).append("</version>\n");
            }
            sb.append("        </dependency>\n");
        }
        context = context.replaceFirst("\\{\\{ \\.CamelDependencies }}", sb.toString());

        IOHelper.writeText(context, new FileOutputStream(pom, false));
    }

    @Override
    protected Set<String> resolveDependencies(File settings, File profile) throws Exception {
        Set<String> answer = super.resolveDependencies(settings, profile);

        answer.removeIf(s -> s.contains("camel-core"));
        answer.removeIf(s -> s.contains("camel-platform-http"));
        answer.removeIf(s -> s.contains("camel-microprofile-health"));
        answer.removeIf(s -> s.contains("camel-dsl-modeline"));

        return answer;
    }

    private CamelCatalog loadQuarkusCatalog() {
        CamelCatalog answer = new DefaultCamelCatalog(true);

        // use kamelet-main to dynamic download dependency via maven
        KameletMain main = new KameletMain();
        try {
            main.start();

            // shrinkwrap does not return POM file as result (they are hardcoded to be filtered out)
            // so after this we download a JAR and then use its File location to compute the file for the downloaded POM
            MavenDependencyDownloader downloader = main.getCamelContext().hasService(MavenDependencyDownloader.class);
            downloader.downloadArtifact("io.quarkus.platform", "quarkus-camel-bom:pom", quarkusVersion);
            MavenArtifact ma = downloader.downloadArtifact("io.quarkus", "quarkus-core", quarkusVersion);
            if (ma != null && ma.getFile() != null) {
                String name = ma.getFile().getAbsolutePath();
                name = name.replace("io/quarkus/quarkus-core", "io/quarkus/platform/quarkus-camel-bom");
                name = name.replace("quarkus-core", "quarkus-camel-bom");
                name = name.replace(".jar", ".pom");
                File file = new File(name);
                if (file.exists()) {
                    DocumentBuilderFactory dbf = XmlHelper.createDocumentBuilderFactory();
                    DocumentBuilder db = dbf.newDocumentBuilder();
                    Document dom = db.parse(file);

                    // grab what exact camelVersion and camelQuarkusVersion we are using
                    NodeList nl = dom.getElementsByTagName("dependency");
                    for (int i = 0; i < nl.getLength(); i++) {
                        Element node = (Element) nl.item(i);
                        String g = node.getElementsByTagName("groupId").item(0).getTextContent();
                        String a = node.getElementsByTagName("artifactId").item(0).getTextContent();
                        if ("org.apache.camel".equals(g) && "camel-core-engine".equals(a)) {
                            camelVersion = node.getElementsByTagName("version").item(0).getTextContent();
                        } else if ("org.apache.camel.quarkus".equals(g) && "camel-quarkus-catalog".equals(a)) {
                            camelQuarkusVersion = node.getElementsByTagName("version").item(0).getTextContent();
                        }
                    }
                }
            }

            if (camelQuarkusVersion != null) {
                // download camel-quarkus-catalog we use to know if we have an extension or not
                downloader.downloadDependency("org.apache.camel.quarkus", "camel-quarkus-catalog", camelQuarkusVersion);

                Class<RuntimeProvider> clazz = main.getCamelContext().getClassResolver().resolveClass(QUARKUS_CATALOG_PROVIDER,
                        RuntimeProvider.class);
                if (clazz != null) {
                    RuntimeProvider provider = main.getCamelContext().getInjector().newInstance(clazz);
                    if (provider != null) {
                        // re-create answer with the classloader that loaded spring-boot to be able to load resources in this catalog
                        Class<CamelCatalog> clazz2
                                = main.getCamelContext().getClassResolver().resolveClass(DEFAULT_CAMEL_CATALOG,
                                        CamelCatalog.class);
                        answer = main.getCamelContext().getInjector().newInstance(clazz2);
                        answer.setRuntimeProvider(provider);
                        // use classloader that loaded spring-boot provider to ensure we can load its resources
                        answer.getVersionManager().setClassLoader(main.getCamelContext().getApplicationContextClassLoader());
                        answer.enableCache();
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        } finally {
            main.stop();
        }

        return answer;
    }

}
