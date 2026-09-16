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
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;

import static org.apache.camel.tooling.util.PackageHelper.findCamelDirectory;

/**
 * Updates ArtifactUtils.java in camel-util with which artifact ships each component, language, data format and built-in
 * bean, from the catalog, so runtime errors can say what to add to the classpath.
 */
@Mojo(name = "update-artifact-helper", threadSafe = true)
public class UpdateArtifactHelper extends AbstractGeneratorMojo {

    private static final String JAVA_FILE = "src/main/java/org/apache/camel/util/ArtifactUtils.java";

    @Parameter(defaultValue = "${project.basedir}/src/generated/resources/org/apache/camel/catalog/")
    protected File jsonDir;

    @Parameter(defaultValue = "${project.basedir}/")
    protected File baseDir;

    @Inject
    public UpdateArtifactHelper(MavenProjectHelper projectHelper, BuildContext buildContext) {
        super(projectHelper, buildContext);
    }

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

        Map<String, String> components = new TreeMap<>();
        Map<String, String> languages = new TreeMap<>();
        Map<String, String> dataformats = new TreeMap<>();
        Map<String, String> beans = new TreeMap<>();

        for (Path file : jsonFiles) {
            final String name = PackageHelper.asName(file);
            try {
                Object jo = Jsoner.deserialize(PackageHelper.loadText(file.toFile()));
                if (!(jo instanceof JsonObject obj)) {
                    continue;
                }
                JsonObject component = obj.getMap("component");
                JsonObject language = obj.getMap("language");
                JsonObject dataformat = obj.getMap("dataformat");
                JsonObject bean = obj.getMap("bean");
                if (component != null) {
                    // every alternative scheme has its own json file, so the scheme is the key
                    put(components, component.getString("scheme"), component.getString("artifactId"));
                } else if (language != null) {
                    put(languages, language.getString("name"), language.getString("artifactId"));
                } else if (dataformat != null) {
                    put(dataformats, dataformat.getString("name"), dataformat.getString("artifactId"));
                } else if (bean != null) {
                    String javaType = bean.getString("javaType");
                    String interfaceType = bean.getStringOrDefault("interfaceType", "");
                    String artifactId = bean.getString("artifactId");
                    if (javaType != null && artifactId != null) {
                        put(beans, bean.getString("name"), javaType + "|" + interfaceType + "|" + artifactId);
                    }
                }
            } catch (Exception e) {
                throw new MojoExecutionException("Error loading json: " + name, e);
            }
        }

        getLog().info("There are " + components.size() + " component schemes, " + languages.size() + " languages, "
                      + dataformats.size() + " data formats and " + beans.size() + " beans with a known artifact");

        try {
            boolean updated = update(camelDir, "COMPONENTS", components);
            updated |= update(camelDir, "LANGUAGES", languages);
            updated |= update(camelDir, "DATAFORMATS", dataformats);
            updated |= update(camelDir, "BEANS", beans);
            if (updated) {
                getLog().info("Updated camel-util/" + JAVA_FILE + " file");
            } else {
                getLog().debug("No changes to camel-util/" + JAVA_FILE + " file");
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error updating ArtifactUtils.java", e);
        }
    }

    private static void put(Map<String, String> table, String key, String artifactId) {
        if (key != null && artifactId != null) {
            table.put(key, artifactId);
        }
    }

    /**
     * Rewrites the Map.entry lines between the START and END tokens of the given table, in the layout the formatter
     * keeps (so a rebuild does not change the file again).
     */
    private static boolean update(File camelDir, String token, Map<String, String> table) throws Exception {
        File java = new File(camelDir, JAVA_FILE);
        String text = PackageHelper.loadText(java);
        String startToken = "// " + token + ": START";
        String endToken = "// " + token + ": END";
        String spaces12 = "            ";
        String spaces4 = "    ";

        StringJoiner sb = new StringJoiner(",\n");
        for (Map.Entry<String, String> entry : table.entrySet()) {
            String line = spaces12 + "Map.entry(\"" + entry.getKey() + "\", \"" + entry.getValue() + "\")";
            if (line.length() > 120) {
                // the formatter wraps a long entry after the key
                line = spaces12 + "Map.entry(\"" + entry.getKey() + "\",\n" + spaces12 + spaces4 + spaces4
                       + "\"" + entry.getValue() + "\")";
            }
            sb.add(line);
        }
        String changed = sb.toString().trim();

        String existing = Strings.between(text, startToken, endToken);
        if (existing == null) {
            return false;
        }
        if (existing.trim().equals(changed)) {
            return false;
        }
        String before = Strings.before(text, startToken);
        String after = Strings.after(text, endToken);
        text = before + startToken + "\n" + spaces12 + changed + "\n" + spaces4 + endToken + after;
        PackageHelper.writeText(java, text);
        return true;
    }
}
