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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.maven.connector.util.FileHelper;
import org.apache.camel.maven.connector.util.GitHelper;
import org.apache.camel.maven.connector.util.JSonSchemaHelper;
import org.apache.camel.maven.connector.util.StringHelper;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.jar.AbstractJarMojo;

import static org.apache.camel.maven.connector.util.JSonSchemaHelper.prettyPrint;

@Mojo(name = "jar", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, requiresProject = true, threadSafe = true,
        requiresDependencyResolution = ResolutionScope.RUNTIME)
public class ConnectorMojo extends AbstractJarMojo {

    /**
     * Directory containing the classes and resource files that should be packaged into the JAR.
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true)
    private File classesDirectory;

    /**
     * Whether to generate JSon schema files to the sources directory (<tt>src/main/resources</tt>) which allows the files to be stored together with the source code.
     * If this options is <tt>false</tt> the JSon schema files are generated into <tt>target/classes</tt> and only included in the built JAR.
     */
    @Parameter(defaultValue = "true")
    private boolean generateToSources;

    /**
     * Whether to include the git url for the git repository of the source code for the Camel connector
     */
    @Parameter(defaultValue = "false")
    private boolean includeGitUrl;

    /**
     * Whether to output JSon connector schema files in pretty print mode or not
     */
    @Parameter(defaultValue = "true")
    private boolean prettyPrint;

    private CamelCatalog catalog = new DefaultCamelCatalog();

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

        // project root folder
        File root = classesDirectory.getParentFile().getParentFile();

        String gitUrl = null;

        // find the component dependency and get its .json file
        File file = new File(classesDirectory, "camel-connector.json");
        if (file.exists()) {

            // updating to use correct project version in camel-connector.json
            String version = getProject().getVersion();
            updateVersionInCamelConnectorJSon("version", version);

            try {
                ObjectMapper mapper = new ObjectMapper();
                Map dto = mapper.readValue(file, Map.class);

                if (includeGitUrl) {
                    gitUrl = embedGitUrlInCamelConnectorJSon(mapper, dto);
                }

                File schema = embedCamelComponentSchema(file);
                if (schema != null) {
                    String json = FileHelper.loadText(new FileInputStream(schema));

                    List<Map<String, String>> rows = org.apache.camel.catalog.JSonSchemaHelper.parseJsonSchema("component", json, false);
                    String header = buildComponentHeaderSchema(rows, dto, gitUrl);
                    getLog().debug(header);

                    rows = org.apache.camel.catalog.JSonSchemaHelper.parseJsonSchema("componentProperties", json, true);
                    String componentOptions = buildComponentOptionsSchema(rows, dto);
                    getLog().debug(componentOptions);

                    rows = org.apache.camel.catalog.JSonSchemaHelper.parseJsonSchema("properties", json, true);
                    String endpointOptions = buildEndpointOptionsSchema(rows, dto);
                    getLog().debug(endpointOptions);

                    String connectorOptions = buildConnectorOptionsSchema(dto);
                    getLog().debug(connectorOptions);

                    // generate the json file
                    StringBuilder jsonSchema = new StringBuilder();
                    jsonSchema.append("{\n");
                    jsonSchema.append(header);
                    jsonSchema.append(componentOptions);
                    jsonSchema.append(endpointOptions);
                    jsonSchema.append(connectorOptions);
                    jsonSchema.append("}\n");

                    String newJson = jsonSchema.toString();

                    // parse ourselves
                    rows = org.apache.camel.catalog.JSonSchemaHelper.parseJsonSchema("component", newJson, false);
                    String newScheme = getOption(rows, "scheme");

                    checkConnectorScheme(newScheme);

                    // write the json file to the target directory as if camel apt would do it
                    String javaType = (String) dto.get("javaType");
                    String dir = javaType.substring(0, javaType.lastIndexOf("."));
                    dir = dir.replace('.', '/');
                    File subDir = new File(classesDirectory, dir);
                    String name = newScheme + ".json";
                    File out = new File(subDir, name);

                    FileOutputStream fos = new FileOutputStream(out, false);
                    // output as pretty print
                    newJson = prettyPrint ? prettyPrint(newJson) : newJson;
                    fos.write(newJson.getBytes());
                    fos.close();

                    // also write the file in the root folder so its easier to find that for tooling
                    out = new File(classesDirectory, "camel-connector-schema.json");
                    fos = new FileOutputStream(out, false);
                    fos.write(newJson.getBytes());
                    fos.close();

                    if (generateToSources) {
                        // copy the file into the sources as well
                        File from = new File(classesDirectory, "camel-connector-schema.json");
                        File to = new File(root, "src/main/resources/camel-connector-schema.json");
                        FileHelper.copyFile(from, to);
                    }
                }

                // build json schema for component that only has the selectable options
            } catch (Exception e) {
                throw new MojoExecutionException("Error in camel-connector-maven-plugin", e);
            }
        }

