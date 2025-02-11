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
import java.nio.file.Files;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;

import static org.apache.camel.tooling.util.PackageHelper.findCamelDirectory;

/**
 * Updates the MimeTypeHelper.java with the known types
 */
@Mojo(name = "update-mime-type-helper", threadSafe = true)
public class UpdateMimeTypeHelper extends AbstractGeneratorMojo {

    private static final String TYPES_START_TOKEN = "// MIME-TYPES: START";
    private static final String TYPES_END_TOKEN = "// MIME-TYPES: END";

    @Parameter(defaultValue = "${project.basedir}/src/main/resources/mime-types.txt")
    protected File mimeFile;

    @Parameter(defaultValue = "${project.basedir}/")
    protected File baseDir;

    @Inject
    public UpdateMimeTypeHelper(MavenProjectHelper projectHelper, BuildContext buildContext) {
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
        Map<String, String> types = new TreeMap<>();

        // extra for yaml
        types.put("yaml", "text/yaml");
        types.put("yml", "text/yaml");

        try (Stream<String> s = Files.lines(mimeFile.toPath())) {
            for (String line : s.toList()) {
                line = line.trim();
                if (line.startsWith("#") || line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\\s");
                if (parts.length > 1) {
                    String mime = parts[0].trim();
                    for (int i = 1; i < parts.length; i++) {
                        String ext = parts[i].trim();
                        if (!ext.isBlank()) {
                            types.put(ext, mime);
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error parsing mime-type: " + mimeFile, e);
        }

        getLog().info("There are " + types.size() + " mime-types");

        try {
            boolean updated = updateMimeTypeHelper(camelDir, types);
            if (updated) {
                getLog().info("Updated camel-util/src/main/java/org/apache/camel/util/MimeTypeHelper.java file");
            } else {
                getLog().debug("No changes to camel-util/src/main/java/org/apache/camel/util/MimeTypeHelper.java file");
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error updating MimeHelper.java", e);
        }
    }

    private boolean updateMimeTypeHelper(File camelDir, Map<String, String> types) throws Exception {
        // load source code and update
        File java = new File(camelDir, "src/main/java/org/apache/camel/util/MimeTypeHelper.java");
        String text = PackageHelper.loadText(java);
        String spaces4 = "    ";
        String spaces12 = "            ";

        StringJoiner sb = new StringJoiner("\n");
        for (var e : types.entrySet()) {
            sb.add(spaces12 + "case \"" + e.getKey() + "\" -> \"" + e.getValue() + "\";");
        }
        String changed = sb.toString();

        String existing = Strings.between(text, TYPES_START_TOKEN, TYPES_END_TOKEN);
        if (existing != null) {
            // remove leading line breaks etc
            existing = existing.trim();
            changed = changed.trim();
            if (existing.equals(changed)) {
                return false;
            } else {
                String before = Strings.before(text, TYPES_START_TOKEN);
                String after = Strings.after(text, TYPES_END_TOKEN);
                text = before + TYPES_START_TOKEN + "\n" + spaces12 + changed + "\n" + spaces12 + TYPES_END_TOKEN + after;
                PackageHelper.writeText(java, text);
                return true;
            }
        }

        return false;
    }

}
