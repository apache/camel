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
package org.apache.camel.maven.packaging;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import static org.apache.camel.maven.packaging.PackageHelper.after;
import static org.apache.camel.maven.packaging.PackageHelper.loadText;
import static org.apache.camel.maven.packaging.PackageHelper.parseAsMap;

/**
 * Analyses the Camel plugins in a project and generates extra descriptor information for easier auto-discovery in Camel.
 *
 * @goal generate-dataformats-list
 */
public class PackageDataFormatMojo extends AbstractMojo {

    /**
     * The maven project.
     *
     * @parameter property="project"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * The output directory for generated dataformats file
     *
     * @parameter default-value="${project.build.directory}/generated/camel/dataformats"
     */
    protected File dataFormatOutDir;

    /**
     * The output directory for generated dataformats file
     *
     * @parameter default-value="${project.build.directory}/classes"
     */
    protected File schemaOutDir;

    /**
     * Maven ProjectHelper.
     *
     * @component
     * @readonly
     */
    private MavenProjectHelper projectHelper;

    /**
     * Execute goal.
     *
     * @throws org.apache.maven.plugin.MojoExecutionException execution of the main class or one of the
     *                                                        threads it generated failed.
     * @throws org.apache.maven.plugin.MojoFailureException   something bad happened...
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        prepareDataFormat(getLog(), project, projectHelper, dataFormatOutDir, schemaOutDir);
    }

    public static void prepareDataFormat(Log log, MavenProject project, MavenProjectHelper projectHelper, File dataFormatOutDir, File schemaOutDir) throws MojoExecutionException {
        File camelMetaDir = new File(dataFormatOutDir, "META-INF/services/org/apache/camel/");

        Map<String, String> javaTypes = new HashMap<String, String>();

        StringBuilder buffer = new StringBuilder();
        int count = 0;
        for (Resource r : project.getBuild().getResources()) {
            File f = new File(r.getDirectory());
            if (!f.exists()) {
                f = new File(project.getBasedir(), r.getDirectory());
            }
            f = new File(f, "META-INF/services/org/apache/camel/dataformat");

            if (f.exists() && f.isDirectory()) {
                File[] files = f.listFiles();
                if (files != null) {
                    for (File file : files) {
                        // skip directories as there may be a sub .resolver directory
                        if (file.isDirectory()) {
                            continue;
                        }
                        String name = file.getName();
                        if (name.charAt(0) != '.') {
                            count++;
                            if (buffer.length() > 0) {
                                buffer.append(" ");
                            }
                            buffer.append(name);
                        }

                        // find out the javaType for each data format
                        try {
                            String text = loadText(new FileInputStream(file));
                            Map<String, String> map = parseAsMap(text);
                            String javaType = map.get("class");
                            if (javaType != null) {
                                javaTypes.put(name, javaType);
                            }
                        } catch (IOException e) {
                            throw new MojoExecutionException("Failed to read file " + file + ". Reason: " + e, e);
                        }
                    }
                }
            }
        }

        // find camel-core and grab the data format model from there, and enrich this model with information from this artifact
        // and create json schema model file for this data format
        try {
            if (count > 0) {
                Artifact camelCore = findCamelCoreArtifact(project);
                if (camelCore != null) {
                    File core = camelCore.getFile();
                    if (core != null) {
                        URL url = new URL("file", null, core.getAbsolutePath());
                        URLClassLoader loader = new URLClassLoader(new URL[]{url});
                        for (Map.Entry<String, String> entry : javaTypes.entrySet()) {
                            String name = entry.getKey();
                            String javaType = entry.getValue();
                            String modelName = asModelName(name);

                            InputStream is = loader.getResourceAsStream("org/apache/camel/model/dataformat/" + modelName + ".json");
                            if (is == null) {
                                // use file input stream if we build camel-core itself, and thus do not have a JAR which can be loaded by URLClassLoader
                                is = new FileInputStream(new File(core, "org/apache/camel/model/dataformat/" + modelName + ".json"));
                            }
                            String json = loadText(is);
                            if (json != null) {
                                DataFormatModel dataFormatModel = new DataFormatModel();
                                dataFormatModel.setName(name);
                                dataFormatModel.setTitle("");
                                dataFormatModel.setModelName(modelName);
                                dataFormatModel.setLabel("");
                                dataFormatModel.setDescription(project.getDescription());
                                dataFormatModel.setJavaType(javaType);
                                dataFormatModel.setGroupId(project.getGroupId());
                                dataFormatModel.setArtifactId(project.getArtifactId());
                                dataFormatModel.setVersion(project.getVersion());

                                List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("model", json, false);
                                for (Map<String, String> row : rows) {
                                    if (row.containsKey("title")) {
                                        String title = row.get("title");
                                        dataFormatModel.setTitle(asModelTitle(name, title));
                                    }
                                    if (row.containsKey("label")) {
                                        dataFormatModel.setLabel(row.get("label"));
                                    }
                                    if (row.containsKey("javaType")) {
                                        dataFormatModel.setModelJavaType(row.get("javaType"));
                                    }
                                    // override description for camel-core, as otherwise its too generic
                                    if ("camel-core".equals(project.getArtifactId())) {
                                        if (row.containsKey("description")) {
                                            dataFormatModel.setLabel(row.get("description"));
                                        }
                                    }
                                }
                                log.debug("Model " + dataFormatModel);

                                // build json schema for the data format
                                String properties = after(json, "  \"properties\": {");
                                String schema = createParameterJsonSchema(dataFormatModel, properties);
                                log.debug("JSon schema\n" + schema);

                                // write this to the directory
                                File dir = new File(schemaOutDir, schemaSubDirectory(dataFormatModel.getJavaType()));
                                dir.mkdirs();

                                File out = new File(dir, name + ".json");
                                FileOutputStream fos = new FileOutputStream(out, false);
                                fos.write(schema.getBytes());
                                fos.close();

                                log.debug("Generated " + out + " containing JSon schema for " + name + " data format");
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error loading dataformat model from camel-core. Reason: " + e, e);
        }

        if (count > 0) {
            Properties properties = new Properties();
            String names = buffer.toString();
            properties.put("dataFormats", names);
            properties.put("groupId", project.getGroupId());
            properties.put("artifactId", project.getArtifactId());
            properties.put("version", project.getVersion());
            properties.put("projectName", project.getName());
            if (project.getDescription() != null) {
                properties.put("projectDescription", project.getDescription());
            }

            camelMetaDir.mkdirs();
            File outFile = new File(camelMetaDir, "dataformat.properties");
            try {
                properties.store(new FileWriter(outFile), "Generated by camel-package-maven-plugin");
                log.info("Generated " + outFile + " containing " + count + " Camel " + (count > 1 ? "dataformats: " : "dataformat: ") + names);

                if (projectHelper != null) {
                    List<String> includes = new ArrayList<String>();
                    includes.add("**/dataformat.properties");
                    projectHelper.addResource(project, dataFormatOutDir.getPath(), includes, new ArrayList<String>());
                    projectHelper.attachArtifact(project, "properties", "camelDataFormat", outFile);
                }
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to write properties to " + outFile + ". Reason: " + e, e);
            }
        } else {
            log.debug("No META-INF/services/org/apache/camel/dataformat directory found. Are you sure you have created a Camel data format?");
        }
    }

    private static String asModelName(String name) {
        // special for some data formats
        if ("json-gson".equals(name) || "json-jackson".equals(name) || "json-xstream".equals(name)) {
            return "json";
        } else if ("bindy-csv".equals(name) || "bindy-fixed".equals(name) || "bindy-kvp".equals(name)) {
            return "bindy";
        } else if ("zipfile".equals(name)) {
            // darn should have been lower case
            return "zipFile";
        }
        return name;
    }

    private static String asModelTitle(String name, String title) {
        // special for some data formats
        if ("json-gson".equals(name)) {
            return "JSon GSon";
        } else if ("json-jackson".equals(name)) {
            return "JSon Jackson";
        } else if ("json-xstream".equals(name)) {
            return "JSon XStream";
        } else if ("bindy-csv".equals(name)) {
            return "Bindy CSV";
        } else if ("bindy-fixed".equals(name)) {
            return "Bindy Fixed Length";
        } else if ("bindy-kvp".equals(name)) {
            return "Bindy Key Value Pair";
        }
        return title;
    }

    private static Artifact findCamelCoreArtifact(MavenProject project) {
        // maybe this project is camel-core itself
        Artifact artifact = project.getArtifact();
        if (artifact.getGroupId().equals("org.apache.camel") && artifact.getArtifactId().equals("camel-core")) {
            return artifact;
        }

        // or its a component which has a dependency to camel-core
        Iterator it = project.getDependencyArtifacts().iterator();
        while (it.hasNext()) {
            artifact = (Artifact) it.next();
            if (artifact.getGroupId().equals("org.apache.camel") && artifact.getArtifactId().equals("camel-core")) {
                return artifact;
            }
        }
        return null;
    }

    private static String schemaSubDirectory(String javaType) {
        int idx = javaType.lastIndexOf('.');
        String pckName = javaType.substring(0, idx);
        return pckName.replace('.', '/');
    }

    private static String createParameterJsonSchema(DataFormatModel dataFormatModel, String schema) {
        StringBuilder buffer = new StringBuilder("{");
        // component model
        buffer.append("\n \"dataformat\": {");
        buffer.append("\n    \"name\": \"").append(dataFormatModel.getName()).append("\",");
        buffer.append("\n    \"kind\": \"").append("dataformat").append("\",");
        buffer.append("\n    \"modelName\": \"").append(dataFormatModel.getModelName()).append("\",");
        if (dataFormatModel.getTitle() != null) {
            buffer.append("\n    \"title\": \"").append(dataFormatModel.getTitle()).append("\",");
        }
        if (dataFormatModel.getDescription() != null) {
            buffer.append("\n    \"description\": \"").append(dataFormatModel.getDescription()).append("\",");
        }
        buffer.append("\n    \"label\": \"").append(dataFormatModel.getLabel()).append("\",");
        buffer.append("\n    \"javaType\": \"").append(dataFormatModel.getJavaType()).append("\",");
        if (dataFormatModel.getModelJavaType() != null) {
            buffer.append("\n    \"modelJavaType\": \"").append(dataFormatModel.getModelJavaType()).append("\",");
        }
        buffer.append("\n    \"groupId\": \"").append(dataFormatModel.getGroupId()).append("\",");
        buffer.append("\n    \"artifactId\": \"").append(dataFormatModel.getArtifactId()).append("\",");
        buffer.append("\n    \"version\": \"").append(dataFormatModel.getVersion()).append("\"");
        buffer.append("\n  },");

        buffer.append("\n  \"properties\": {");
        buffer.append(schema);
        return buffer.toString();
    }

    private static class DataFormatModel {
        private String name;
        private String title;
        private String modelName;
        private String description;
        private String label;
        private String javaType;
        private String modelJavaType;
        private String groupId;
        private String artifactId;
        private String version;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public String getModelJavaType() {
            return modelJavaType;
        }

        public void setModelJavaType(String modelJavaType) {
            this.modelJavaType = modelJavaType;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getJavaType() {
            return javaType;
        }

        public void setJavaType(String javaType) {
            this.javaType = javaType;
        }

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public void setArtifactId(String artifactId) {
            this.artifactId = artifactId;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        @Override
        public String toString() {
            return "DataFormatModel["
                    + "name='" + name + '\''
                    + ", title='" + title + '\''
                    + ", modelName='" + modelName + '\''
                    + ", description='" + description + '\''
                    + ", label='" + label + '\''
                    + ", javaType='" + javaType + '\''
                    + ", modelJavaType='" + modelJavaType + '\''
                    + ", groupId='" + groupId + '\''
                    + ", artifactId='" + artifactId + '\''
                    + ", version='" + version + '\''
                    + ']';
        }
    }

}
