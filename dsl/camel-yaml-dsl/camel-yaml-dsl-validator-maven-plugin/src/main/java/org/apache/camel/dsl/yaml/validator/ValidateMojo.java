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
package org.apache.camel.dsl.yaml.validator;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import com.networknt.schema.ValidationMessage;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.FileUtil;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;

@Mojo(name = "validate", threadSafe = true)
public class ValidateMojo extends AbstractMojo {

    private final YamlValidator validator = new YamlValidator();

    private static final String IGNORE_FILE = "application.yml";

    /**
     * The maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    /**
     * Skip the validation execution.
     */
    @Parameter(property = "camel.skipValidation", defaultValue = "false")
    private boolean skip;

    /**
     * Whether to fail if validation reported errors. By default, the plugin logs the errors at WARN level
     */
    @Parameter(property = "camel.failOnError", defaultValue = "false")
    private boolean failOnError;

    @Parameter(defaultValue = "${project.build.directory}")
    private String projectBuildDir;

    /**
     * Whether to only accept files with xxx.camel.yaml as file name. By default, all .yaml files are accepted.
     */
    @Parameter(property = "camel.onlyCamelYamlExt")
    private boolean onlyCamelYamlExt;

    /**
     * Whether to include test source code
     */
    @Parameter(property = "camel.includeTest", defaultValue = "false")
    private boolean includeTest;

    /**
     * To filter the names of YAML files to only include files matching any of the given list of patterns (wildcard and
     * regular expression). Multiple values can be separated by comma.
     */
    @Parameter(property = "camel.includes")
    private String includes;

    /**
     * To filter the names of YAML files to exclude files matching any of the given pattern in the list (wildcard and
     * regular expression). Multiple values can be separated by comma.
     */
    @Parameter(property = "camel.excludes")
    private String excludes;

    /**
     * yamlFiles in memory cache, useful for multi modules maven projects
     */
    private static final Set<File> yamlFiles = new LinkedHashSet<>();

    private final RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repositorySystemSession;

    @Inject
    public ValidateMojo(RepositorySystem repositorySystem) {
        this.repositorySystem = repositorySystem;
    }

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("skipping YAML DSL validation as per configuration");
            return;
        }

        // find all XML routes
        String ext = onlyCamelYamlExt ? ".camel.yaml" : ".yaml";
        findYamlRouters(yamlFiles, includeTest, ext, project);
        getLog().debug("Found " + yamlFiles.size() + " YAML files ...");

        Map<File, List<ValidationMessage>> reports = new LinkedHashMap<>();
        List<File> matched = new ArrayList<>();
        for (File file : yamlFiles) {
            if (matchFile(file)) {
                matched.add(file);
            }
        }
        if (!matched.isEmpty()) {
            getLog().info("Validating " + matched.size() + " YAML files ...");
            try {
                validator.init();
                for (File file : matched) {
                    var report = validateYamlRoute(file);
                    reports.put(file, report);
                }
            } catch (Exception e) {
                throw new MojoExecutionException(e);
            }
        }

        validateResults(reports);
    }

    private void validateResults(Map<File, List<ValidationMessage>> reports) throws MojoExecutionException {
        int count = errorCounts(reports);
        if (count == 0) {
            getLog().info("Validation success");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Validation error detected in ").append(count).append(" files\n\n");

        for (var e : reports.entrySet()) {
            String name = e.getKey().getName();
            var report = e.getValue();

            sb.append("\tFile: ").append(name).append("\n");
            for (var r : report) {
                sb.append("\t\t").append(r.toString()).append("\n");
            }
            sb.append("\n");
        }
        getLog().warn("\n\n" + sb + "\n\n");

        if (failOnError) {
            throw new MojoExecutionException(sb.toString());
        }
    }

    private int errorCounts(Map<File, List<ValidationMessage>> reports) {
        int count = 0;
        for (List<ValidationMessage> list : reports.values()) {
            if (!list.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private List<ValidationMessage> validateYamlRoute(File file) throws Exception {
        getLog().debug("Validating YAML DSL in file: " + file);
        return validator.validate(file);
    }

    private boolean matchFile(File file) {
        String no = FileUtil.onlyName(file.getName());
        if (IGNORE_FILE.equals(no)) {
            return false;
        }
        return matchRouteFile(file, excludes, includes, project);
    }

    public static boolean matchRouteFile(File file, String excludes, String includes, MavenProject project) {
        if (excludes == null && includes == null) {
            return true;
        } else if (excludes != null && fileListMatchesPattern(excludes, file, project)) {
            return false;
        } else {
            return includes != null ? fileListMatchesPattern(includes, file, project) : true;
        }
    }

    public static boolean fileListMatchesPattern(String fileList, File file, MavenProject project) {
        for (String fileName : fileList.split(",")) {
            fileName = fileName.trim();
            String fqn = stripRootPath(asRelativeFile(file.getAbsolutePath(), project), project);
            boolean match = PatternHelper.matchPattern(fqn, fileName) || PatternHelper.matchPattern(file.getName(), fileName);
            if (match) {
                return true;
            }
        }
        return false;
    }

    private static String stripRootPath(String name, MavenProject project) {
        for (String dir : project.getCompileSourceRoots()) {
            dir = asRelativeFile(dir, project);
            if (name.startsWith(dir)) {
                return name.substring(dir.length() + 1);
            }
        }

        for (String dir : project.getTestCompileSourceRoots()) {
            dir = asRelativeFile(dir, project);
            if (name.startsWith(dir)) {
                return name.substring(dir.length() + 1);
            }
        }

        for (Resource resource : project.getResources()) {
            String dir = asRelativeFile(resource.getDirectory(), project);
            if (name.startsWith(dir)) {
                return name.substring(dir.length() + 1);
            }
        }

        for (Resource resource : project.getTestResources()) {
            String dir = asRelativeFile(resource.getDirectory(), project);
            if (name.startsWith(dir)) {
                return name.substring(dir.length() + 1);
            }
        }

        return name;
    }

    private static String asRelativeFile(String name, MavenProject project) {
        String answer = name;
        String base = project.getBasedir().getAbsolutePath();
        if (name.startsWith(base)) {
            answer = name.substring(base.length());
            if (answer.startsWith(File.separator)) {
                answer = answer.substring(1);
            }
        }

        return answer;
    }

    private static void findYamlRouters(Set<File> yamlFiles, boolean includeTest, String ext, MavenProject project) {
        for (Resource dir : project.getResources()) {
            finYamlFiles(new File(dir.getDirectory()), ext, yamlFiles);
        }
        if (includeTest) {
            for (Resource dir : project.getTestResources()) {
                finYamlFiles(new File(dir.getDirectory()), ext, yamlFiles);
            }
        }
    }

    private static void finYamlFiles(File dir, String ext, Set<File> yamlFiles) {
        File[] files = dir.isDirectory() ? dir.listFiles() : null;
        if (files != null) {
            for (File file : files) {
                String name = file.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith(ext)) {
                    yamlFiles.add(file);
                } else if (file.isDirectory()) {
                    finYamlFiles(file, ext, yamlFiles);
                }
            }
        }
    }

}