        return super.createArchive();
    }

    private void checkConnectorScheme(String connectorScheme) {
        List<String> componentNames = catalog.findComponentNames();
        if (componentNames != null && componentNames.contains(connectorScheme)) {
            String format = "Can't package a connector with scheme '%s' as a component with the same scheme is already registered in the catalog";
            String message = String.format(format, connectorScheme);
            getLog().error(message);
            throw new IllegalArgumentException(message);
        }
    }

    private String embedGitUrlInCamelConnectorJSon(ObjectMapper mapper, Map dto) throws MojoExecutionException {
        // we want to include the git url of the project
        File gitFolder = GitHelper.findGitFolder();
        try {
            String gitUrl = GitHelper.extractGitUrl(gitFolder);
            if (gitUrl == null) {
                return null;
            }

            // embed girUrl in camel-connector.json file
            String existingGitUrl = (String) dto.get("gitUrl");
            if (existingGitUrl == null || !existingGitUrl.equals(gitUrl)) {
                dto.put("gitUrl", gitUrl);
                // update file
                File file = new File(classesDirectory, "camel-connector.json");
                mapper.writerWithDefaultPrettyPrinter().writeValue(file, dto);
                // update source file also
                File root = classesDirectory.getParentFile().getParentFile();
                File sourceFile = new File(root, "src/main/resources/camel-connector.json");
                if (sourceFile.exists()) {
                    getLog().info("Updating gitUrl to " + gitUrl + " in " + sourceFile);
                    mapper.writerWithDefaultPrettyPrinter().writeValue(sourceFile, dto);
                }
            }
            return gitUrl;
            
        } catch (IOException e) {
            throw new MojoExecutionException("Error in camel-connector-maven-plugin", e);
        }
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
        // find the endpoint options
        List options = (List) dto.get("componentOptions");
        Map values = (Map) dto.get("componentValues");
        Map overrides = (Map) dto.get("componentOverrides");

        StringBuilder sb = new StringBuilder();
        sb.append("  \"componentProperties\": {\n");

        boolean first = true;

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            String key = row.get("name");

            if (options == null || !options.contains(key)) {
                continue;
            }

            // do we have a new default value for this row?
            if (values != null && values.containsKey(key)) {
                // the value may be an integer so we need to use Object and toString when putting back in row
                Object newDefaultValue = values.get(key);
                if (newDefaultValue != null) {
                    row.put("defaultValue", newDefaultValue.toString());
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
            String line = buildJSonLineFromRow(row);

            if (!first) {
                sb.append(",\n");
            }
            sb.append("    ").append(line);

            first = false;
        }
        if (!first) {
            sb.append("\n");
        }

        sb.append("  },\n");
        return sb.toString();
    }

    private String buildEndpointOptionsSchema(List<Map<String, String>> rows, Map dto) throws JsonProcessingException {
        // find the endpoint options
        List options = (List) dto.get("endpointOptions");
        Map values = (Map) dto.get("endpointValues");
        Map overrides = (Map) dto.get("endpointOverrides");

        // if the dto is scheduled then we need to add timer options
        if ("timer".equals(dto.get("scheduler"))) {
            // include the period option from the timer as we use that
            Map<String, String> period = new LinkedHashMap<>();
            period.put("name", "schedulerPeriod");
            period.put("kind", "parameter");
            period.put("displayName", "Period");
            period.put("group", "consumer");
            period.put("type", "integer");
            period.put("javaType", "long");
            period.put("deprecated", "false");
            period.put("secret", "false");
            period.put("defaultValue", "1000");
            period.put("description", "Delay in milli seconds between scheduling (executing)");

            getLog().debug("Connector is using scheduler: timer");
            rows.add(period);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("  \"properties\": {\n");

        boolean first = true;
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            String key = row.get("name");

            if (options == null || !options.contains(key)) {
                continue;
            }

            // do we have a new default value for this row?
            if (values != null && values.containsKey(key)) {
                // the value may be an integer so we need to use Object and toString when putting back in row
                Object newDefaultValue = values.get(key);
                if (newDefaultValue != null) {
                    row.put("defaultValue", newDefaultValue.toString());
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
            String line = buildJSonLineFromRow(row);

            if (!first) {
                sb.append(",\n");
            }
            sb.append("    ").append(line);

            first = false;
        }
        if (!first) {
            sb.append("\n");
        }

        sb.append("  },\n");
        return sb.toString();
    }

    private String buildConnectorOptionsSchema(Map dto) throws JsonProcessingException {
        // find the endpoint options
        Map<String, Map> properties = (Map) dto.get("connectorProperties");

        StringBuilder sb = new StringBuilder();
        sb.append("  \"connectorProperties\": {\n");

        AtomicBoolean first = new AtomicBoolean(true);

        if (properties != null) {
            for (Map.Entry<String, Map> entry: properties.entrySet()) {
                Map row = entry.getValue();
                row.put("name", entry.getKey());

                String line = buildJSonLineFromRow(row);

                if (!first.get()) {
                    sb.append(",\n");
                }
                sb.append("    ").append(line);

                first.set(false);
            }
        }

        if (!first.get()) {
            sb.append("\n");
        }

        sb.append("  }\n");
        return sb.toString();
    }

    private String buildComponentHeaderSchema(List<Map<String, String>> rows, Map dto, String gitUrl) throws Exception {
        String baseScheme = (String) dto.get("baseScheme");
        String title = (String) dto.get("name");
        String scheme = (String) dto.get("scheme");
        if (scheme == null || scheme.isEmpty()) {
            scheme = StringHelper.camelCaseToDash(title);
        }
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
        String deprecated = getOption(rows, "deprecated");
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
            // ensure description is sanitized
            String text = JSonSchemaHelper.sanitizeDescription(description, false);
            sb.append("    \"description\": \"" + text + "\",\n");
        }
        if (label != null) {
            sb.append("    \"label\": \"" + label + "\",\n");
        }
        if (deprecated != null) {
            sb.append("    \"deprecated\": " + deprecated + ",\n");
        }
        if (async != null) {
            sb.append("    \"async\": " + async + ",\n");
        }
        if (producerOnly != null) {
            sb.append("    \"producerOnly\": " + producerOnly + ",\n");
        } else if (consumerOnly != null) {
            sb.append("    \"consumerOnly\": " + consumerOnly + ",\n");
        }
        if (lenientProperties != null) {
            sb.append("    \"lenientProperties\": " + lenientProperties + ",\n");
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

            // find the artifact on the classpath that has the Camel component this connector is using
            // then we want to grab its json schema file to embed in this JAR so we have all files together

            if (scheme != null && groupId != null && artifactId != null) {
                for (Object obj : getProject().getDependencyArtifacts()) {
                    Artifact artifact = (Artifact) obj;
                    if ("jar".equals(artifact.getType())) {
                        // use baseVersion so we can support SNAPSHOT versions that are based on a base version
                        if (groupId.equals(artifact.getGroupId()) && artifactId.equals(artifact.getArtifactId())) {
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

                                    // updating to use correct base version in camel-connector.json
                                    String baseVersion = artifact.getVersion();
                                    updateVersionInCamelConnectorJSon("baseVersion", baseVersion);

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

    private void updateVersionInCamelConnectorJSon(String qualifier, String version) throws MojoExecutionException {
        File file = new File(classesDirectory, "camel-connector.json");
        if (file.exists()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                Map dto = mapper.readValue(file, Map.class);

                if (version != null) {
                    String existingVersion = (String) dto.get(qualifier);
                    if (existingVersion == null || !existingVersion.equals(version)) {
                        dto.put(qualifier, version);
                        // update file
                        mapper.writerWithDefaultPrettyPrinter().writeValue(file, dto);
                        // project root folder
                        File root = classesDirectory.getParentFile().getParentFile();
                        // update source file also
                        file = new File(root, "src/main/resources/camel-connector.json");
                        if (file.exists()) {
                            getLog().info("Updating " + qualifier + " to " + version + " in " + file);
                            mapper.writerWithDefaultPrettyPrinter().writeValue(file, dto);
                        }
                    }
                }
            } catch (Exception e) {
                throw new MojoExecutionException("Error in camel-connector-maven-plugin", e);
            }
        }
    }

    /**
     * Builds a JSon line of the given row
     */
    private static String buildJSonLineFromRow(Map<String, String> row) {
        String name = row.get("name");
        String displayName = row.get("displayName");
        String kind = row.get("kind");
        boolean required = false;
        Object value = row.getOrDefault("required", "false");
        if (value instanceof Boolean) {
            required = (Boolean) value;
        } else if (value != null) {
            required = Boolean.valueOf(value.toString());
        }
        String javaType = row.get("javaType");
        String defaultValue = row.get("defaultValue");
        String description = row.get("description");
        boolean deprecated = false;
        value = row.getOrDefault("deprecated", "false");
        if (value instanceof Boolean) {
            deprecated = (Boolean) value;
        } else if (value != null) {
            deprecated = Boolean.valueOf(value.toString());
        }
        boolean secret = false;
        value = row.getOrDefault("secret", "false");
        if (value instanceof Boolean) {
            secret = (Boolean) value;
        } else if (value != null) {
            secret = Boolean.valueOf(value.toString());
        }
        String group = row.get("group");
        String label = row.get("label");
        // for enum we need to build it back as a set
        Set<String> enums = null;
        // the enum can either be a List or String
        value = row.get("enum");
        if (value instanceof List) {
            enums = new LinkedHashSet<String>((List)value);
        } else if (value instanceof String) {
            String[] array = value.toString().split(",");
            enums = Arrays.stream(array).collect(Collectors.toSet());
        }
        boolean enumType = enums != null;
        String optionalPrefix = row.get("optionalPrefix");
        String prefix = row.get("prefix");
        boolean multiValue = false;
        value = row.getOrDefault("multiValue", "false");
        if (value instanceof Boolean) {
            multiValue = (Boolean) value;
        } else if (value != null) {
            multiValue = Boolean.valueOf(value.toString());
        }

        return JSonSchemaHelper.toJson(name, displayName, kind, required, javaType, defaultValue, description, deprecated, secret, group, label,
            enumType, enums, false, null, false, optionalPrefix, prefix, multiValue);
    }

    private static String extractClass(List<String> lines) {
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("class=")) {
                return line.substring(6);
            }
        }
        return null;
    }

    private static String extractScheme(Map map) {
        return (String) map.get("baseScheme");
    }

    private static String extractGroupId(Map map) {
        return (String) map.get("baseGroupId");
    }

    private static String extractArtifactId(Map map) {
        return (String) map.get("baseArtifactId");
    }

    private static String extractVersion(Map map) {
        return (String) map.get("baseVersion");
    }

}
