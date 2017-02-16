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
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.jar.AbstractJarMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "jar", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, requiresProject = true, threadSafe = true,
        requiresDependencyResolution = ResolutionScope.RUNTIME)
public class ConnectorMojo extends AbstractJarMojo {

    /**
     * Little helper class to read/write the json models.
     */
    public static class ComponentDTO {
        public ComponentHeaderDTO component = new ComponentHeaderDTO();
        public LinkedHashMap<String, Map> componentProperties = new LinkedHashMap<>();
        public LinkedHashMap<String, Map> properties = new LinkedHashMap<>();
    }

    public static class ComponentHeaderDTO {
        public String gitUrl;
        public String kind;
        public String baseScheme;
        public String scheme;
        public String syntax;
        public String title;
        public String description;
        public String label;
        public Boolean deprecated;
        public Boolean async;
        public Boolean producerOnly;
        public Boolean consumerOnly;
        public Boolean lenientProperties;
        public String javaType;
        public String groupId;
        public String artifactId;
        public String version;
    }

    public static class ConnectorDTO {
        public String gitUrl;
        public String groupId;
        public String artifactId;
        public String version;
        public String baseGroupId;
        public String baseArtifactId;
        public String baseVersion;
        public String baseJavaType;
        public String baseScheme;
        public String name;
        public String scheme;
        public String javaType;
        public String description;
        public ArrayList<String> labels = new ArrayList<>();
        public String pattern;
        public ArrayList<String> endpointOptions = new ArrayList<>();
        public Map<String, String> endpointValues = new HashMap<>();
        public Map<String, Object> endpointOverrides = new HashMap<>();
        public ArrayList<String> componentOptions = new ArrayList<>();
        public Map<String, String> componentValues = new HashMap<>();
        public Map<String, Object> componentOverrides = new HashMap<>();
    }


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
                mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
                mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                mapper.enable(SerializationFeature.INDENT_OUTPUT);

                ConnectorDTO connector = mapper.readValue(file, ConnectorDTO.class);

                // embed gitUrl in camel-connector.json file
                if (gitUrl != null) {
                    String existingGitUrl = connector.gitUrl;
                    if (existingGitUrl == null || !existingGitUrl.equals(gitUrl)) {
                        connector.gitUrl = gitUrl;
                        // update file
                        mapper.writeValue(file, connector);
                        // update source file also
                        file = new File(root, "src/main/resources/camel-connector.json");
                        if (file.exists()) {
                            getLog().info("Updating gitUrl to " + file);
                            mapper.writeValue(file, connector);
                        }
                    }
                }


