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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Set;

import org.apache.camel.main.download.MavenGav;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.OrderedProperties;
import org.apache.commons.io.FileUtils;

class ExportQuarkus extends Export {

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

        context = context.replaceFirst("\\{\\{ \\.GroupId }}", ids[0]);
        context = context.replaceFirst("\\{\\{ \\.ArtifactId }}", ids[1]);
        context = context.replaceFirst("\\{\\{ \\.Version }}", ids[2]);
        context = context.replaceAll("\\{\\{ \\.QuarkusVersion }}", quarkusVersion);
        context = context.replaceFirst("\\{\\{ \\.JavaVersion }}", javaVersion);

        OrderedProperties prop = new OrderedProperties();
        prop.load(new FileInputStream(settings));
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
                gid = "org.apache.camel.quarkus";
                aid = aid.replace("camel-", "camel-quarkus-");
                v = null;
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

}
