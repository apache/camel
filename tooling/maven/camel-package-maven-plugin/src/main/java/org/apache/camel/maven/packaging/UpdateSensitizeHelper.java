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
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.model.LanguageModel;
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
    private static final String PATTERN_START_TOKEN = "// SENSITIVE-PATTERN: START";
    private static final String PATTERN_END_TOKEN = "// SENSITIVE-PATTERN: END";
    private static final String SECURITY_START_TOKEN = "// SECURITY-OPTIONS: START";
    private static final String SECURITY_END_TOKEN = "// SECURITY-OPTIONS: END";

    private static final String SECRET = "secret";
    private static final String INSECURE_DEV = "insecure:dev";
    private static final String INSECURE_SSL = "insecure:ssl";
    private static final String INSECURE_SERIALIZATION = "insecure:serialization";

    // mapping from category string to the constant name used in SecurityUtils.java
    private static final Map<String, String> CATEGORY_CONSTANTS = Map.of(
            INSECURE_SSL, "INSECURE_SSL",
            INSECURE_SERIALIZATION, "INSECURE_SERIALIZATION",
            INSECURE_DEV, "INSECURE_DEV");

    // mapping from insecure value to the constant name used in SecurityUtils.java
    private static final Map<String, String> VALUE_CONSTANTS = Map.of(
            "false", "VALUE_FALSE");

    // extra keys that are regarded as secret which may not yet been in any component
    // they MUST be in lowercase and without a dash
    private static final String[] EXTRA_KEYS
            = new String[] { "apipassword", "apiuser", "apiusername", "api_key", "api_secret", SECRET, "keystorePassword" };

    // extra security options from camel-main properties that are not in component JSON files
    // each entry: option name (lowercase, no dashes), security category, insecure value
    private static final String[][] EXTRA_SECURITY_OPTIONS = {
            { "devconsoleenabled", INSECURE_DEV, "true" },
            { "uploadenabled", INSECURE_DEV, "true" },
            { "downloadenabled", INSECURE_DEV, "true" },
            { "sendenabled", INSECURE_DEV, "true" },
    };

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
        // key -> [category, insecureValue]
        Map<String, String[]> securityOptions = new TreeMap<>();

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

                // only check these kind
                if (!isComponent && !isDataFormat && !isLanguage) {
                    continue;
                }

                if (isComponent) {
                    ComponentModel cm = JsonMapper.generateComponentModel(json);
                    cm.getComponentOptions().forEach(o -> {
                        collectSecretOption(o, secrets);
                        collectSecurityOption(o, securityOptions);
                    });
                    cm.getEndpointOptions().forEach(o -> collectSecurityOption(o, securityOptions));
                } else if (isDataFormat) {
                    DataFormatModel dm = JsonMapper.generateDataFormatModel(json);
                    dm.getOptions().forEach(o -> {
                        collectSecretOption(o, secrets);
                        collectSecurityOption(o, securityOptions);
                    });
                } else if (isLanguage) {
                    LanguageModel lm = JsonMapper.generateLanguageModel(json);
                    lm.getOptions().forEach(o -> {
                        collectSecretOption(o, secrets);
                        collectSecurityOption(o, securityOptions);
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
            boolean updated = updateSensitiveHelperKeys(camelDir, secrets);
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
            updateSensitiveJsonSchema(baseDir, secrets);
        } catch (Exception e) {
            throw new MojoExecutionException("Error updating sensitive-keys.json", e);
        }

        // add extra security options from camel-main properties
        for (String[] extra : EXTRA_SECURITY_OPTIONS) {
            securityOptions.putIfAbsent(extra[0], new String[] { extra[1], extra[2] });
        }

        // update SecurityUtils with insecure options
        if (!securityOptions.isEmpty()) {
            getLog().info("There are " + securityOptions.size()
                          + " distinct insecure security options across all the Camel components/dataformats/languages");
            try {
                boolean updated = updateSecurityUtils(camelDir, securityOptions);
                if (updated) {
                    getLog().info("Updated camel-util/src/main/java/org/apache/camel/util/SecurityUtils.java file");
                } else {
                    getLog().debug("No changes to camel-util/src/main/java/org/apache/camel/util/SecurityUtils.java file");
                }
            } catch (Exception e) {
                throw new MojoExecutionException("Error updating SecurityUtils.java", e);
            }
        }
    }

    private boolean updateSensitiveHelperKeys(File camelDir, Set<String> secrets) throws Exception {
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

        String existing = Strings.between(text, KEYS_START_TOKEN, KEYS_END_TOKEN);
        if (existing != null) {
            // remove leading line breaks etc
            existing = existing.trim();
            changed = changed.trim();
            if (existing.equals(changed)) {
                return false;
            } else {
                String before = Strings.before(text, KEYS_START_TOKEN);
                String after = Strings.after(text, KEYS_END_TOKEN);
                text = before + KEYS_START_TOKEN + "\n" + spaces20 + changed + "\n" + spaces12 + KEYS_END_TOKEN + after;
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

    private void updateSensitiveJsonSchema(File camelDir, Set<String> secrets) throws Exception {
        File target = new File(camelDir, "src/generated/resources/org/apache/camel/catalog/main/sensitive-keys.json");
        JsonArray arr = new JsonArray();
        arr.addAll(secrets);
        String json = JsonMapper.serialize(arr);
        PackageHelper.writeText(target, json);
    }

    private static void collectSecretOption(
            org.apache.camel.tooling.model.BaseOptionModel o, Set<String> secrets) {
        if (o.isSecret() || SECRET.equals(o.getSecurity())) {
            String key = o.getName().toLowerCase(Locale.ENGLISH);
            key = key.replace("-", "");
            secrets.add(key);
        }
    }

    private static void collectSecurityOption(
            org.apache.camel.tooling.model.BaseOptionModel o, Map<String, String[]> securityOptions) {
        String security = o.getSecurity();
        if (!Strings.isNullOrEmpty(security) && !SECRET.equals(security)) {
            // only collect insecure:* categories; secrets are handled by SensitiveUtils
            String key = o.getName().toLowerCase(Locale.ENGLISH);
            key = key.replace("-", "");
            String insecureValue = o.getInsecureValue();
            if (Strings.isNullOrEmpty(insecureValue)) {
                // default: boolean options are insecure when true
                insecureValue = "boolean".equals(o.getType()) ? "true" : "";
            }
            // only add if not already present (first wins)
            securityOptions.putIfAbsent(key, new String[] { security, insecureValue });
        }
    }

    private boolean updateSecurityUtils(File camelDir, Map<String, String[]> securityOptions) throws Exception {
        File java = new File(camelDir, "src/main/java/org/apache/camel/util/SecurityUtils.java");
        String text = PackageHelper.loadText(java);
        String spaces8 = "        ";

        StringJoiner sb = new StringJoiner("\n");
        for (Map.Entry<String, String[]> entry : securityOptions.entrySet()) {
            String key = entry.getKey();
            String category = entry.getValue()[0];
            String insecureValue = entry.getValue()[1];
            String categoryRef = CATEGORY_CONSTANTS.get(category);
            String categoryExpr = categoryRef != null ? categoryRef : "\"" + category + "\"";
            String valueRef = VALUE_CONSTANTS.get(insecureValue);
            String valueExpr = valueRef != null ? valueRef : "\"" + insecureValue + "\"";
            sb.add(spaces8 + "map.put(\"" + key + "\", new SecurityOption(" + categoryExpr + ", " + valueExpr + "));");
        }
        String changed = sb.toString();

        String existing = Strings.between(text, SECURITY_START_TOKEN, SECURITY_END_TOKEN);
        if (existing != null) {
            existing = existing.trim();
            changed = changed.trim();
            if (existing.equals(changed)) {
                return false;
            } else {
                String before = Strings.before(text, SECURITY_START_TOKEN);
                String after = Strings.after(text, SECURITY_END_TOKEN);
                text = before + SECURITY_START_TOKEN + "\n" + spaces8 + changed + "\n" + spaces8 + SECURITY_END_TOKEN + after;
                PackageHelper.writeText(java, text);
                return true;
            }
        }

        return false;
    }

}