                File schema = embedCamelComponentSchema(connector);
                if (schema != null) {


                    ComponentDTO json = mapper.readValue(schema, ComponentDTO.class);

                    ComponentDTO newJson = new ConnectorMojo.ComponentDTO();

                    newJson.component = buildComponentHeaderSchema(json, connector, gitUrl);
                    getLog().debug(mapper.writeValueAsString(newJson.component));

                    newJson.componentProperties = buildComponentOptionsSchema(json, connector);
                    getLog().debug(mapper.writeValueAsString(newJson.componentProperties));

                    newJson.properties = buildEndpointOptionsSchema(json, connector);
                    getLog().debug(mapper.writeValueAsString(newJson.properties));

                    // generate the json file
                    String newScheme = newJson.component.scheme;

                    // write the json file to the target directory as if camel apt would do it
                    String javaType = connector.javaType;
                    String dir = javaType.substring(0, javaType.lastIndexOf("."));
                    dir = dir.replace('.', '/');
                    File subDir = new File(classesDirectory, dir);
                    String name = newScheme + ".json";
                    File out = new File(subDir, name);

                    FileOutputStream fos = new FileOutputStream(out, false);
                    mapper.writer(new CamelComponentPrettyPrinter()).writeValue(fos, newJson);
                    fos.close();

                    // also write the file in the root folder so its easier to find that for tooling
                    out = new File(classesDirectory, "camel-connector-schema.json");
                    fos = new FileOutputStream(out, false);
                    mapper.writer(new CamelComponentPrettyPrinter()).writeValue(fos, newJson);
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

    private String extractJavaType(String scheme) throws Exception {
        File file = new File(classesDirectory, "META-INF/services/org/apache/camel/component/" + scheme);
        if (file.exists()) {
            List<String> lines = FileHelper.loadFile(file);
            String fqn = extractClass(lines);
            return fqn;
        }
        return null;
    }

    private LinkedHashMap<String, Map> buildComponentOptionsSchema(ComponentDTO component, ConnectorDTO connector) throws JsonProcessingException {
        // find the endpoint options
        List options = connector.componentOptions;
        Map values = connector.componentValues;
        Map overrides = connector.componentOverrides;

        LinkedHashMap<String, Map> rc = new LinkedHashMap<>();
        for (Map.Entry<String, Map> entry : component.componentProperties.entrySet()) {
            HashMap<String, String> row = new HashMap<>(entry.getValue());
            String key = entry.getKey();

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
            rc.put(key, row);
        }
        return rc;
    }

    private LinkedHashMap<String, Map> buildEndpointOptionsSchema(ComponentDTO component, ConnectorDTO connector) throws JsonProcessingException {
        // find the endpoint options
        List options = connector.endpointOptions;
        Map values = connector.endpointValues;
        Map overrides = connector.endpointOverrides;

        LinkedHashMap<String, Map> rc = new LinkedHashMap<>();
        for (Map.Entry<String, Map> entry : component.properties.entrySet()) {
            HashMap<String, String> row = new HashMap<>(entry.getValue());
            String key = entry.getKey();

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
            rc.put(key, row);
        }
        return rc;
    }

    private ComponentHeaderDTO buildComponentHeaderSchema(ComponentDTO component, ConnectorDTO connector, String gitUrl) throws Exception {
        String baseScheme = connector.baseScheme;
        String title = connector.name;
        String scheme = StringHelper.camelCaseToDash(title);
        String baseSyntax = component.component.syntax;
        String syntax = baseSyntax.replaceFirst(baseScheme, scheme);

        String description = connector.description;
        // dto has labels
        String label = null;
        List<String> labels = connector.labels;
        if (labels != null) {
            label = labels.stream().collect(Collectors.joining(","));
        }

        Boolean async = component.component.async;
        String pattern = connector.pattern;
        boolean producerOnly = "To".equalsIgnoreCase(pattern);
        boolean consumerOnly = "From".equalsIgnoreCase(pattern);
        boolean lenientProperties = component.component.lenientProperties == Boolean.TRUE;
        boolean deprecated = component.component.deprecated == Boolean.TRUE;
        String javaType = extractJavaType(scheme);
        String groupId = getProject().getGroupId();
        String artifactId = getProject().getArtifactId();
        String version = getProject().getVersion();

        ComponentHeaderDTO rc = new ComponentHeaderDTO();
        rc.gitUrl = gitUrl;
        rc.kind = "component";
        rc.baseScheme = StringHelper.nullSafe(baseScheme);
        rc.scheme = scheme;
        rc.syntax = syntax;
        rc.title = title;
        rc.description = description;
        rc.label = label;
        rc.deprecated = deprecated;
        rc.async = async;
        rc.producerOnly = producerOnly;
        rc.consumerOnly = consumerOnly;
        rc.lenientProperties = lenientProperties;
        rc.javaType = javaType;
        rc.groupId = groupId;
        rc.artifactId = artifactId;
        rc.version = version;
        return rc;
    }

    /**
     * Finds and embeds the Camel component JSon schema file
     */
    private File embedCamelComponentSchema(ConnectorDTO connector) throws MojoExecutionException {
        try {
            String scheme = connector.baseScheme;
            String groupId = connector.baseGroupId;
            String artifactId = connector.baseArtifactId;
            String version = connector.baseVersion;

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

    private static String extractClass(List<String> lines) {
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("class=")) {
                return line.substring(6);
            }
        }
        return null;
    }

}
