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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.tooling.model.BaseModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.model.LanguageModel;
import org.apache.camel.tooling.model.OtherModel;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

/**
 * Prepares the user guide to keep the table of content up to date with the
 * components, data formats, and languages.
 */
@Mojo(name = "prepare-user-guide", threadSafe = true)
public class PrepareUserGuideMojo extends AbstractMojo {

    /**
     * The maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    /**
     * The directory for components catalog
     */
    @Parameter(defaultValue = "${project.build.directory}/classes/org/apache/camel/catalog/components")
    protected File componentsDir;

    /**
     * The directory for data formats catalog
     */
    @Parameter(defaultValue = "${project.build.directory}/classes/org/apache/camel/catalog/dataformats")
    protected File dataFormatsDir;

    /**
     * The directory for languages catalog
     */
    @Parameter(defaultValue = "${project.build.directory}/classes/org/apache/camel/catalog/languages")
    protected File languagesDir;

    /**
     * The directory for others catalog
     */
    @Parameter(defaultValue = "${project.build.directory}/classes/org/apache/camel/catalog/others")
    protected File othersDir;

    /**
     * The directory for the user guide
     */
    @Parameter(defaultValue = "${project.directory}/../../../docs/user-manual/en")
    protected File userGuideDir;

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
        executeComponents();
        executeOthers();
        executeDataFormats();
        executeLanguages();
    }

    protected void executeComponents() throws MojoExecutionException, MojoFailureException {
        Set<File> componentFiles = new TreeSet<>();

        if (componentsDir != null && componentsDir.isDirectory()) {
            File[] files = componentsDir.listFiles();
            if (files != null) {
                componentFiles.addAll(Arrays.asList(files));
            }
        }

        try {
            List<ComponentModel> models = new ArrayList<>();
            for (File file : componentFiles) {
                String json = PackageHelper.loadText(file);
                ComponentModel model = JsonMapper.generateComponentModel(json);

                // filter out alternative schemas which reuses documentation
                boolean add = true;
                if (!Strings.isNullOrEmpty(model.getAlternativeSchemes())) {
                    String first = model.getAlternativeSchemes().split(",")[0];
                    if (!model.getScheme().equals(first)) {
                        add = false;
                    }
                }
                if (add) {
                    models.add(model);
                }
            }

            // sor the models
            models.sort(ComponentModel.compareTitle());

            // the summary file has the TOC
            File file = new File(userGuideDir, "SUMMARY.md");

            // update core components
            StringBuilder core = new StringBuilder();
            core.append("* Core Components\n");
            for (ComponentModel model : models) {
                if (model.getLabel().contains("core")) {
                    String line = "\t* " + link(model) + "\n";
                    core.append(line);
                }
            }
            boolean updated = updateCoreComponents(file, core.toString());

            // update regular components
            StringBuilder regular = new StringBuilder();
            regular.append("* Components\n");
            for (ComponentModel model : models) {
                if (!model.getLabel().contains("core")) {
                    String line = "\t* " + link(model) + "\n";
                    regular.append(line);
                }
            }
            updated |= updateComponents(file, regular.toString());

            if (updated) {
                getLog().info("Updated user guide file: " + file);
            } else {
                getLog().debug("No changes to user guide file: " + file);
            }

        } catch (IOException e) {
            throw new MojoFailureException("Error due " + e.getMessage(), e);
        }
    }

    protected void executeOthers() throws MojoExecutionException, MojoFailureException {
        Set<File> otherFiles = new TreeSet<>();

        if (othersDir != null && othersDir.isDirectory()) {
            File[] files = othersDir.listFiles();
            if (files != null) {
                otherFiles.addAll(Arrays.asList(files));
            }
        }

        List<OtherModel> models = new ArrayList<>();
        for (File file : otherFiles) {
            try {
                String json = PackageHelper.loadText(file);
                OtherModel model = JsonMapper.generateOtherModel(json);
                models.add(model);
            } catch (Exception e) {
                throw new MojoFailureException("Error reading file: " + file, e);
            }
        }

        // sort the models
        models.sort(BaseModel.compareTitle());

        // the summary file has the TOC
        File file = new File(userGuideDir, "SUMMARY.md");

        // update core components
        StringBuilder other = new StringBuilder();
        other.append("* Miscellaneous Components\n");
        for (OtherModel model : models) {
            String line = "\t* " + link(model) + "\n";
            other.append(line);
        }
        boolean updated = updateOthers(file, other.toString());

        if (updated) {
            getLog().info("Updated user guide file: " + file);
        } else {
            getLog().debug("No changes to user guide file: " + file);
        }
    }

    protected void executeDataFormats() throws MojoExecutionException, MojoFailureException {
        Set<File> dataFormatFiles = new TreeSet<>();

        if (dataFormatsDir != null && dataFormatsDir.isDirectory()) {
            File[] files = dataFormatsDir.listFiles();
            if (files != null) {
                dataFormatFiles.addAll(Arrays.asList(files));
            }
        }

        try {
            List<DataFormatModel> models = new ArrayList<>();
            for (File file : dataFormatFiles) {
                String json = PackageHelper.loadText(file);
                DataFormatModel model = JsonMapper.generateDataFormatModel(json);
                models.add(model);
            }

            // sort the models
            models.sort(BaseModel.compareTitle());

            // the summary file has the TOC
            File file = new File(userGuideDir, "SUMMARY.md");

            // update data formats
            StringBuilder dataFormats = new StringBuilder();
            dataFormats.append("* Data Formats\n");
            for (DataFormatModel model : models) {
                String line = "\t* " + link(model) + "\n";
                dataFormats.append(line);
            }
            boolean updated = updateDataFormats(file, dataFormats.toString());

            if (updated) {
                getLog().info("Updated user guide file: " + file);
            } else {
                getLog().debug("No changes to user guide file: " + file);
            }

        } catch (IOException e) {
            throw new MojoFailureException("Error due " + e.getMessage(), e);
        }
    }

    protected void executeLanguages() throws MojoExecutionException, MojoFailureException {
        Set<File> languageFiles = new TreeSet<>();

        if (languagesDir != null && languagesDir.isDirectory()) {
            File[] files = languagesDir.listFiles();
            if (files != null) {
                languageFiles.addAll(Arrays.asList(files));
            }
        }

        try {
            List<LanguageModel> models = new ArrayList<>();
            for (File file : languageFiles) {
                String json = PackageHelper.loadText(file);
                LanguageModel model = JsonMapper.generateLanguageModel(json);
                models.add(model);
            }

            // sort the models
            models.sort(BaseModel.compareTitle());

            // the summary file has the TOC
            File file = new File(userGuideDir, "SUMMARY.md");

            // update languages
            StringBuilder languages = new StringBuilder();
            languages.append("* Expression Languages\n");
            for (LanguageModel model : models) {
                String line = "\t* " + link(model) + "\n";
                languages.append(line);
            }
            boolean updated = updateLanguages(file, languages.toString());

            if (updated) {
                getLog().info("Updated user guide file: " + file);
            } else {
                getLog().debug("No changes to user guide file: " + file);
            }

        } catch (IOException e) {
            throw new MojoFailureException("Error due " + e.getMessage(), e);
        }
    }

    private boolean updateCoreComponents(File file, String changed) throws MojoExecutionException {
        if (!file.exists()) {
            return false;
        }

        try {
            String text = PackageHelper.loadText(file);

            String existing = Strings.between(text, "<!-- core components: START -->", "<!-- core components: END -->");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = Strings.before(text, "<!-- core components: START -->");
                    String after = Strings.after(text, "<!-- core components: END -->");
                    text = before + "<!-- core components: START -->\n" + changed + "\n<!-- core components: END -->" + after;
                    PackageHelper.writeText(file, text);
                    return true;
                }
            } else {
                getLog().warn("Cannot find markers in file " + file);
                getLog().warn("Add the following markers");
                getLog().warn("\t<!-- core components: START -->");
                getLog().warn("\t<!-- core components: END -->");
                return false;
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }
    }

    private boolean updateComponents(File file, String changed) throws MojoExecutionException {
        if (!file.exists()) {
            return false;
        }

        try {
            String text = PackageHelper.loadText(file);

            String existing = Strings.between(text, "<!-- components: START -->", "<!-- components: END -->");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = Strings.before(text, "<!-- components: START -->");
                    String after = Strings.after(text, "<!-- components: END -->");
                    text = before + "<!-- components: START -->\n" + changed + "\n<!-- components: END -->" + after;
                    PackageHelper.writeText(file, text);
                    return true;
                }
            } else {
                getLog().warn("Cannot find markers in file " + file);
                getLog().warn("Add the following markers");
                getLog().warn("\t<!-- components: START -->");
                getLog().warn("\t<!-- components: END -->");
                return false;
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }
    }

    private boolean updateOthers(File file, String changed) throws MojoExecutionException {
        if (!file.exists()) {
            return false;
        }

        try {
            String text = PackageHelper.loadText(file);

            String existing = Strings.between(text, "<!-- others: START -->", "<!-- others: END -->");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = Strings.before(text, "<!-- others: START -->");
                    String after = Strings.after(text, "<!-- others: END -->");
                    text = before + "<!-- others: START -->\n" + changed + "\n<!-- others: END -->" + after;
                    PackageHelper.writeText(file, text);
                    return true;
                }
            } else {
                getLog().warn("Cannot find markers in file " + file);
                getLog().warn("Add the following markers");
                getLog().warn("\t<!-- others: START -->");
                getLog().warn("\t<!-- others: END -->");
                return false;
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }
    }

    private boolean updateDataFormats(File file, String changed) throws MojoExecutionException {
        if (!file.exists()) {
            return false;
        }

        try {
            String text = PackageHelper.loadText(file);

            String existing = Strings.between(text, "<!-- dataformats: START -->", "<!-- dataformats: END -->");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = Strings.before(text, "<!-- dataformats: START -->");
                    String after = Strings.after(text, "<!-- dataformats: END -->");
                    text = before + "<!-- dataformats: START -->\n" + changed + "\n<!-- dataformats: END -->" + after;
                    PackageHelper.writeText(file, text);
                    return true;
                }
            } else {
                getLog().warn("Cannot find markers in file " + file);
                getLog().warn("Add the following markers");
                getLog().warn("\t<!-- dataformats: START -->");
                getLog().warn("\t<!-- dataformats: END -->");
                return false;
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }
    }

    private boolean updateLanguages(File file, String changed) throws MojoExecutionException {
        if (!file.exists()) {
            return false;
        }

        try {
            String text = PackageHelper.loadText(file);

            String existing = Strings.between(text, "<!-- languages: START -->", "<!-- languages: END -->");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = Strings.before(text, "<!-- languages: START -->");
                    String after = Strings.after(text, "<!-- languages: END -->");
                    text = before + "<!-- languages: START -->\n" + changed + "\n<!-- languages: END -->" + after;
                    PackageHelper.writeText(file, text);
                    return true;
                }
            } else {
                getLog().warn("Cannot find markers in file " + file);
                getLog().warn("Add the following markers");
                getLog().warn("\t<!-- languages: START -->");
                getLog().warn("\t<!-- languages: END -->");
                return false;
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }
    }

    private static String link(ComponentModel model) {
        return "[" + model.getTitle() + "](" + model.getScheme() + "-component.adoc)";
    }

    private static String link(OtherModel model) {
        return "[" + model.getTitle() + "](" + model.getName() + ".adoc)";
    }

    private static String link(DataFormatModel model) {
        // special for some data formats
        String name = asDataFormatName(model.getName());
        return "[" + model.getTitle() + "](" + name + "-dataformat.adoc)";
    }

    private static String link(LanguageModel model) {
        return "[" + model.getTitle() + "](" + model.getName() + "-language.adoc)";
    }

    private static String asDataFormatName(String name) {
        // special for some dataformats which share the same readme file
        if (name.startsWith("bindy")) {
            return "bindy";
        } else {
            return name;
        }
    }
}
