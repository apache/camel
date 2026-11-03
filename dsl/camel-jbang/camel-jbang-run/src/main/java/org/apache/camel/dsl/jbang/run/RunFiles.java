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
package org.apache.camel.dsl.jbang.run;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.run.common.Source;
import org.apache.camel.dsl.jbang.run.common.SourceHelper;
import org.apache.camel.dsl.jbang.run.common.SourceScheme;
import org.apache.camel.util.AntPathMatcher;
import org.apache.camel.util.FileUtil;
import org.apache.camel.xml.io.util.XmlStreamDetector;
import org.apache.camel.xml.io.util.XmlStreamInfo;

/**
 * What kind of file a file to run is: a route (or another Camel DSL file), a properties file, a script, a Groovy
 * source, a TLS file, a JKube file, a readme, or a file for the classpath; which files are skipped; and a Citrus test,
 * which is not run, as is a folder of only Citrus tests.
 *
 * @since 4.24
 */
public final class RunFiles {

    private static final String[] ACCEPTED_XML_ROOT_ELEMENT_NAMES = new String[] {
            "route", "routes",
            "routeTemplate", "routeTemplates",
            "templatedRoute", "templatedRoutes",
            "rest", "rests",
            "routeConfiguration",
            "beans", "blueprint", "camel"
    };

    private static final Set<String> ACCEPTED_XML_ROOT_ELEMENTS
            = new HashSet<>(Arrays.asList(ACCEPTED_XML_ROOT_ELEMENT_NAMES));

    private static final String[] ACCEPTED_YAML_ROOT_ELEMENT_NAMES = new String[] {
            "- from:", "- route:",
            "- routeTemplate:", "- route-template:",
            "- templatedRoute:", "templated-route:",
            "- routeConfiguration:", "- route-configuration:",
            "- rest:", "- beans:",
            "Pipe" // special for camel-k pipe
    };

    private static final Set<String> ACCEPTED_YAML_ROOT_ELEMENTS
            = new HashSet<>(Arrays.asList(ACCEPTED_YAML_ROOT_ELEMENT_NAMES));

    /**
     * The names of a Citrus test that no route has: name.citrus.yaml, name.citrus.it.yaml and any other
     * name.citrus.*.yaml, the same in .xml and .groovy, and a Cucumber feature.
     */
    private static final Pattern CITRUS_TEST = Pattern.compile(".+\\.citrus(?:[.-].+)?\\.(?:ya?ml|xml|groovy)|.+\\.feature");

    /** The names Citrus runs as a test that a route can have as well: NameTest.java, NameIT.xml... */
    private static final Pattern CITRUS_TEST_CLASS
            = Pattern.compile(".*(?:Test|IT|TestCase|ITCase)\\.java|.*(?:Test|IT)\\.xml");

    private RunFiles() {
    }

    /**
     * Whether the file is a Camel DSL file or a configuration file (rather than a file for the classpath).
     */
    public static boolean knownFile(String file) throws Exception {
        // always include kamelets
        String ext = FileUtil.onlyExt(file, false);
        if ("kamelet.yaml".equals(ext)) {
            return true;
        }
        String ext2 = FileUtil.onlyExt(file, true);
        if (ext2 != null) {
            SourceScheme sourceScheme = SourceScheme.fromUri(file);
            // special for yaml or xml, as we need to check if they have camel or not
            if (!sourceScheme.isRemote() && ("xml".equals(ext2) || "yaml".equals(ext2))) {
                // load content into memory
                Source source = SourceHelper.resolveSource(file);
                if ("xml".equals(ext2)) {
                    XmlStreamDetector detector = new XmlStreamDetector(
                            new ByteArrayInputStream(source.content().getBytes(StandardCharsets.UTF_8)));
                    XmlStreamInfo info = detector.information();
                    if (!info.isValid()) {
                        return false;
                    }
                    return ACCEPTED_XML_ROOT_ELEMENTS.contains(info.getRootElementName());
                } else {
                    // for yaml we only accept xxx.camel.yaml or xxx.yaml
                    if (!"camel.yaml".equals(ext) && !"yaml".equals(ext)) {
                        return false;
                    }
                    return ACCEPTED_YAML_ROOT_ELEMENTS.stream().anyMatch(tag -> source.content().contains(tag));
                }
            }
            // if the ext is an accepted file then we include it as a potential route
            // (java files need to be included as route to support pojos/processors with routes)
            return SourceHelper.isAcceptedSourceFile(ext2);
        } else {
            // assume match as it can be wildcard or dir
            return true;
        }
    }

