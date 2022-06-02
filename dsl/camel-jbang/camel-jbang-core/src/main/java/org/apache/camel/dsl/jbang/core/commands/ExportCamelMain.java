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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.main.MavenGav;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.OrderedProperties;
import org.apache.camel.util.StringHelper;
import org.apache.commons.io.FileUtils;
import picocli.CommandLine;

@CommandLine.Command(name = "standalone", description = "Export as standalone Camel Main project")
class ExportCamelMain extends CamelCommand {

    private static final String BUILD_DIR = ".camel-jbang/work";

    private static final String[] SETTINGS_PROP_SOURCE_KEYS = new String[] {
            "camel.main.routesIncludePattern",
            "camel.component.properties.location",
            "camel.component.kamelet.location",
            "camel.jbang.classpathFiles"
    };

    @CommandLine.Option(names = { "--gav" }, description = "The Maven group:artifact:version", required = true)
    private String gav;

    @CommandLine.Option(names = { "--main-classname" },
                        description = "The class name of the Camel Main application class",
                        defaultValue = "CamelApplication")
    private String mainClassname;

    @CommandLine.Option(names = { "--java-version" }, description = "Java version (11 or 17)",
                        defaultValue = "11")
    private String javaVersion;

    @CommandLine.Option(names = { "--kamelets-version" }, description = "Apache Camel Kamelets version",
                        defaultValue = "0.8.1")
    private String kameletsVersion;

    @CommandLine.Option(names = { "-dir", "--directory" }, description = "Directory where the project will be exported",
                        defaultValue = ".")
    private String exportDir;

    @CommandLine.Option(names = { "--fresh" }, description = "Make sure we use fresh (i.e. non-cached) resources")
    private boolean fresh;

