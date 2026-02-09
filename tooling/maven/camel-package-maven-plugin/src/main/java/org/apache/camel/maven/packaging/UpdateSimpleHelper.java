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
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;

import javax.inject.Inject;

import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.model.LanguageModel;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;

import static org.apache.camel.tooling.util.PackageHelper.findCamelDirectory;

/**
 * Updates the SimpleHelper.java with the built-in simple functions
 */
@Mojo(name = "update-simple-helper", threadSafe = true)
public class UpdateSimpleHelper extends AbstractGeneratorMojo {

    private static final String FUNCTIONS_START_TOKEN = "// SIMPLE-FUNCTIONS: START";
    private static final String FUNCTIONS_END_TOKEN = "// SIMPLE-FUNCTIONS: END";

    @Parameter(defaultValue = "${project.basedir}/src/generated/resources/org/apache/camel/catalog/languages/simple.json")
    protected File simpleFile;

    @Parameter(defaultValue = "${project.basedir}/")
    protected File baseDir;

    @Inject
    public UpdateSimpleHelper(MavenProjectHelper projectHelper, BuildContext buildContext) {
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
        Set<String> functions = new TreeSet<>();

        try {
            String json = PackageHelper.loadText(simpleFile);
            LanguageModel model = JsonMapper.generateLanguageModel(json);
            model.getFunctions().forEach(f -> {
                String name = f.getName();
                if (name.contains("(")) {
                    name = name.substring(0, name.indexOf("("));
                }
                if (name.contains(":")) {
                    name = name.substring(0, name.indexOf(":"));
                }
                if (name.contains(".")) {
                    name = name.substring(0, name.indexOf("."));
                }
                name = name.toLowerCase(Locale.ENGLISH);
                functions.add(name);
            });
        } catch (Exception e) {
            throw new MojoExecutionException("Error loading json: " + simpleFile, e);
        }

        getLog().info("There are " + functions.size() + " simple language functions");
        try {
            boolean updated = updateSimpleHelperFunctions(camelDir, functions);
            if (updated) {
                getLog().info(
                        "Updated camel-util/src/main/java/org/apache/camel/util/SimpleUtils.java file");
            } else {
                getLog().debug(
                        "No changes to camel-util/src/main/java/org/apache/camel/util/SimpleUtils.java file");
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error updating SimpleUtils.java", e);
        }
    }

    private boolean updateSimpleHelperFunctions(File camelDir, Set<String> secrets) throws Exception {
        // load source code and update
        File java = new File(camelDir, "src/main/java/org/apache/camel/util/SimpleUtils.java");
        String text = PackageHelper.loadText(java);
        String spaces20 = "                    ";
        String spaces12 = "            ";

        StringJoiner sb = new StringJoiner(",\n");
        for (String name : secrets) {
            sb.add(spaces20 + "\"" + name + "\"");
        }
        String changed = sb.toString();

        String existing = Strings.between(text, FUNCTIONS_START_TOKEN, FUNCTIONS_END_TOKEN);
        if (existing != null) {
            // remove leading line breaks etc
            existing = existing.trim();
            changed = changed.trim();
            if (existing.equals(changed)) {
                return false;
            } else {
                String before = Strings.before(text, FUNCTIONS_START_TOKEN);
                String after = Strings.after(text, FUNCTIONS_END_TOKEN);
                text = before + FUNCTIONS_START_TOKEN + "\n" + spaces20 + changed + "\n" + spaces12 + FUNCTIONS_END_TOKEN
                       + after;
                PackageHelper.writeText(java, text);
                return true;
            }
        }

        return false;
    }

}
