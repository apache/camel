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

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.main.download.MavenGav;
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

        System.out.println("Exporting as Camel Main project to: " + exportDir);

        // use a temporary work dir
        File buildDir = new File(BUILD_DIR);
        FileUtil.removeDir(buildDir);
        buildDir.mkdirs();

        // copy source files
        String packageName = ids[0] + "." + ids[1];
        File srcJavaDir = new File(BUILD_DIR, "src/main/java/" + packageName.replace('.', '/'));
        srcJavaDir.mkdirs();
        File srcResourcesDir = new File(BUILD_DIR, "src/main/resources");
        srcResourcesDir.mkdirs();
        File srcCamelResourcesDir = new File(BUILD_DIR, "src/main/resources/camel");
        srcCamelResourcesDir.mkdirs();
        copySourceFiles(settings, profile, srcJavaDir, srcResourcesDir, srcCamelResourcesDir, packageName);
        // copy from settings to profile
        copySettingsAndProfile(settings, profile, srcResourcesDir, prop -> {
            if (!prop.containsKey("camel.main.basePackageScan") && !prop.containsKey("camel.main.base-package-scan")) {
                prop.put("camel.main.basePackageScan", packageName);
            }
            return prop;
        });
        // create main class
        createMainClassSource(srcJavaDir, packageName, mainClassname);
        // gather dependencies
        Set<String> deps = resolveDependencies(settings, profile);
        // create pom
        createPom(settings, new File(BUILD_DIR, "pom.xml"), deps, packageName);
        // maven wrapper
        if (mavenWrapper) {
            copyMavenWrapper();
        }

        if (exportDir.equals(".")) {
            // we export to current dir so prepare for this by cleaning up existing files
            File target = new File(exportDir);
            for (File f : target.listFiles()) {
                if (!f.isHidden() && f.isDirectory()) {
                    FileUtil.removeDir(f);
                } else if (!f.isHidden() && f.isFile()) {
                    f.delete();
                }
            }
        }
        // copy to export dir and remove work dir
        FileUtils.copyDirectory(new File(BUILD_DIR), new File(exportDir));
        FileUtil.removeDir(new File(BUILD_DIR));

        return 0;
    }

    private void createPom(File settings, File pom, Set<String> deps, String packageName) throws Exception {
        String[] ids = gav.split(":");

        InputStream is = ExportCamelMain.class.getClassLoader().getResourceAsStream("templates/main-pom.tmpl");
        String context = IOHelper.loadText(is);
        IOHelper.close(is);

        CamelCatalog catalog = new DefaultCamelCatalog();
        String camelVersion = catalog.getCatalogVersion();

        context = context.replaceFirst("\\{\\{ \\.GroupId }}", ids[0]);
        context = context.replaceFirst("\\{\\{ \\.ArtifactId }}", ids[1]);
        context = context.replaceFirst("\\{\\{ \\.Version }}", ids[2]);
        context = context.replaceFirst("\\{\\{ \\.JavaVersion }}", javaVersion);
        context = context.replaceAll("\\{\\{ \\.CamelVersion }}", camelVersion);
        context = context.replaceAll("\\{\\{ \\.MainClassname }}", packageName + "." + mainClassname);

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
            if ("org.apache.camel".equals(gid)) {
                v = null; // use version from bom
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

        // remove out of the box dependencies
        answer.removeIf(s -> s.contains("camel-core"));
        answer.removeIf(s -> s.contains("camel-main"));
        answer.removeIf(s -> s.contains("camel-health"));
        answer.removeIf(s -> s.contains("camel-dsl-modeline"));

        // if platform-http is included then we need vertx as implementation
        if (answer.stream().anyMatch(s -> s.contains("camel-platform-http") && !s.contains("camel-platform-http-vertx"))) {
            // version does not matter
            answer.add("mvn:org.apache.camel:camel-platform-http-vertx:1.0-SNAPSHOT");
        }

        return answer;
    }

    private void createMainClassSource(File srcJavaDir, String packageName, String mainClassname) throws Exception {
        InputStream is = ExportCamelMain.class.getClassLoader().getResourceAsStream("templates/main.tmpl");
        String context = IOHelper.loadText(is);
        IOHelper.close(is);

        context = context.replaceFirst("\\{\\{ \\.PackageName }}", packageName);
        context = context.replaceAll("\\{\\{ \\.MainClassname }}", mainClassname);
        IOHelper.writeText(context, new FileOutputStream(srcJavaDir + "/" + mainClassname + ".java", false));
    }

    @Override
    protected void copySourceFiles(
            File settings, File profile, File srcJavaDir, File srcResourcesDir, File srcCamelResourcesDir, String packageName)
            throws Exception {

        super.copySourceFiles(settings, profile, srcJavaDir, srcResourcesDir, srcCamelResourcesDir, packageName);

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
