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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.camel.main.MavenGav;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.OrderedProperties;
import org.apache.camel.util.StringHelper;
import picocli.CommandLine;

abstract class BaseExport extends CamelCommand {

    protected static final String BUILD_DIR = ".camel-jbang/work";

    protected static final String[] SETTINGS_PROP_SOURCE_KEYS = new String[] {
            "camel.main.routesIncludePattern",
            "camel.component.properties.location",
            "camel.component.kamelet.location",
            "camel.jbang.classpathFiles"
    };

    @CommandLine.Option(names = { "--gav" }, description = "The Maven group:artifact:version", required = true)
    protected String gav;

    @CommandLine.Option(names = { "--java-version" }, description = "Java version (11 or 17)", defaultValue = "11")
    protected String javaVersion;

    @CommandLine.Option(names = {
            "--kamelets-version" }, description = "Apache Camel Kamelets version", defaultValue = "0.8.1")
    protected String kameletsVersion;

    @CommandLine.Option(names = {
            "-dir",
            "--directory" }, description = "Directory where the project will be exported", defaultValue = ".")
    protected String exportDir;

    @CommandLine.Option(names = { "--fresh" }, description = "Make sure we use fresh (i.e. non-cached) resources")
    protected boolean fresh;

    public BaseExport(CamelJBangMain main) {
        super(main);
    }

    protected static String getScheme(String name) {
        int pos = name.indexOf(":");
        if (pos != -1) {
            return name.substring(0, pos);
        }
        return null;
    }

    protected Integer runSilently() throws Exception {
        Run run = new Run(getMain());
        Integer code = run.runSilent();
        return code;
    }

    protected Set<String> resolveDependencies(File settings) throws Exception {
        Set<String> answer = new TreeSet<>((o1, o2) -> {
            // favour org.apache.camel first
            boolean c1 = o1.contains("org.apache.camel:");
            boolean c2 = o2.contains("org.apache.camel:");
            if (c1 && !c2) {
                return -1;
            } else if (!c1 && c2) {
                return 1;
            }
            return o1.compareTo(o2);
        });
        List<String> lines = Files.readAllLines(settings.toPath());
        for (String line : lines) {
            if (line.startsWith("dependency=")) {
                String v = StringHelper.after(line, "dependency=");
                // skip endpointdsl as its already included, and core-languages and java-joor as
                // we let quarkus compile
                boolean skip = v == null || v.contains("org.apache.camel:camel-core-languages")
                        || v.contains("org.apache.camel:camel-java-joor-dsl")
                        || v.contains("camel-endpointdsl");
                if (!skip) {
                    answer.add(v);
                }
                if (v != null && v.contains("org.apache.camel:camel-kamelet")) {
                    // include kamelet catalog if we use kamelets
                    answer.add("org.apache.camel.kamelets:camel-kamelets:" + kameletsVersion);
                }
            }
        }

        // remove duplicate versions (keep first)
        Map<String, String> versions = new HashMap<>();
        Set<String> toBeRemoved = new HashSet<>();
        for (String line : answer) {
            MavenGav gav = MavenGav.parseGav(null, line);
            String ga = gav.getGroupId() + ":" + gav.getArtifactId();
            if (!versions.containsKey(ga)) {
                versions.put(ga, gav.getVersion());
            } else {
                toBeRemoved.add(line);
            }
        }
        answer.removeAll(toBeRemoved);

        return answer;
    }

    protected void copySourceFiles(
            File settings, File profile, File srcJavaDir, File srcResourcesDir, File srcCamelResourcesDir,
            String packageName)
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
                    boolean skip = profile.getName().equals(f); // skip copying profile
                    if (skip) {
                        continue;
                    }
                    String ext = FileUtil.onlyExt(f, true);
                    boolean java = "java".equals(ext);
                    boolean camel = "camel.main.routesIncludePattern".equals(k)
                            || "camel.component.kamelet.location".equals(k);
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
                            adjustJavaSourceFileLine(line, fos);
                            fos.write(line.getBytes(StandardCharsets.UTF_8));
                            fos.write("\n".getBytes(StandardCharsets.UTF_8));
                        }
                        IOHelper.close(fos);
                    }
                }
            }
        }
    }

    protected void adjustJavaSourceFileLine(String line, FileOutputStream fos) throws Exception {
        // noop
    }

    protected void copySettingsAndProfile(
            File settings, File profile, File targetDir,
            Function<Properties, Object> customize)
            throws Exception {
        OrderedProperties prop = new OrderedProperties();
        prop.load(new FileInputStream(settings));
        OrderedProperties prop2 = new OrderedProperties();
        if (profile.exists()) {
            prop2.load(new FileInputStream(profile));
        }

        for (Map.Entry<Object, Object> entry : prop.entrySet()) {
            String key = entry.getKey().toString();
            boolean skip = "camel.main.routesCompileDirectory".equals(key)
                    || "camel.main.routesReloadEnabled".equals(key);
            if (!skip && key.startsWith("camel.main")) {
                prop2.put(entry.getKey(), entry.getValue());
            }
        }

        if (customize != null) {
            customize.apply(prop2);
        }

        FileOutputStream fos = new FileOutputStream(new File(targetDir, "application.properties"), false);
        for (Map.Entry<Object, Object> entry : prop2.entrySet()) {
            String k = entry.getKey().toString();
            String v = entry.getValue().toString();

            boolean skip = k.startsWith("camel.jbang.");
            if (skip) {
                continue;
            }

            // files are now loaded in classpath
            v = v.replaceAll("file:", "classpath:");
            if ("camel.main.routesIncludePattern".equals(k)) {
                // camel.main.routesIncludePattern should remove all .java as we use spring boot
                // to load them
                // camel.main.routesIncludePattern should remove all file: classpath: as we copy
                // them to src/main/resources/camel where camel auto-load from
                v = Arrays.stream(v.split(","))
                        .filter(n -> !n.endsWith(".java") && !n.startsWith("file:") && !n.startsWith("classpath:"))
                        .collect(Collectors.joining(","));
            }
            if (!v.isBlank()) {
                String line = applicationPropertyLine(k, v);
                fos.write(line.getBytes(StandardCharsets.UTF_8));
                fos.write("\n".getBytes(StandardCharsets.UTF_8));
            }
        }
        IOHelper.close(fos);
    }

    protected String applicationPropertyLine(String key, String value) {
        return key + "=" + value;
    }

    protected static void safeCopy(File source, File target, boolean override) throws Exception {
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

    protected void safeCopy(InputStream source, File target) throws Exception {
        if (source == null) {
            return;
        }

        File dir = target.getParentFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        if (!target.exists()) {
            Files.copy(source, target.toPath());
        }
    }
}