    /**
     * Whether the file is not run: a build file, a hidden or excluded file, a directory, or a file without extension.
     *
     * @param openApiGeneratedFile the Rest DSL file generated from the OpenAPI spec, which is never skipped
     */
    public static boolean skipFile(String name, List<String> excludes, String openApiGeneratedFile) {
        if (name.startsWith("github:") || name.startsWith("https://github.com/")
                || name.startsWith("https://gist.github.com/")) {
            return false;
        }

        // flatten file
        name = FileUtil.stripPath(name);

        if (openApiGeneratedFile.equals(name)) {
            return false;
        }
        if ("pom.xml".equalsIgnoreCase(name)) {
            return true;
        }
        if ("build.gradle".equalsIgnoreCase(name)) {
            return true;
        }
        if ("camel-runner.jar".equals(name)) {
            return true;
        }
        if ("docker-compose.yml".equals(name) || "docker-compose.yaml".equals(name) || "compose.yml".equals(name)
                || "compose.yaml".equals(name)) {
            return true;
        }
        if (name.equals("NOTICE.txt") || name.equals("LICENSE.txt")) {
            return true;
        }

        if (name.startsWith(".")) {
            // relative file is okay, otherwise we assume it's a hidden file
            boolean ok = name.startsWith("..") || name.startsWith("./");
            if (!ok) {
                return true;
            }
        }

        // is the file excluded?
        if (isExcluded(name, excludes)) {
            return true;
        }

        // skip dirs
        if (!name.startsWith("classpath:")) {
            Path path = Path.of(name);
            if (Files.exists(path) && Files.isDirectory(path)) {
                return true;
            }
        }

        if (FileUtil.onlyExt(name) == null) {
            return true;
        }

        return false;
    }

    private static boolean isExcluded(String name, List<String> excludes) {
        if (excludes != null) {
            for (String pattern : excludes) {
                pattern = pattern.trim();
                if (AntPathMatcher.INSTANCE.match(pattern, name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether the properties file is used: an application-xxx.properties file only for the profile xxx.
     */
    public static boolean acceptPropertiesFile(String file, String profile) {
        String name = FileUtil.onlyName(file);
        if (profile != null && name.startsWith("application-")) {
            // only accept the file that matches the correct profile
            return ("application-" + profile).equals(name);
        }
        return true;
    }

    public static boolean isGroovyFile(String name) {
        return name.endsWith(".groovy");
    }

    public static boolean isScriptFile(String name) {
        return name.endsWith(".sh");
    }

    public static boolean isTlsFile(String name) {
        return name.endsWith(".crt") || name.endsWith(".key") || name.endsWith(".pem");
    }

    public static boolean isJKubeFile(String name) {
        return name.endsWith(".jkube.yaml") || name.endsWith(".jkube.yml");
    }

    public static boolean isReadmeFile(String name) {
        String on = FileUtil.onlyName(FileUtil.stripPath(name), true);
        return on.toLowerCase(Locale.ROOT).startsWith("readme");
    }

    /**
     * Whether the file is a Citrus test, such as test/orders.citrus.it.yaml: camel test run runs it, and no integration
     * loads a route from it. A Citrus test is named name.citrus.yaml, name.citrus.it.yaml or another
     * name.citrus.*.yaml, the same in .xml or .groovy, or is a Cucumber .feature. A Java or XML file with a test name,
     * such as OrderTest.java or OrderIT.xml, is a Citrus test when it uses Citrus, as a route can have such a name too.
     *
     * @param file the file, read only for a Java or XML test name
     */
    public static boolean isCitrusTest(Path file) {
        Path fileName = file.getFileName();
        if (fileName == null) {
            return false;
        }
        String name = fileName.toString();
        if (CITRUS_TEST.matcher(name.toLowerCase(Locale.ROOT)).matches()) {
            return true;
        }
        if (CITRUS_TEST_CLASS.matcher(name).matches() && Files.isRegularFile(file)) {
            try {
                // the package org.citrusframework, or the namespace of a test in the XML DSL of Citrus
                return Files.readString(file).contains("citrusframework");
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * The Citrus tests of a folder that holds nothing else camel run would load, such as the test folder of a project.
     * Empty when the folder holds no Citrus test, or a route or another source, a Camel application.properties, or a
     * subfolder (which can hold routes). Hidden files, data files and the other properties files, such as
     * citrus-application.properties, do not count.
     *
     * @param dir the folder to run (camel run test/, or --source-dir)
     */
    public static List<Path> citrusTestsOnly(Path dir) {
        List<Path> entries;
        try (Stream<Path> list = Files.list(dir)) {
            entries = list.sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
        List<Path> tests = new ArrayList<>();
        for (Path entry : entries) {
            String name = entry.getFileName().toString();
            if (name.startsWith(".")) {
                continue;
            }
            if (Files.isDirectory(entry)) {
                return List.of();
            }
            if (isCitrusTest(entry)) {
                tests.add(entry);
            } else if (name.endsWith(".properties")) {
                // the configuration of camel run, which can name the routes to load (camel.main.routesIncludePattern)
                if (name.equals("application.properties") || name.startsWith("application-")) {
                    return List.of();
                }
            } else if (isGroovyFile(name) || loadsRoutes(entry)) {
                return List.of();
            }
        }
        return tests;
    }

    /** Whether camel run would load the file as a route or another source; a file it cannot read is taken as one. */
    private static boolean loadsRoutes(Path file) {
        try {
            return knownFile(file.toString());
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * What a run says of the Citrus tests among its files: they are not routes, and camel test run runs them.
     *
     * @param tests the Citrus tests, as given to run
     */
    public static String citrusTestMessage(List<String> tests) {
        String files = String.join(" ", tests);
        if (tests.size() == 1) {
            return files + " is a Citrus test, not a route: run it with camel test run " + files;
        }
        return String.join(", ", tests) + " are Citrus tests, not routes: run them with camel test run " + files;
    }

    /**
     * The line a run prints for a Citrus test given with routes: the test is left out, and the routes run.
     *
     * @param test the Citrus test, as given to run
     */
    public static String citrusTestSkipMessage(String test) {
        return "Skipping " + test + ": a Citrus test, not a route (run it with camel test run " + test + ")";
    }
}
