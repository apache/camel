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
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.model.LanguageModel;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import static org.apache.camel.tooling.util.PackageHelper.findCamelDirectory;

/**
 * Updates the MainHelper.java with the known component, data-format, and language names.
 */
@Mojo(name = "update-main-helper", threadSafe = true)
public class UpdateMainHelper extends AbstractGeneratorMojo {

    private static final String COMPONENT_START_TOKEN = "// COMPONENT-ENV-NAMES: START";
    private static final String COMPONENT_END_TOKEN = "// COMPONENT-ENV-NAMES: END";
    private static final String DATAFORMAT_START_TOKEN = "// DATAFORMAT-ENV-NAMES: START";
    private static final String DATAFORMAT_END_TOKEN = "// DATAFORMAT-ENV-NAMES: END";
    private static final String LANGUAGE_START_TOKEN = "// LANGUAGE-ENV-NAMES: START";
    private static final String LANGUAGE_END_TOKEN = "// LANGUAGE-ENV-NAMES: END";

    @Parameter(defaultValue = "${project.basedir}/src/generated/resources/org/apache/camel/catalog/")
    protected File jsonDir;

    @Parameter(defaultValue = "${project.basedir}/")
    protected File baseDir;

    /**
     * Execute goal.
     *
     * @throws MojoExecutionException execution of the main class or one of the threads it generated failed.
     * @throws MojoFailureException   something bad happened...
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        File camelDir = findCamelDirectory(baseDir, "core/camel-main");
        if (camelDir == null) {
            getLog().debug("No core/camel-main folder found, skipping execution");
            return;
        }

        List<Path> jsonFiles = PackageHelper.findJsonFiles(jsonDir.toPath()).collect(Collectors.toList());
        Set<String> components = new TreeSet<>();
        Set<String> dataformats = new TreeSet<>();
        Set<String> languages = new TreeSet<>();

        for (Path file : jsonFiles) {
            final String name = PackageHelper.asName(file);

            try {
                String json = PackageHelper.loadText(file.toFile());
                JsonObject obj = (JsonObject) Jsoner.deserialize(json);

                Map<String, Object> model;
                boolean isComponent = (model = obj.getMap("component")) != null;
                boolean isDataFormat = !isComponent && (model = obj.getMap("dataformat")) != null;
                boolean isLanguage = !isComponent && !isDataFormat && (model = obj.getMap("language")) != null;

                // only check these kind
                if (!isComponent && !isDataFormat && !isLanguage) {
                    continue;
                }

                if (isComponent) {
                    ComponentModel cm = JsonMapper.generateComponentModel(json);
                    components.add(asEnvName("CAMEL_COMPONENT_", cm.getScheme()));
                    if (cm.getAlternativeSchemes() != null) {
                        String[] aliases = cm.getAlternativeSchemes().split(",");
                        for (String alias : aliases) {
                            components.add(asEnvName("CAMEL_COMPONENT_", alias));
                        }
                    }
                } else if (isDataFormat) {
                    DataFormatModel dm = JsonMapper.generateDataFormatModel(json);
                    dataformats.add(asEnvName("CAMEL_DATAFORMAT_", dm.getName()));
                } else if (isLanguage) {
                    LanguageModel lm = JsonMapper.generateLanguageModel(json);
                    languages.add(asEnvName("CAMEL_LANGUAGE_", lm.getName()));
                }
            } catch (Exception e) {
                throw new MojoExecutionException("Error loading json: " + name, e);
            }
        }

        try {
            boolean updated = updateMainHelper(camelDir, components, COMPONENT_START_TOKEN, COMPONENT_END_TOKEN);
            updated |= updateMainHelper(camelDir, dataformats, DATAFORMAT_START_TOKEN, DATAFORMAT_END_TOKEN);
            updated |= updateMainHelper(camelDir, languages, LANGUAGE_START_TOKEN, LANGUAGE_END_TOKEN);
            if (updated) {
                getLog().info("Updated camel-main/src/main/java/org/apache/camel/main/MainHelper.java file");
            } else {
                getLog().debug("No changes to camel-main/src/main/java/org/apache/camel/main/MainHelper.java file");
            }

        } catch (Exception e) {
            throw new MojoExecutionException("Error updating MainHelper.java", e);
        }
    }

    private boolean updateMainHelper(File camelDir, Set<String> names, String startToken, String endToken) throws Exception {
        // load source code and update
        File java = new File(camelDir, "src/main/java/org/apache/camel/main/MainHelper.java");
        String text = PackageHelper.loadText(java);
        String spaces20 = "                    ";
        String spaces12 = "            ";

        StringJoiner sb = new StringJoiner(",\n");
        for (String name : names) {
            sb.add(spaces20 + "\"" + name + "\"");
        }
        String changed = sb.toString();

        String existing = Strings.between(text, startToken, endToken);
        if (existing != null) {
            // remove leading line breaks etc
            existing = existing.trim();
            changed = changed.trim();
            if (existing.equals(changed)) {
                return false;
            } else {
                String before = Strings.before(text, startToken);
                String after = Strings.after(text, endToken);
                text = before + startToken + "\n" + spaces20 + changed + "\n" + spaces12 + endToken + after;
                PackageHelper.writeText(java, text);
                return true;
            }
        }

        return false;
    }

    private static String asEnvName(String prefix, String name) {
        return prefix + name.toUpperCase(Locale.US).replace('-', '_');
    }

}
