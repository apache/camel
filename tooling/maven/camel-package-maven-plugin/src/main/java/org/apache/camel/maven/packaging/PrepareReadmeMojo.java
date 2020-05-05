/**
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
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.maven.packaging.model.ComponentModel;
import org.apache.camel.maven.packaging.model.DataFormatModel;
import org.apache.camel.maven.packaging.model.EipModel;
import org.apache.camel.maven.packaging.model.LanguageModel;
import org.apache.camel.maven.packaging.model.OtherModel;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.mvel2.templates.TemplateRuntime;

import static java.util.stream.Collectors.toSet;
import static org.apache.camel.maven.packaging.PackageHelper.loadText;
import static org.apache.camel.maven.packaging.PackageHelper.writeText;

/**
 * Prepares the readme.md files content up to date with all the artifacts that Apache Camel ships.
 */
@Mojo(name = "prepare-readme", threadSafe = true)
public class PrepareReadmeMojo extends AbstractMojo {

    /**
     * The maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    /**
     * The directory for EIPs (model) catalog
     */
    @Parameter(defaultValue = "${project.build.directory}/classes/org/apache/camel/catalog/models")
    protected File eipsDir;

    /**
     * The directory for camel-core
     */
    @Parameter(defaultValue = "${project.directory}/../../../camel-core")
    protected File readmeCoreDir;

    /**
     * Maven ProjectHelper.
     */
    @Component
    private MavenProjectHelper projectHelper;

    /**
     * Execute goal.
     *
     * @throws MojoExecutionException execution of the main class or one of the
     *                                threads it generated failed.
     * @throws MojoFailureException   something bad happened...
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        // update readme file in camel-core
        executeEipsReadme();
    }

    protected void executeEipsReadme() throws MojoExecutionException, MojoFailureException {
        Set<File> eipFiles = new TreeSet<>();

        if (eipsDir != null && eipsDir.isDirectory()) {
            File[] files = eipsDir.listFiles();
            if (files != null) {
                eipFiles.addAll(Arrays.asList(files));
            }
        }

        try {
            List<EipModel> models = new ArrayList<>();
            for (File file : eipFiles) {
                String json = loadText(new FileInputStream(file));
                EipModel model = generateEipModel(json);

                // we only want actual EIPs from the models
                if (model.getLabel().startsWith("eip")) {
                    models.add(model);
                }
            }

            // re-order the EIPs so we have them in different categories

            // sort the models
            Collections.sort(models, new EipComparator());

            // how many deprecated
            long deprecated = models.stream()
                    .filter(EipModel::isDeprecated)
                    .count();

            // update the big readme file in the core dir
            File file = new File(readmeCoreDir, "readme-eip.adoc");

            // update regular components
            boolean exists = file.exists();
            String changed = templateEips(models, deprecated);
            boolean updated = updateEips(file, changed);

            if (updated) {
                getLog().info("Updated readme-eip.adoc file: " + file);
            } else if (exists) {
                getLog().debug("No changes to readme-eip.adoc file: " + file);
            } else {
                getLog().warn("No readme-eip.adoc file: " + file);
            }

        } catch (IOException e) {
            throw new MojoFailureException("Error due " + e.getMessage(), e);
        }
    }

    private String templateEips(List<EipModel> models, long deprecated) throws MojoExecutionException {
        try {
            String template = loadText(UpdateReadmeMojo.class.getClassLoader().getResourceAsStream("readme-eips.mvel"));
            Map<String, Object> map = new HashMap<>();
            map.put("eips", models);
            map.put("numberOfDeprecated", deprecated);
            String out = (String) TemplateRuntime.eval(template, map, Collections.singletonMap("util", MvelHelper.INSTANCE));
            return out;
        } catch (Exception e) {
            throw new MojoExecutionException("Error processing mvel template. Reason: " + e, e);
        }
    }


    private boolean updateEips(File file, String changed) throws MojoExecutionException {
        if (!file.exists()) {
            return false;
        }

        try {
            String text = loadText(new FileInputStream(file));

            String existing = StringHelper.between(text, "// eips: START", "// eips: END");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = StringHelper.before(text, "// eips: START");
                    String after = StringHelper.after(text, "// eips: END");
                    text = before + "// eips: START\n" + changed + "\n// eips: END" + after;
                    writeText(file, text);
                    return true;
                }
            } else {
                getLog().warn("Cannot find markers in file " + file);
                getLog().warn("Add the following markers");
                getLog().warn("\t// eips: START");
                getLog().warn("\t// eips: END");
                return false;
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }
    }

    private static class EipComparator implements Comparator<EipModel> {

        @Override
        public int compare(EipModel o1, EipModel o2) {
            // lets sort by title
            return o1.getTitle().compareToIgnoreCase(o2.getTitle());
        }
    }

    private EipModel generateEipModel(String json) {
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("model", json, false);

        EipModel eip = new EipModel();
        eip.setName(JSonSchemaHelper.getSafeValue("name", rows));
        eip.setTitle(JSonSchemaHelper.getSafeValue("title", rows));
        eip.setDescription(JSonSchemaHelper.getSafeValue("description", rows));
        eip.setJavaType(JSonSchemaHelper.getSafeValue("javaType", rows));
        eip.setLabel(JSonSchemaHelper.getSafeValue("label", rows));
        eip.setDeprecated("true".equals(JSonSchemaHelper.getSafeValue("deprecated", rows)));
        eip.setDeprecationNote(JSonSchemaHelper.getSafeValue("deprecationNote", rows));
        eip.setInput("true".equals(JSonSchemaHelper.getSafeValue("input", rows)));
        eip.setOutput("true".equals(JSonSchemaHelper.getSafeValue("output", rows)));

        return eip;
    }

}
