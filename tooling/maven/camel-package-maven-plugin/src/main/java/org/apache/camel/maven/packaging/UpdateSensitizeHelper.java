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
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.model.LanguageModel;
import org.apache.camel.tooling.model.PojoBeanModel;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;

import static org.apache.camel.tooling.util.PackageHelper.findCamelDirectory;

/**
 * Updates the SensitiveHelper.java with the known secret option names
 */
@Mojo(name = "update-sensitive-helper", threadSafe = true)
public class UpdateSensitizeHelper extends AbstractGeneratorMojo {

    private static final String KEYS_START_TOKEN = "// SENSITIVE-KEYS: START";
    private static final String KEYS_END_TOKEN = "// SENSITIVE-KEYS: END";
    private static final String WHOLE_KEYS_START_TOKEN = "// SENSITIVE-WHOLE-KEYS: START";
    private static final String WHOLE_KEYS_END_TOKEN = "// SENSITIVE-WHOLE-KEYS: END";
    private static final String PATTERN_START_TOKEN = "// SENSITIVE-PATTERN: START";
    private static final String PATTERN_END_TOKEN = "// SENSITIVE-PATTERN: END";

    // extra keys that are regarded as secret which may not yet been in any component
    // they MUST be in lowercase and without a dash
    private static final String[] EXTRA_KEYS
            = new String[] {
                    "apipassword", "apiuser", "apiusername", "api_key", "api_secret", "callbacksecret", "secret",
                    "keystorePassword" };

    // the camel-main configuration options (camel.server, camel.vault and others), whose secret options are matched by
    // their whole key, as a component can have an option with the same name that is not secret (such as tenantId)
    private static final String MAIN_METADATA = "camel-main-configuration-metadata.json";

    @Parameter(defaultValue = "${project.basedir}/src/generated/resources/org/apache/camel/catalog/")
    protected File jsonDir;

    @Parameter(defaultValue = "${project.basedir}/")
    protected File baseDir;

    @Inject
    public UpdateSensitizeHelper(MavenProjectHelper projectHelper, BuildContext buildContext) {
        super(projectHelper, buildContext);
    }

