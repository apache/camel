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

package org.apache.camel.dsl.jbang.run.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Stream;

import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;

import static org.apache.camel.dsl.jbang.run.common.CamelJBangConstants.JKUBE_MAVEN_PLUGIN_VERSION;

/**
 * Helper class provides export utilities for instance when copying files from temporary work folder to export directory
 * target.
 */
public final class ExportHelper {

    /**
     * The settings that the silent run writes in the work directory: the classified source files, the dependencies and
     * other details the export reads.
     */
    public static final String RUN_SETTINGS_FILE = "camel-jbang-run.properties";

    public static final int MAX_RESOURCE_DIR_FILES = 1000;

    private ExportHelper() {
        // prevent instantiation of utility class.
    }

    public static Path validateResourceDir(String dir) {
        Path path = Paths.get(dir).normalize();
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("--resource-dirs only accepts relative paths: " + dir);
        }
        if (path.getFileName() != null && path.getFileName().toString().equals("..")) {
            throw new IllegalArgumentException(
                    "--resource-dirs path must end with a named directory, not '..': " + dir);
        }
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("--resource-dirs path is not a directory: " + dir);
        }
        return path;
    }

    public static List<Path> walkResourceDir(Path dirPath) throws IOException {
        try (Stream<Path> walk = Files.walk(dirPath)) {
            List<Path> files = walk.filter(Files::isRegularFile).toList();
            if (files.size() > MAX_RESOURCE_DIR_FILES) {
                throw new IllegalArgumentException(
                        "--resource-dirs directory contains " + files.size()
                                                   + " files which exceeds the limit of " + MAX_RESOURCE_DIR_FILES + ": "
                                                   + dirPath);
            }
            return files;
        }
    }

    /**
     * Replaces the files with the (not hidden) files of the given directory.
     */
    public static void dirToFiles(String dir, List<String> files) {
        files.clear();
        boolean currentDir = Paths.get(dir).normalize().toString().isEmpty();
        String base = currentDir ? "" : FileUtil.stripTrailingSeparator(dir) + "/";
        try (Stream<Path> paths = Files.list(Paths.get(dir))) {
            paths.filter(p -> {
                try {
                    return Files.isRegularFile(p) && !Files.isHidden(p);
                } catch (IOException e) {
                    return false;
                }
            })
                    .forEach(f -> files.add(base + f.getFileName()));
        } catch (IOException e) {
            // Ignore
        }
    }

    public static void cleanExportDir(String dir) {
        cleanExportDir(dir, true);
    }

    public static void cleanExportDir(String dir, boolean keepHidden) {
        Path targetPath = Paths.get(dir);
        if (!Files.exists(targetPath)) {
            return;
        }

        try (Stream<Path> paths = Files.list(targetPath)) {
            paths.forEach(path -> {
                try {
                    boolean isHidden = Files.isHidden(path);
                    if (Files.isDirectory(path) && (!keepHidden || !isHidden)) {
                        PathUtils.deleteDirectory(path);
                    } else if (Files.isRegularFile(path) && (!keepHidden || !isHidden)) {
                        Files.deleteIfExists(path);
                    }
                } catch (IOException e) {
                    // Ignore
                }
            });
        } catch (IOException e) {
            // Ignore
        }
    }

    public static void safeCopy(
            ClassLoader classLoader, String scheme, Path source, Path target, boolean override)
            throws Exception {
        if ("classpath".equals(scheme)) {
            // in windows the source object contains the windows file separator
            // the getResourceAsStream cannot find the resource file inside the jar in this case
            // then we have to replace the windows file separator to unix style
            try (var ins = classLoader.getResourceAsStream(source.toString().replace("\\", "/"));
                 var outs = Files.newOutputStream(target)) {
                IOHelper.copy(ins, outs);
            }
        } else {
            safeCopy(source, target, override);
        }
    }

    public static void safeCopy(Path source, Path target, boolean override) throws Exception {
        if (!Files.exists(source)) {
            return;
        }

        if (Files.isDirectory(source)) {
            // flatten files if they are from a directory
            try (var stream = Files.list(source)) {
                stream.filter(Files::isRegularFile)
                        .forEach(child -> {
                            try {
                                safeCopy(child, target.resolve(child.getFileName()), override);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
            return;
        }

        if (!Files.exists(target)) {
            Files.copy(source, target);
        } else if (override) {
            Files.copy(source, target,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void safeCopy(InputStream source, Path target) throws Exception {
        if (source == null) {
            return;
        }

        Path dir = target.getParent();
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }

        if (!Files.exists(target)) {
            Files.copy(source, target);
        }
    }

    public static String exportPackageName(String groupId, String artifactId, String packageName) {
        if ("false".equalsIgnoreCase(packageName)) {
            return null; // package names are turned off (we should use root package)
        }
        if (packageName != null) {
            return packageName; // use specific package name
        }

        // compute package name based on Maven GAV
        // for package name it must be in lower-case and alpha/numeric
        String s = groupId + "." + artifactId;
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch == '.' || Character.isAlphabetic(ch) || Character.isDigit(ch)) {
                ch = Character.toLowerCase(ch);
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    public static Comparator<MavenGav> mavenGavComparator() {
        return new Comparator<MavenGav>() {
            @Override
            public int compare(MavenGav o1, MavenGav o2) {
                int r1 = rankGroupId(o1);
                int r2 = rankGroupId(o2);

                if (r1 > r2) {
                    return -1;
                } else if (r2 > r1) {
                    return 1;
                } else {
                    return o1.toString().compareTo(o2.toString());
                }
            }

            int rankGroupId(MavenGav o1) {
                String g1 = o1.getGroupId();
                if (g1 == null) {
                    return 0;
                }

                switch (g1) {
                    case "org.springframework.boot" -> {
                        return 30;
                    }
                    case "io.quarkus" -> {
                        return 30;
                    }
                    case "org.apache.camel.quarkus" -> {
                        String a1 = o1.getArtifactId();
                        // main/core/engine first
                        if ("camel-quarkus-core".equals(a1)) {
                            return 21;
                        }
                        return 20;
                    }
                    case "org.apache.camel.springboot" -> {
                        String a1 = o1.getArtifactId();
                        // main/core/engine first
                        if ("camel-spring-boot-starter".equals(a1)) {
                            return 21;
                        } else if ("camel-spring-boot-engine-starter".equals(a1)) {
                            return 22;
                        }
                        return 20;
                    }
                    case "org.apache.camel" -> {
                        String a1 = o1.getArtifactId();
                        // main/core/engine first
                        if ("camel-main".equals(a1)) {
                            return 11;
                        }
                        return 10;
                    }
                    case "org.apache.camel.kamelets" -> {
                        return 5;
                    }
                    default -> {
                        return 0;
                    }
                }
            }
        };
    }

    /**
     * Whether the directory is the current directory, or one that contains it, such as <tt>./</tt>, <tt>$PWD</tt> or
     * <tt>..</tt>: cleaning it before the export deletes the files of the project, so it is only cleaned with
     * --clean-dir.
     */
    public static boolean isCurrentDirOrParent(String dir) {
        Path path = Path.of(dir).toAbsolutePath().normalize();
        return Path.of("").toAbsolutePath().normalize().startsWith(path);
    }

    /**
     * The key=value properties of the given sources, where a later source overrides an earlier one.
     */
    public static Map<String, String> propertiesMap(Printer printer, String[]... propertySources) {
        Map<String, String> result = new LinkedHashMap<>();
        if (propertySources != null) {
            for (String[] props : Arrays.stream(propertySources).filter(Objects::nonNull).toList()) {
                for (String s : props) {
                    String[] kv = s.split("=", 2);
                    if (kv.length < 2) {
                        printer.printWarn("property '" + s + "' has a bad format (should be 'key=value'), skipping.");
                    } else {
                        result.put(kv[0], kv[1]);
                    }
                }
            }
        }
        return result;
    }

    /**
     * The given Maven build properties (prop=value) as properties.
     */
    public static Properties mapBuildProperties(List<String> buildProperties) {
        var answer = new Properties();
        buildProperties.stream()
                .filter(item -> !item.isEmpty())
                .map(item -> item.split("=", 2))
                .filter(toks -> toks.length == 2)
                .forEach(toks -> answer.setProperty(toks[0], toks[1]));
        return answer;
    }

    public static int httpManagementPort(Path settings) {
        try {
            List<String> lines = RuntimeUtil.loadPropertiesLines(settings);
            String port = lines.stream().filter(l -> l.startsWith("camel.management.port="))
                    .map(s -> StringHelper.after(s, "=")).findFirst().orElse("-1");
            return Integer.parseInt(port);
        } catch (Exception e) {
            // ignore
        }
        return -1;
    }

    public static String jkubeMavenPluginVersion(Path settings, Properties props) {
        String answer = null;
        if (props != null) {
            answer = props.getProperty(JKUBE_MAVEN_PLUGIN_VERSION);
        }
        if (answer == null) {
            try {
                List<String> lines = RuntimeUtil.loadPropertiesLines(settings);
                answer = lines.stream()
                        .filter(l -> l.startsWith(JKUBE_MAVEN_PLUGIN_VERSION + "=") || l.startsWith("jkube.version="))
                        .map(s -> StringHelper.after(s, "=")).findFirst().orElse(null);
            } catch (Exception e) {
                // ignore
            }
        }
        return answer != null ? answer : "1.20.0";
    }

    /**
     * The arguments of a command line typed as one text, such as <tt>--jvm-args</tt>: split at whitespace outside
     * double or single quotes, which are removed, and a backslash escapes the next character (outside single quotes).
     */
    public static List<String> splitArguments(String line) {
        List<String> answer = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean token = false;
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote == 0 && Character.isWhitespace(c)) {
                if (token) {
                    answer.add(current.toString());
                    current.setLength(0);
                    token = false;
                }
            } else if (c == '\\' && quote != '\'' && i + 1 < line.length()) {
                current.append(line.charAt(++i));
                token = true;
            } else if ((c == '"' || c == '\'') && (quote == 0 || quote == c)) {
                quote = quote == 0 ? c : 0;
                token = true;
            } else {
                current.append(c);
                token = true;
            }
        }
        if (token) {
            answer.add(current.toString());
        }
        return answer;
    }
}