    public ExportCamelMain(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer call() throws Exception {
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
        copySourceFiles(settings, srcJavaDir, srcResourcesDir, srcCamelResourcesDir, packageName);
        // copy from settings to profile
        copySettingsAndProfile(settings, profile, srcResourcesDir, packageName);
        // create main class
        createMainClassSource(srcJavaDir, packageName, mainClassname);
        // gather dependencies
        Set<String> deps = resolveDependencies(settings);
        // create pom
        createPom(new File(BUILD_DIR, "pom.xml"), deps, packageName);

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

    private void createPom(File pom, Set<String> deps, String packageName) throws Exception {
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
        context = context.replaceFirst("\\{\\{ \\.MainClassname }}", packageName + "." + mainClassname);

        StringBuilder sb = new StringBuilder();
        for (String dep : deps) {
            MavenGav gav = MavenGav.parseGav(null, dep);
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

    private Set<String> resolveDependencies(File settings) throws Exception {
        Set<String> answer = new TreeSet<>();
        List<String> lines = Files.readAllLines(settings.toPath());
        for (String line : lines) {
            if (line.startsWith("dependency=")) {
                String v = StringHelper.after(line, "dependency=");
                // skip core-languages and java-joor as we let spring boot compile
                boolean skip = v == null || v.contains("org.apache.camel:camel-core-languages")
                        || v.contains("org.apache.camel:camel-java-joor-dsl");
                if (!skip) {
                    answer.add(v);
                }
                if (v != null && v.contains("org.apache.camel:camel-kamelet")) {
                    // include kamelet catalog if we use kamelets
                    answer.add("org.apache.camel.kamelets:camel-kamelets:" + kameletsVersion);
                }
            }
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

    private Integer runSilently() throws Exception {
        Run run = new Run(getMain());
        Integer code = run.runSilent();
        return code;
    }

    private void copySourceFiles(
            File settings, File srcJavaDir, File srcResourcesDir, File srcCamelResourcesDir, String packageName)
            throws Exception {
        // read the settings file and find the files to copy
        OrderedProperties prop = new OrderedProperties();
        prop.load(new FileInputStream(settings));

        for (String k : SETTINGS_PROP_SOURCE_KEYS) {
            String files = prop.getProperty(k);
            if (files != null) {
                for (String f : files.split(",")) {
                    String scheme = getScheme(f);
                    if (scheme != null) {
                        f = f.substring(scheme.length() + 1);
                    }
                    String ext = FileUtil.onlyExt(f, true);
                    boolean java = "java".equals(ext);
                    boolean camel = "camel.main.routesIncludePattern".equals(k) || "camel.component.kamelet.location".equals(k);
                    File target = java ? srcJavaDir : camel ? srcCamelResourcesDir : srcResourcesDir;
                    File source = new File(f);
                    File out = new File(target, source.getName());
                    safeCopy(source, out, true);
                    if (java) {
                        // need to append package name in java source file
                        List<String> lines = Files.readAllLines(out.toPath());
                        lines.add(0, "");
                        lines.add(0, "package " + packageName + ";");
                        FileOutputStream fos = new FileOutputStream(out);
                        for (String line : lines) {
                            fos.write(line.getBytes(StandardCharsets.UTF_8));
                            fos.write("\n".getBytes(StandardCharsets.UTF_8));
                        }
                        IOHelper.close(fos);
                    }
                }
            }
        }
        // log4j configuration
        InputStream is = ExportCamelMain.class.getResourceAsStream("/log4j2.properties");
        safeCopy(is, new File(srcResourcesDir, "log4j2.properties"));
        is = ExportCamelMain.class.getResourceAsStream("/log4j2.component.properties");
        safeCopy(is, new File(srcResourcesDir, "log4j2.component.properties"));
    }

    private void copySettingsAndProfile(File settings, File profile, File targetDir, String packageName) throws Exception {
        OrderedProperties prop = new OrderedProperties();
        prop.load(new FileInputStream(settings));
        OrderedProperties prop2 = new OrderedProperties();
        if (profile.exists()) {
            prop2.load(new FileInputStream(profile));
        }

        for (Map.Entry<Object, Object> entry : prop.entrySet()) {
            String key = entry.getKey().toString();
            boolean skip = "camel.main.routesCompileDirectory".equals(key) || "camel.main.routesReloadEnabled".equals(key);
            if (!skip && key.startsWith("camel.main")) {
                prop2.put(entry.getKey(), entry.getValue());
            }
        }
        // should have package name set for package scan
        if (!prop2.containsKey("camel.main.basePackageScan") && !prop2.containsKey("camel.main.base-package-scan")) {
            prop2.put("camel.main.basePackageScan", packageName);
        }

        FileOutputStream fos = new FileOutputStream(new File(targetDir, profile.getName()), false);
        for (Map.Entry<Object, Object> entry : prop2.entrySet()) {
            String k = entry.getKey().toString();
            String v = entry.getValue().toString();
            // files are now loaded in classpath
            v = v.replaceAll("file:", "classpath:");
            if ("camel.main.routesIncludePattern".equals(k)) {
                // camel.main.routesIncludePattern should remove all .java as they become regular java classes
                // camel.main.routesIncludePattern should remove all file: classpath: as we copy them to src/main/resources/camel where camel auto-load from
                v = Arrays.stream(v.split(","))
                        .filter(n -> !n.endsWith(".java") && !n.startsWith("file:") && !n.startsWith("classpath:"))
                        .collect(Collectors.joining(","));
            }
            if (!v.isBlank()) {
                String line = k + "=" + v;
                fos.write(line.getBytes(StandardCharsets.UTF_8));
                fos.write("\n".getBytes(StandardCharsets.UTF_8));
            }
        }
        IOHelper.close(fos);
    }

    private void safeCopy(InputStream source, File target) throws Exception {
        if (source == null) {
            return;
        }

        if (!target.exists()) {
            Files.copy(source, target.toPath());
        }
    }

    private static void safeCopy(File source, File target, boolean override) throws Exception {
        if (!source.exists()) {
            return;
        }

        if (!target.exists()) {
            Files.copy(source.toPath(), target.toPath());
        } else if (override) {
            Files.copy(source.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String getScheme(String name) {
        int pos = name.indexOf(":");
        if (pos != -1) {
            return name.substring(0, pos);
        }
        return null;
    }

}
