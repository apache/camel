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
package org.apache.camel.maven.connector;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.jar.AbstractJarMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "jar", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, requiresProject = true, threadSafe = true,
        requiresDependencyResolution = ResolutionScope.RUNTIME)
public class ConnectorMojo extends AbstractJarMojo {

    /**
     * Directory containing the classes and resource files that should be packaged into the JAR.
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true)
    private File classesDirectory;

    /**
     * Whether to include the git url for the git repository of the source code for the Camel connector
     */
    @Parameter(defaultValue = "false")
    private boolean includeGitUrl;

    @Override
    protected File getClassesDirectory() {
        return classesDirectory;
    }

    @Override
    protected String getClassifier() {
        // no classifier
        return null;
    }

    @Override
    protected String getType() {
        return "jar";
    }

    @Override
    public File createArchive() throws MojoExecutionException {

        String gitUrl = null;

        // find the component dependency and get its .json file
        File file = new File(classesDirectory, "camel-connector.json");
        if (file.exists()) {

            if (includeGitUrl) {
                // we want to include the git url of the project
                File gitFolder = GitHelper.findGitFolder();
                try {
                    gitUrl = GitHelper.extractGitUrl(gitFolder);
                } catch (IOException e) {
                    throw new MojoExecutionException("Cannot extract gitUrl due " + e.getMessage(), e);
                }
                if (gitUrl == null) {
                    getLog().warn("No .git directory found for connector");
                }
            }

            try {
                ObjectMapper mapper = new ObjectMapper();
                Map dto = mapper.readValue(file, Map.class);

                // embed girUrl in camel-connector.json file
                if (gitUrl != null) {
                    String existingGitUrl = (String) dto.get("gitUrl");
                    if (existingGitUrl == null || !existingGitUrl.equals(gitUrl)) {
                        dto.put("gitUrl", gitUrl);
                        // update file
                        mapper.writerWithDefaultPrettyPrinter().writeValue(file, dto);
                        // update source file also
                        File root = classesDirectory.getParentFile().getParentFile();
                        file = new File(root, "src/main/resources/camel-connector.json");
                        if (file.exists()) {
                            getLog().info("Updating gitUrl to " + file);
                            mapper.writerWithDefaultPrettyPrinter().writeValue(file, dto);
                        }
                    }
                }

                File schema = embedCamelComponentSchema(file);
                if (schema != null) {
                    String json = FileHelper.loadText(new FileInputStream(schema));

                    List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("component", json, false);
                    String header = buildComponentHeaderSchema(rows, dto, gitUrl);
                    getLog().debug(header);

                    rows = JSonSchemaHelper.parseJsonSchema("componentProperties", json, true);
                    // we do not offer editing component properties (yet) so clear the rows
                    rows.clear();
                    String componentOptions = buildComponentOptionsSchema(rows, dto);
                    getLog().debug(componentOptions);

                    rows = JSonSchemaHelper.parseJsonSchema("properties", json, true);
                    String endpointOptions = buildEndpointOptionsSchema(rows, dto);
                    getLog().debug(endpointOptions);

                    // generate the json file
                    StringBuilder jsonSchema = new StringBuilder();
                    jsonSchema.append("{\n");
                    jsonSchema.append(header);
                    jsonSchema.append(componentOptions);
                    jsonSchema.append(endpointOptions);
                    jsonSchema.append("}\n");

                    String newJson = jsonSchema.toString();

                    // parse ourselves
                    rows = JSonSchemaHelper.parseJsonSchema("component", newJson, false);
                    String newScheme = getOption(rows, "scheme");

                    // write the json file to the target directory as if camel apt would do it
                    String javaType = (String) dto.get("javaType");
                    String dir = javaType.substring(0, javaType.lastIndexOf("."));
                    dir = dir.replace('.', '/');
                    File subDir = new File(classesDirectory, dir);
                    String name = newScheme + ".json";
                    File out = new File(subDir, name);

                    FileOutputStream fos = new FileOutputStream(out, false);
                    fos.write(newJson.getBytes());
                    fos.close();

                    // also write the file in the root folder so its easier to find that for tooling
                    out = new File(classesDirectory, "camel-connector-schema.json");
                    fos = new FileOutputStream(out, false);
                    fos.write(newJson.getBytes());
                    fos.close();
                }

                // build json schema for component that only has the selectable options
            } catch (Exception e) {
                throw new MojoExecutionException("Error in camel-connector-maven-plugin", e);
            }
        }

        return super.createArchive();
    }

