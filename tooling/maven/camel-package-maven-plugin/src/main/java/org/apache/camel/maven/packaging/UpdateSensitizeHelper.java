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
import java.util.Arrays;
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
 * Updates the SensitiveHelper.java with the known secret option names
 */
@Mojo(name = "update-sensitive-helper", threadSafe = true)
public class UpdateSensitizeHelper extends AbstractGeneratorMojo {

    private static final String START_TOKEN = "// SENSITIVE-KEYS: START";
    private static final String END_TOKEN = "// SENSITIVE-KEYS: END";

    // extra keys that are regarded as secret which may not yet been in any component
    // they MUST be in lowercase and without a dash
    private static final String[] EXTRA_KEYS = new String[] { "apipassword", "apiuser", "apiusername" };

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
        File camelDir = findCamelDirectory(baseDir, "core/camel-util");
        if (camelDir == null) {
            getLog().debug("No core/camel-util folder found, skipping execution");
            return;
        }

        List<Path> jsonFiles = PackageHelper.findJsonFiles(jsonDir.toPath()).collect(Collectors.toList());
        Set<String> secrets = new TreeSet<>();

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
                    cm.getComponentOptions().forEach(o -> {
                        if (o.isSecret()) {
                            // key should be lower and without dashes
                            String key = o.getName().toLowerCase(Locale.ENGLISH);
                            key = key.replaceAll("-", "");
                            secrets.add(key);
                        }
                    });
                } else if (isDataFormat) {
                    DataFormatModel dm = JsonMapper.generateDataFormatModel(json);
                    dm.getOptions().forEach(o -> {
                        if (o.isSecret()) {
                            // key should be lower and without dashes
                            String key = o.getName().toLowerCase(Locale.ENGLISH);
                            key = key.replaceAll("-", "");
                            secrets.add(key);
                        }
                    });
                } else if (isLanguage) {
                    LanguageModel lm = JsonMapper.generateLanguageModel(json);
                    lm.getOptions().forEach(o -> {
                        if (o.isSecret()) {
                            // key should be lower and without dashes
                            String key = o.getName().toLowerCase(Locale.ENGLISH);
                            key = key.replaceAll("-", "");
                            secrets.add(key);
                        }
                    });
                }
            } catch (Exception e) {
                throw new MojoExecutionException("Error loading json: " + name, e);
            }
        }

        // add extra keys
        secrets.addAll(Arrays.asList(EXTRA_KEYS));

        getLog().info("There are " + secrets.size()
                      + " distinct secret options across all the Camel components/dataformats/languages");

        try {
            boolean updated = updateSensitiveHelper(camelDir, secrets);
            if (updated) {
                getLog().info("Updated camel-util/src/main/java/org/apache/camel/util/SensitiveUtils.java file");
            } else {
                getLog().debug("No changes to camel-util/src/main/java/org/apache/camel/util/SensitiveUtils.java file");
            }

        } catch (Exception e) {
            throw new MojoExecutionException("Error updating SensitiveUtils.java", e);
        }
    }

    private boolean updateSensitiveHelper(File camelDir, Set<String> secrets) throws Exception {
        // load source code and update
        File java = new File(camelDir, "src/main/java/org/apache/camel/util/SensitiveUtils.java");
        String text = PackageHelper.loadText(java);
        String spaces20 = "                    ";
        String spaces12 = "            ";

        StringJoiner sb = new StringJoiner(",\n");
        for (String name : secrets) {
            sb.add(spaces20 + "\"" + name + "\"");
        }
        String changed = sb.toString();

        String existing = Strings.between(text, START_TOKEN, END_TOKEN);
        if (existing != null) {
            // remove leading line breaks etc
            existing = existing.trim();
            changed = changed.trim();
            if (existing.equals(changed)) {
                return false;
            } else {
                String before = Strings.before(text, START_TOKEN);
                String after = Strings.after(text, END_TOKEN);
                text = before + START_TOKEN + "\n" + spaces20 + changed + "\n" + spaces12 + END_TOKEN + after;
                PackageHelper.writeText(java, text);
                return true;
            }
        }

        return false;
    }

}
