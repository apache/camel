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
package org.apache.camel.maven.packaging;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.tooling.model.ExampleModel;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.mvel2.templates.TemplateRuntime;

/**
 * Prepares the readme.md files content up to date with all the examples that
 * Apache Camel ships.
 */
@Mojo(name = "prepare-example", threadSafe = true)
public class PrepareExampleMojo extends AbstractMojo {

    /**
     * The maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    /**
     * Maven ProjectHelper.
     */
    @Component
    private MavenProjectHelper projectHelper;

    /**
     * Execute goal.
     *
     * @throws MojoExecutionException execution of the main class or one of the
     *             threads it generated failed.
     * @throws MojoFailureException something bad happened...
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        executeExamplesReadme();
    }

    protected void executeExamplesReadme() throws MojoExecutionException, MojoFailureException {
        Set<File> examples = new TreeSet<>();

        // only run in examples directory where the main readme.adoc file is
        // located
        String currentDir = Paths.get(".").normalize().toAbsolutePath().toString();
        if (!currentDir.endsWith("examples")) {
            return;
        }

        File dir = new File(".");
        File[] files = dir.listFiles();
        if (files != null) {
            examples.addAll(Arrays.asList(files));
        }

        try {
            List<ExampleModel> models = new ArrayList<>();

            for (File file : examples) {

                if (file.isDirectory() && file.getName().startsWith("camel-example")) {
                    File pom = new File(file, "pom.xml");
                    if (pom.exists()) {
                        String existing = FileUtils.readFileToString(pom, Charset.defaultCharset());

                        ExampleModel model = new ExampleModel();
                        model.setFileName(file.getName());

                        String name = Strings.between(existing, "<name>", "</name>");
                        String title = Strings.between(existing, "<title>", "</title>");
                        String description = Strings.between(existing, "<description>", "</description>");
                        String category = Strings.between(existing, "<category>", "</category>");

                        if (title != null) {
                            model.setTitle(title);
                        } else {
                            // fallback and use file name as title
                            model.setTitle(asTitle(file.getName()));
                        }
                        if (description != null) {
                            model.setDescription(description);
                        }
                        if (category != null) {
                            model.setCategory(category);
                        }
                        if (name != null && name.contains("(deprecated)")) {
                            model.setDeprecated("true");
                        } else {
                            model.setDeprecated("false");
                        }

                        // readme files is either readme.md or readme.adoc
                        String[] readmes = new File(file, ".").list((folder, fileName) -> fileName.toLowerCase().startsWith("readme"));
                        if (readmes != null && readmes.length == 1) {
                            model.setReadmeFileName(readmes[0]);
                        }

                        models.add(model);
                    }
                }
            }

            // sort the models
            models.sort(new ExampleComparator());

            // how many deprecated
            long deprecated = models.stream().filter(m -> "true".equals(m.getDeprecated())).count();

            // update the big readme file in the examples dir
            File file = new File(".", "README.adoc");

            // update regular components
            boolean exists = file.exists();
            String changed = templateExamples(models, deprecated);
            boolean updated = updateExamples(file, changed);

            if (updated) {
                getLog().info("Updated readme.adoc file: " + file);
            } else if (exists) {
                getLog().debug("No changes to readme.adoc file: " + file);
            } else {
                getLog().warn("No readme.adoc file: " + file);
            }

        } catch (IOException e) {
            throw new MojoFailureException("Error due " + e.getMessage(), e);
        }
    }

    private String templateExamples(List<ExampleModel> models, long deprecated) throws MojoExecutionException {
        try {
            String template = PackageHelper.loadText(UpdateReadmeMojo.class.getClassLoader().getResourceAsStream("readme-examples.mvel"));
            Map<String, Object> map = new HashMap<>();
            map.put("examples", models);
            map.put("numberOfDeprecated", deprecated);
            String out = (String)TemplateRuntime.eval(template, map, Collections.singletonMap("util", MvelHelper.INSTANCE));
            return out;
        } catch (Exception e) {
            throw new MojoExecutionException("Error processing mvel template. Reason: " + e, e);
        }
    }

    private boolean updateExamples(File file, String changed) throws MojoExecutionException {
        if (!file.exists()) {
            return false;
        }

        try {
            String text = PackageHelper.loadText(file);

            String existing = Strings.between(text, "// examples: START", "// examples: END");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = Strings.before(text, "// examples: START");
                    String after = Strings.after(text, "// examples: END");
                    text = before + "// examples: START\n" + changed + "\n// examples: END" + after;
                    PackageHelper.writeText(file, text);
                    return true;
                }
            } else {
                getLog().warn("Cannot find markers in file " + file);
                getLog().warn("Add the following markers");
                getLog().warn("\t// examples: START");
                getLog().warn("\t// examples: END");
                return false;
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }
    }

    private static class ExampleComparator implements Comparator<ExampleModel> {

        @Override
        public int compare(ExampleModel o1, ExampleModel o2) {
            // lets sort by category first and then file afterwards
            int num = o1.getCategory().compareToIgnoreCase(o2.getCategory());
            if (num == 0) {
                return o1.getFileName().compareToIgnoreCase(o2.getFileName());
            } else {
                return num;
            }
        }
    }

    private static String asTitle(String fileName) {
        // skip camel-example
        String answer = fileName.toLowerCase();
        if (answer.startsWith("camel-example-")) {
            answer = answer.substring(14);
        }
        answer = Strings.camelDashToTitle(answer);
        return answer;
    }

}