    private String extractJavaType(String scheme) throws Exception {
        File file = new File(classesDirectory, "META-INF/services/org/apache/camel/component/" + scheme);
        if (file.exists()) {
            List<String> lines = FileHelper.loadFile(file);
            String fqn = extractClass(lines);
            return fqn;
        }
        return null;
    }

    private String getOption(List<Map<String, String>> rows, String key) {
        for (Map<String, String> row : rows) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        return null;
    }

    private String buildComponentOptionsSchema(List<Map<String, String>> rows, Map dto) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();

        StringBuilder sb = new StringBuilder();
        sb.append("  \"componentProperties\": {\n");

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            String key = row.get("name");
            row.remove("name");
            String line = mapper.writeValueAsString(row);

            sb.append("    \"" + key + "\": ");
            sb.append(line);
            if (i < rows.size() - 1) {
                sb.append(",\n");
            } else {
                sb.append("\n");
            }
        }

        sb.append("  },\n");
        return sb.toString();
    }

    private String buildEndpointOptionsSchema(List<Map<String, String>> rows, Map dto) throws JsonProcessingException {
        // find the endpoint options
        List options = (List) dto.get("endpointOptions");
        Map values = (Map) dto.get("endpointValues");
        Map overrides = (Map) dto.get("endpointOverrides");

        ObjectMapper mapper = new ObjectMapper();

        StringBuilder sb = new StringBuilder();
        sb.append("  \"properties\": {\n");

        boolean first = true;
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            String key = row.get("name");
            row.remove("name");

            if (options == null || !options.contains(key)) {
                continue;
            }

            // do we have a new default value for this row?
            if (values != null && values.containsKey(key)) {
                String newDefaultValue = (String) values.get(key);
                if (newDefaultValue != null) {
                    row.put("defaultValue", newDefaultValue);
                }
            }

            // is there any overrides for this row?
            if (overrides != null && overrides.containsKey(key)) {
                Map over = (Map) overrides.get(key);
                if (over != null) {
                    row.putAll(over);
                }
            }

            // we should build the json as one-line which is how Camel does it today
            // which makes its internal json parser support loading our generated schema file
            String line = mapper.writeValueAsString(row);

            if (!first) {
                sb.append(",\n");
            }
            sb.append("    \"" + key + "\": ");
            sb.append(line);

            first = false;
        }
        if (!first) {
            sb.append("\n");
        }

        sb.append("  }\n");
        return sb.toString();
    }

    private String buildComponentHeaderSchema(List<Map<String, String>> rows, Map dto, String gitUrl) throws Exception {
        String baseScheme = (String) dto.get("baseScheme");
        String title = (String) dto.get("name");
        String scheme = StringHelper.camelCaseToDash(title);
        String baseSyntax = getOption(rows, "syntax");
        String syntax = baseSyntax.replaceFirst(baseScheme, scheme);

        String description = (String) dto.get("description");
        // dto has labels
        String label = null;
        List<String> labels = (List<String>) dto.get("labels");
        if (labels != null) {
            label = labels.stream().collect(Collectors.joining(","));
        }
        String async = getOption(rows, "async");
        String pattern = (String) dto.get("pattern");
        String producerOnly = "To".equalsIgnoreCase(pattern) ? "true" : null;
        String consumerOnly = "From".equalsIgnoreCase(pattern) ? "true" : null;
        String lenientProperties = getOption(rows, "lenientProperties");
        String javaType = extractJavaType(scheme);
        String groupId = getProject().getGroupId();
        String artifactId = getProject().getArtifactId();
        String version = getProject().getVersion();

        StringBuilder sb = new StringBuilder();
        sb.append("  \"component\": {\n");
        if (gitUrl != null) {
            sb.append("    \"girUrl\": \"" + StringHelper.nullSafe(gitUrl) + "\",\n");
        }
        sb.append("    \"kind\": \"component\",\n");
        sb.append("    \"baseScheme\": \"" + StringHelper.nullSafe(baseScheme) + "\",\n");
        sb.append("    \"scheme\": \"" + scheme + "\",\n");
        sb.append("    \"syntax\": \"" + syntax + "\",\n");
        sb.append("    \"title\": \"" + title + "\",\n");
        if (description != null) {
            sb.append("    \"description\": \"" + description + "\",\n");
        }
        if (label != null) {
            sb.append("    \"label\": \"" + label + "\",\n");
        }
        sb.append("    \"deprecated\": \"false\",\n");
        if (async != null) {
            sb.append("    \"async\": \"" + async + "\",\n");
        }
        if (producerOnly != null) {
            sb.append("    \"producerOnly\": \"" + producerOnly + "\",\n");
        } else if (consumerOnly != null) {
            sb.append("    \"consumerOnly\": \"" + consumerOnly + "\",\n");
        }
        if (lenientProperties != null) {
            sb.append("    \"lenientProperties\": \"" + lenientProperties + "\",\n");
        }
        sb.append("    \"javaType\": \"" + javaType + "\",\n");
        sb.append("    \"groupId\": \"" + groupId + "\",\n");
        sb.append("    \"artifactId\": \"" + artifactId + "\",\n");
        sb.append("    \"version\": \"" + version + "\"\n");
        sb.append("  },\n");

        return sb.toString();
    }

    /**
     * Finds and embeds the Camel component JSon schema file
     */
    private File embedCamelComponentSchema(File file) throws MojoExecutionException {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map dto = mapper.readValue(file, Map.class);

            String scheme = extractScheme(dto);
            String groupId = extractGroupId(dto);
            String artifactId = extractArtifactId(dto);
            String version = extractVersion(dto);

            // find the artifact on the classpath that has the Camel component this connector is using
            // then we want to grab its json schema file to embed in this JAR so we have all files together

            if (scheme != null && groupId != null && artifactId != null) {
                for (Object obj : getProject().getDependencyArtifacts()) {
                    Artifact artifact = (Artifact) obj;
                    if ("jar".equals(artifact.getType())) {
                        // use baseVersion so we can support SNAPSHOT versions that are based on a base version
                        if (groupId.equals(artifact.getGroupId()) && artifactId.equals(artifact.getArtifactId()) && version.equals(artifact.getBaseVersion())) {
                            // load the component file inside the file
                            URL url = new URL("file:" + artifact.getFile());
                            URLClassLoader child = new URLClassLoader(new URL[]{url}, this.getClass().getClassLoader());

                            InputStream is = child.getResourceAsStream("META-INF/services/org/apache/camel/component/" + scheme);
                            if (is != null) {
                                List<String> lines = FileHelper.loadFile(is);
                                String fqn = extractClass(lines);
                                is.close();

                                // only keep package
                                String pck = fqn.substring(0, fqn.lastIndexOf("."));
                                String name = pck.replace(".", "/") + "/" + scheme + ".json";

                                is = child.getResourceAsStream(name);
                                if (is != null) {
                                    List<String> schema = FileHelper.loadFile(is);
                                    is.close();

                                    // write schema to file
                                    File out = new File(classesDirectory, "camel-component-schema.json");
                                    FileOutputStream fos = new FileOutputStream(out, false);
                                    for (String line : schema) {
                                        fos.write(line.getBytes());
                                        fos.write("\n".getBytes());
                                    }
                                    fos.close();

                                    getLog().info("Embedded camel-component-schema.json file for Camel component " + scheme);

                                    return out;
                                }
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            throw new MojoExecutionException("Cannot read file camel-connector.json", e);
        }

        return null;
    }

    private String extractClass(List<String> lines) {
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("class=")) {
                return line.substring(6);
            }
        }
        return null;
    }

    private String extractScheme(Map map) {
        return (String) map.get("baseScheme");
    }

    private String extractGroupId(Map map) {
        return (String) map.get("baseGroupId");
    }

    private String extractArtifactId(Map map) {
        return (String) map.get("baseArtifactId");
    }

    private String extractVersion(Map map) {
        return (String) map.get("baseVersion");
    }

}