    /**
     * Execute goal.
     *
     * @throws MojoExecutionException execution of the main class or one of the threads it generated failed.
     */
    @Override
    public void execute() throws MojoExecutionException {
        File camelDir = findCamelDirectory(baseDir, "core/camel-util");
        if (camelDir == null) {
            getLog().debug("No core/camel-util folder found, skipping execution");
            return;
        }
        List<Path> jsonFiles;
        try (Stream<Path> stream = PackageHelper.findJsonFiles(jsonDir.toPath())) {
            jsonFiles = stream.toList();
        }
        Set<String> secrets = new TreeSet<>();
        // the whole keys of the camel-main options flagged secret (such as camel.vault.azure.tenantid)
        Set<String> wholeKeys = new TreeSet<>();

        for (Path file : jsonFiles) {
            final String name = PackageHelper.asName(file);

            try {
                String json = PackageHelper.loadText(file.toFile());
                Object jo = Jsoner.deserialize(json);
                JsonObject obj;
                if (jo instanceof JsonObject) {
                    obj = (JsonObject) jo;
                } else {
                    continue;
                }

                boolean isComponent = obj.getMap("component") != null;
                boolean isDataFormat = !isComponent && obj.getMap("dataformat") != null;
                boolean isLanguage = !isComponent && !isDataFormat && obj.getMap("language") != null;
                boolean isBean = !isComponent && !isDataFormat && !isLanguage && obj.getMap("bean") != null;
                boolean isMain = MAIN_METADATA.equals(file.getFileName().toString());

                // only check these kind
                if (!isComponent && !isDataFormat && !isLanguage && !isBean && !isMain) {
                    continue;
                }

                if (isMain) {
                    // the option names are whole keys (such as camel.server.jwtKeystorePassword)
                    Collection<?> properties = obj.getCollection("properties");
                    if (properties != null) {
                        for (Object o : properties) {
                            Map<?, ?> option = (Map<?, ?>) o;
                            if (Boolean.TRUE.equals(option.get("secret"))) {
                                // key should be lower and without dashes
                                String key = option.get("name").toString().toLowerCase(Locale.ENGLISH);
                                key = key.replace("-", "");
                                wholeKeys.add(key);
                            }
                        }
                    }
                } else if (isBean) {
                    PojoBeanModel pm = JsonMapper.generatePojoBeanModel(obj);
                    pm.getOptions().forEach(o -> {
                        if (o.isSecret()) {
                            // key should be lower and without dashes
                            String key = o.getName().toLowerCase(Locale.ENGLISH);
                            key = key.replace("-", "");
                            secrets.add(key);
                        }
                    });
                } else if (isComponent) {
                    ComponentModel cm = JsonMapper.generateComponentModel(json);
                    cm.getComponentOptions().forEach(o -> {
                        if (o.isSecret()) {
                            // key should be lower and without dashes
                            String key = o.getName().toLowerCase(Locale.ENGLISH);
                            key = key.replace("-", "");
                            secrets.add(key);
                        }
                    });
                } else if (isDataFormat) {
                    DataFormatModel dm = JsonMapper.generateDataFormatModel(json);
                    dm.getOptions().forEach(o -> {
                        if (o.isSecret()) {
                            // key should be lower and without dashes
                            String key = o.getName().toLowerCase(Locale.ENGLISH);
                            key = key.replace("-", "");
                            secrets.add(key);
                        }
                    });
                } else if (isLanguage) {
                    LanguageModel lm = JsonMapper.generateLanguageModel(json);
                    lm.getOptions().forEach(o -> {
                        if (o.isSecret()) {
                            // key should be lower and without dashes
                            String key = o.getName().toLowerCase(Locale.ENGLISH);
                            key = key.replace("-", "");
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
                      + " distinct secret options across all the Camel components/dataformats/languages/beans, and "
                      + wholeKeys.size() + " secret camel-main options");

        try {
            boolean updated = updateSensitiveHelperKeys(camelDir, KEYS_START_TOKEN, KEYS_END_TOKEN, secrets,
                    "                    ");
            updated |= updateSensitiveHelperKeys(camelDir, WHOLE_KEYS_START_TOKEN, WHOLE_KEYS_END_TOKEN, wholeKeys,
                    "            ");
            updated |= updateSensitiveHelperPatterns(camelDir, secrets);
            if (updated) {
                getLog().info("Updated camel-util/src/main/java/org/apache/camel/util/SensitiveUtils.java file");
            } else {
                getLog().debug("No changes to camel-util/src/main/java/org/apache/camel/util/SensitiveUtils.java file");
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error updating SensitiveUtils.java", e);
        }

        try {
            updateSensitiveJsonSchema(baseDir, "sensitive-keys.json", secrets);
            updateSensitiveJsonSchema(baseDir, "sensitive-whole-keys.json", wholeKeys);
        } catch (Exception e) {
            throw new MojoExecutionException("Error updating sensitive-keys.json or sensitive-whole-keys.json", e);
        }
    }

    private boolean updateSensitiveHelperKeys(
            File camelDir, String startToken, String endToken, Set<String> secrets, String indent)
            throws Exception {
        // load source code and update
        File java = new File(camelDir, "src/main/java/org/apache/camel/util/SensitiveUtils.java");
        String text = PackageHelper.loadText(java);
        String spaces12 = "            ";

        StringJoiner sb = new StringJoiner(",\n");
        for (String name : secrets) {
            sb.add(indent + "\"" + name + "\"");
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
                text = before + startToken + "\n" + indent + changed + "\n" + spaces12 + endToken + after;
                PackageHelper.writeText(java, text);
                return true;
            }
        }

        return false;
    }

    private boolean updateSensitiveHelperPatterns(File camelDir, Set<String> secrets) throws Exception {
        // load source code and update
        File java = new File(camelDir, "src/main/java/org/apache/camel/util/SensitiveUtils.java");
        String text = PackageHelper.loadText(java);
        String spaces52 = "                                                    ";

        StringJoiner sb = new StringJoiner("\n");
        boolean first = true;
        for (String name : secrets) {
            StringBuilder line = new StringBuilder(name.length() + 32);
            line.append(spaces52);
            line.append("+ \"");
            if (!first) {
                line.append("|");
            }
            line.append("\\\\Q");
            line.append(name);
            line.append("\\\\E\"");
            sb.add(line);
            first = false;
        }
        String changed = sb.toString();

        String existing = Strings.between(text, PATTERN_START_TOKEN, PATTERN_END_TOKEN);
        if (existing != null) {
            // remove leading line breaks etc
            existing = existing.trim();
            changed = changed.trim();
            if (existing.equals(changed)) {
                return false;
            } else {
                String before = Strings.before(text, PATTERN_START_TOKEN);
                String after = Strings.after(text, PATTERN_END_TOKEN);
                text = before + PATTERN_START_TOKEN + "\n" + spaces52 + changed + "\n" + spaces52 + PATTERN_END_TOKEN + after;
                PackageHelper.writeText(java, text);
                return true;
            }
        }

        return false;
    }

    private void updateSensitiveJsonSchema(File camelDir, String fileName, Set<String> secrets) throws Exception {
        File target = new File(camelDir, "src/generated/resources/org/apache/camel/catalog/main/" + fileName);
        JsonArray arr = new JsonArray();
        arr.addAll(secrets);
        String json = JsonMapper.serialize(arr);
        PackageHelper.writeText(target, json);
    }

}
