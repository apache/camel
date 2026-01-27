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
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.tooling.model.JsonMapper;
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
 * Updates the HeaderImportantHelper.java with the known important message headers
 */
@Mojo(name = "update-important-header-helper", threadSafe = true)
public class UpdateHeaderImportantHelper extends AbstractGeneratorMojo {

    private static final String KEYS_START_TOKEN = "// IMPORTANT-HEADER-KEYS: START";
    private static final String KEYS_END_TOKEN = "// IMPORTANT-HEADER-KEYS: END";

    @Parameter(defaultValue = "${project.basedir}/src/generated/resources/org/apache/camel/catalog/")
    protected File jsonDir;

    @Parameter(defaultValue = "${project.basedir}/")
    protected File baseDir;

    @Inject
    public UpdateHeaderImportantHelper(MavenProjectHelper projectHelper, BuildContext buildContext) {
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
        Set<String> importants = new TreeSet<>();

        for (Path file : jsonFiles) {
            final String name = PackageHelper.asName(file);

            try {
                String json = PackageHelper.loadText(file.toFile());
                Object jo = Jsoner.deserialize(json);
                if (jo instanceof JsonObject obj) {
                } else {
                    continue;
                }

                boolean isComponent = obj.getMap("component") != null;
                boolean isEip = !isComponent && obj.getMap("model") != null;

                // only check these kind
                if (!isComponent && !isEip) {
                    continue;
                }

                if (isComponent) {
                    ComponentModel cm = JsonMapper.generateComponentModel(json);
                    cm.getEndpointHeaders().forEach(o -> {
                        if (o.isImportant()) {
                            importants.add(o.getName());
                        }
                    });
                } else if (isEip) {
                    EipModel em = JsonMapper.generateEipModel(json);
                    em.getExchangeProperties().forEach(o -> {
                        if (o.isImportant()) {
                            importants.add(o.getName());
                        }
                    });
                }
            } catch (Exception e) {
                throw new MojoExecutionException("Error loading json: " + name, e);
            }
        }

        getLog().info("There are " + importants.size()
                      + " distinct important options across all the Camel components/eips");

        try {
            boolean updated = updateImportantHeaderKeys(camelDir, importants);
            if (updated) {
                getLog().info("Updated camel-util/src/main/java/org/apache/camel/util/ImportantHeaderUtils.java file");
            } else {
                getLog().debug("No changes to camel-util/src/main/java/org/apache/camel/util/ImportantHeaderUtils.java file");
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error updating ImportantHeaderUtils.java", e);
        }

        try {
            updateImportantHeaderJsonSchema(baseDir, importants);
        } catch (Exception e) {
            throw new MojoExecutionException("Error updating important-headers.json", e);
        }
    }

    private boolean updateImportantHeaderKeys(File camelDir, Set<String> importants) throws Exception {
        // load source code and update
        File java = new File(camelDir, "src/main/java/org/apache/camel/util/ImportantHeaderUtils.java");
        String text = PackageHelper.loadText(java);
        String spaces20 = "                    ";
        String spaces12 = "            ";

        StringJoiner sb = new StringJoiner(",\n");
        for (String name : importants) {
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

    private void updateImportantHeaderJsonSchema(File camelDir, Set<String> secrets) throws Exception {
        File target = new File(camelDir, "src/generated/resources/org/apache/camel/catalog/main/important-headers.json");
        JsonArray arr = new JsonArray();
        arr.addAll(secrets);
        String json = JsonMapper.serialize(arr);
        PackageHelper.writeText(target, json);
    }

}
