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
package org.apache.camel.dsl.jbang.core.commands.test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.citrusframework.TestSource;
import org.citrusframework.main.TestEngine;
import org.citrusframework.main.TestRunConfiguration;
import org.citrusframework.util.FileUtils;
import picocli.CommandLine;

/**
 * Runs Citrus test files directly using the Citrus test engine. This replaces the indirect JBang process call with a
 * direct in-process test execution.
 */
@CommandLine.Command(name = "run",
                     description = "Run Citrus tests")
public class TestRun extends CamelCommand {

    public static final String TEST_DIR = "test";
    public static final String WORK_DIR = ".citrus-jbang";

    private static final String[] ACCEPTED_FILE_EXT = { "xml", "yaml", "yml", "java", "groovy", "feature" };

    @CommandLine.Parameters(description = "Test files or directories to run", arity = "0..*")
    String[] files;

    @CommandLine.Option(names = { "--engine" }, description = "Test engine to use", defaultValue = "default")
    String engine;

    @CommandLine.Option(names = { "--verbose" }, description = "Enable verbose output")
    boolean verbose;

    @CommandLine.Option(names = { "-p", "--property" }, description = "Set system properties (key=value)")
    String[] properties;

    @CommandLine.Option(names = { "--includes" }, description = "Test name include patterns")
    String[] includes;

    public TestRun(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        Path currentDir = Paths.get(".");

        // Determine working directory (prefer test subfolder)
        Path workDir;
        if (currentDir.resolve(TEST_DIR).toFile().exists()) {
            workDir = currentDir.resolve(TEST_DIR);
        } else {
            workDir = currentDir;
        }

        // Set up system properties
        Map<String, String> props = new HashMap<>();
        if (properties != null) {
            for (String prop : properties) {
                String[] parts = prop.split("=", 2);
                if (parts.length == 2) {
                    props.put(parts[0], parts[1]);
                }
            }
        }

        // Apply system properties
        for (Map.Entry<String, String> entry : props.entrySet()) {
            System.setProperty(entry.getKey(), entry.getValue());
        }

        // Clean and create work directory
        File citrusWorkDir = new File(WORK_DIR);
        removeDir(citrusWorkDir);
        if (!citrusWorkDir.mkdirs()) {
            printer().println("Failed to create working directory " + WORK_DIR);
            return 1;
        }

        // Resolve test sources
        List<String> testSources = new ArrayList<>();
        resolveTests(files, testSources, workDir);

        // If no explicit files given, scan current directory
        if (testSources.isEmpty()) {
            resolveTests(new String[] { workDir.toString() }, testSources, workDir);
        }

        if (testSources.isEmpty()) {
            printer().println("No test files found");
            return 1;
        }

        // Build test run configuration
        TestRunConfiguration configuration = getRunConfiguration(testSources, workDir);

        // Look up and run the test engine
        TestEngine testEngine = TestEngine.lookup(configuration);
        testEngine.run();

        return 0;
    }

    /**
     * Creates a test run configuration from the resolved test sources.
     */
    protected TestRunConfiguration getRunConfiguration(List<String> testSources, Path workDir) {
        String ext = FileUtils.getFileExtension(testSources.get(0));

        TestRunConfiguration configuration = new TestRunConfiguration();
        configuration.setWorkDir(workDir.toAbsolutePath().toString());

        if (!"default".equals(engine)) {
            configuration.setEngine(engine);
        } else if ("feature".equals(ext)) {
            configuration.setEngine("cucumber");
        }

        configuration.setVerbose(verbose);

        if (includes != null) {
            configuration.setIncludes(includes);
        }

        // Add test sources
        List<TestSource> sources = new ArrayList<>();
        for (String source : testSources) {
            String sourceExt = FileUtils.getFileExtension(source);
            String baseName = FileUtils.getBaseName(new File(source).getName());
            sources.add(new TestSource(sourceExt, baseName, source));
        }
        configuration.setTestSources(sources);

        return configuration;
    }

    /**
     * Resolves test file paths from the given arguments. Handles both individual files and directories.
     */
    private void resolveTests(String[] testArgs, List<String> resolved, Path workDir) {
        if (testArgs == null) {
            return;
        }

        for (String arg : testArgs) {
            // Adjust path if it starts with test/ prefix and we're using the test subfolder
            String filePath = arg;
            if (filePath.startsWith(TEST_DIR + "/")) {
                filePath = filePath.substring((TEST_DIR + "/").length());
            }

            File resolved0 = workDir.resolve(filePath).toFile();
            if (!resolved0.exists()) {
                resolved0 = new File(filePath);
            }
            final File testFile = resolved0;

            if (testFile.isDirectory()) {
                // Scan directory for test files
                String[] dirFiles = testFile.list();
                if (dirFiles != null) {
                    String[] fullPaths = Arrays.stream(dirFiles)
                            .filter(f -> !skipFile(f))
                            .map(f -> new File(testFile, f).getPath())
                            .toArray(String[]::new);
                    resolveTests(fullPaths, resolved, workDir);
                }
            } else if (testFile.exists() && !skipFile(testFile.getName())) {
                resolved.add(testFile.getPath());
            }
        }
    }

    /**
     * Checks if a file should be skipped based on its extension.
     */
    private boolean skipFile(String fileName) {
        if (fileName.startsWith(".")) {
            return true;
        }
        String ext = FileUtils.getFileExtension(fileName);
        return Arrays.stream(ACCEPTED_FILE_EXT).noneMatch(e -> e.equals(ext));
    }

    /**
     * Recursively removes a directory.
     */
    private static void removeDir(File dir) {
        if (dir.exists()) {
            delete(dir);
        }
    }

    private static void delete(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    delete(child);
                }
            }
        }
        file.delete();
    }
}
