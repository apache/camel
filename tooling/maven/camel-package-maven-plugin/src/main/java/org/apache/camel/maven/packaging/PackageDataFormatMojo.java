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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
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
import org.sonatype.plexus.build.incremental.BuildContext;

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
     * build context to check changed files and mark them for refresh (used for
     * m2e compatibility)
     * 
     * @component
     * @readonly
     */
    private BuildContext buildContext;

    /**
     * Execute goal.
     *
     * @throws org.apache.maven.plugin.MojoExecutionException execution of the main class or one of the
     *                                                        threads it generated failed.
     * @throws org.apache.maven.plugin.MojoFailureException   something bad happened...
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        prepareDataFormat(getLog(), project, projectHelper, dataFormatOutDir, schemaOutDir, buildContext);
    }

    public static void prepareDataFormat(Log log, MavenProject project, MavenProjectHelper projectHelper, File dataFormatOutDir,
                                         File schemaOutDir, BuildContext buildContext) throws MojoExecutionException {

        File camelMetaDir = new File(dataFormatOutDir, "META-INF/services/org/apache/camel/");

        // first we need to setup the output directory because the next check
        // can stop the build before the end and eclipse always needs to know about that directory 
        if (projectHelper != null) {
            projectHelper.addResource(project, dataFormatOutDir.getPath(), Collections.singletonList("**/dataformat.properties"), Collections.emptyList());
        }

        if (!PackageHelper.haveResourcesChanged(log, project, buildContext, "META-INF/services/org/apache/camel/dataformat")) {
            return;
        }

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
                        String javaType = readClassFromCamelResource(file, buffer, buildContext);
                        if (!file.isDirectory() && file.getName().charAt(0) != '.') {
                            count++;
                        }
                        if (javaType != null) {
                            javaTypes.put(file.getName(), javaType);
                        }
                    }
                }
            }
        }

        // is this from Apache Camel then the data format is out of the box and we should enrich the json schema with more details
        boolean apacheCamel = "org.apache.camel".equals(project.getGroupId());

        // find camel-core and grab the data format model from there, and enrich this model with information from this artifact
        // and create json schema model file for this data format
        try {
            if (apacheCamel && count > 0) {
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

                            DataFormatModel dataFormatModel = extractDataFormatModel(project, json, modelName, name, javaType);
                            log.debug("Model " + dataFormatModel);

                            // build json schema for the data format
                            String properties = after(json, "  \"properties\": {");

                            // special prepare for bindy/json properties
                            properties = prepareBindyProperties(name, properties);
                            properties = prepareJsonProperties(name, properties);

                            String schema = createParameterJsonSchema(dataFormatModel, properties);
                            log.debug("JSon schema\n" + schema);

                            // write this to the directory
                            File dir = new File(schemaOutDir, schemaSubDirectory(dataFormatModel.getJavaType()));
                            dir.mkdirs();

                            File out = new File(dir, name + ".json");
                            OutputStream fos = buildContext.newFileOutputStream(out);
                            fos.write(schema.getBytes());
                            fos.close();
                            if (log.isDebugEnabled()) {
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

            // check if the existing file has the same content, and if so then leave it as is so we do not write any changes
            // which can cause a re-compile of all the source code
            if (outFile.exists()) {
                try {
                    Properties existing = new Properties();

                    InputStream is = new FileInputStream(outFile);
                    existing.load(is);
                    is.close();

                    // are the content the same?
                    if (existing.equals(properties)) {
                        log.debug("No dataformat changes detected");
                        return;
                    }
                } catch (IOException e) {
                    // ignore
                }
            }

            try {
                OutputStream os = buildContext.newFileOutputStream(outFile);
                properties.store(os, "Generated by camel-package-maven-plugin");
                os.close();

                log.info("Generated " + outFile + " containing " + count + " Camel " + (count > 1 ? "dataformats: " : "dataformat: ") + names);

            } catch (IOException e) {
                throw new MojoExecutionException("Failed to write properties to " + outFile + ". Reason: " + e, e);
            }
        } else {
            log.debug("No META-INF/services/org/apache/camel/dataformat directory found. Are you sure you have created a Camel data format?");
        }
    }

    private static DataFormatModel extractDataFormatModel(MavenProject project, String json, String modelName, String name, String javaType) throws Exception {
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
            if (row.containsKey("deprecated")) {
                dataFormatModel.setDeprecated(row.get("deprecated"));
            }
            if (row.containsKey("deprecationNote")) {
                dataFormatModel.setDeprecationNote(row.get("deprecationNote"));
            }
            if (row.containsKey("javaType")) {
                dataFormatModel.setModelJavaType(row.get("javaType"));
            }
            if (row.containsKey("firstVersion")) {
                dataFormatModel.setFirstVersion(row.get("firstVersion"));
            }
            // favor description from the model schema
            if (row.containsKey("description")) {
                dataFormatModel.setDescription(row.get("description"));
            }
        }

        // first version special for json
        String firstVersion = prepareJsonFirstVersion(name);
        if (firstVersion != null) {
            dataFormatModel.setFirstVersion(firstVersion);
        }

        return dataFormatModel;
    }

    private static String prepareBindyProperties(String name, String properties) {
        String bindy = "\"enum\": [ \"Csv\", \"Fixed\", \"KeyValue\" ], \"deprecated\": \"false\", \"secret\": \"false\"";
        String bindyCsv = "\"enum\": [ \"Csv\", \"Fixed\", \"KeyValue\" ], \"deprecated\": \"false\", \"secret\": \"false\", \"defaultValue\": \"Csv\"";
        String bindyFixed = "\"enum\": [ \"Csv\", \"Fixed\", \"KeyValue\" ], \"deprecated\": \"false\", \"secret\": \"false\", \"defaultValue\": \"Fixed\"";
        String bindyKvp = "\"enum\": [ \"Csv\", \"Fixed\", \"KeyValue\" ], \"deprecated\": \"false\", \"secret\": \"false\", \"defaultValue\": \"KeyValue\"";

        if ("bindy-csv".equals(name)) {
            properties = properties.replace(bindy, bindyCsv);
        } else if ("bindy-fixed".equals(name)) {
            properties = properties.replace(bindy, bindyFixed);
        } else if ("bindy-kvp".equals(name)) {
            properties = properties.replace(bindy, bindyKvp);
        }

        return properties;
    }

    private static String prepareJsonProperties(String name, String properties) {
        String json = "\"enum\": [ \"Gson\", \"Jackson\", \"Johnzon\", \"XStream\", \"Fastjson\" ], \"deprecated\": \"false\", \"secret\": \"false\", \"defaultValue\": \"XStream\"";
        String jsonGson = "\"enum\": [ \"Gson\", \"Jackson\", \"Johnzon\", \"XStream\", \"Fastjson\" ], \"deprecated\": \"false\", \"secret\": \"false\", \"defaultValue\": \"Gson\"";
        String jsonJackson = "\"enum\": [ \"Gson\", \"Jackson\", \"Johnzon\", \"XStream\", \"Fastjson\" ], \"deprecated\": \"false\", \"secret\": \"false\", \"defaultValue\": \"Jackson\"";
        String jsonJohnzon = "\"enum\": [ \"Gson\", \"Jackson\", \"Johnzon\", \"XStream\", \"Fastjson\" ], \"deprecated\": \"false\", \"secret\": \"false\", \"defaultValue\": \"Johnzon\"";
        String jsonXStream = "\"enum\": [ \"Gson\", \"Jackson\", \"Johnzon\", \"XStream\", \"Fastjson\" ], \"deprecated\": \"false\", \"secret\": \"false\", \"defaultValue\": \"XStream\"";
        String jsonFastjson = "\"enum\": [ \"Gson\", \"Jackson\", \"Johnzon\", \"XStream\", \"Fastjson\" ], \"deprecated\": \"false\", \"secret\": \"false\", \"defaultValue\": \"Fastjson\"";

        if ("json-gson".equals(name)) {
            properties = properties.replace(json, jsonGson);
        } else if ("json-jackson".equals(name)) {
            properties = properties.replace(json, jsonJackson);
        } else if ("json-johnzon".equals(name)) {
            properties = properties.replace(json, jsonJohnzon);
        } else if ("json-xstream".equals(name)) {
            properties = properties.replace(json, jsonXStream);
        } else if ("json-fastjson".equals(name)) {
            properties = properties.replace(json, jsonFastjson);
        }

        return properties;
    }

    private static String prepareJsonFirstVersion(String name) {
        if ("json-gson".equals(name)) {
            return "2.10.0";
        } else if ("json-jackson".equals(name)) {
            return "2.0.0";
        } else if ("json-johnzon".equals(name)) {
            return "2.18.0";
        } else if ("json-xstream".equals(name)) {
            return "2.0.0";
        } else if ("json-fastjson".equals(name)) {
            return "2.20.0";
        }

        return null;
    }

    private static String readClassFromCamelResource(File file, StringBuilder buffer, BuildContext buildContext) throws MojoExecutionException {
        // skip directories as there may be a sub .resolver directory
        if (file.isDirectory()) {
            return null;
        }
        String name = file.getName();
        if (name.charAt(0) != '.') {
            if (buffer.length() > 0) {
                buffer.append(" ");
            }
            buffer.append(name);
        }

        if (!buildContext.hasDelta(file)) {
            // if this file has not changed,
            // then no need to store the javatype
            // for the json file to be generated again
            // (but we do need the name above!)
            return null;
        }

        // find out the javaType for each data format
        try {
            String text = loadText(new FileInputStream(file));
            Map<String, String> map = parseAsMap(text);
            return map.get("class");
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read file " + file + ". Reason: " + e, e);
        }
    }

    private static String asModelName(String name) {
        // special for some data formats
        if ("json-gson".equals(name) || "json-jackson".equals(name) || "json-johnzon".equals(name) || "json-xstream".equals(name) || "json-fastjson".equals(name)) {
            return "json";
        } else if ("bindy-csv".equals(name) || "bindy-fixed".equals(name) || "bindy-kvp".equals(name)) {
            return "bindy";
        } else if ("zipfile".equals(name)) {
            // darn should have been lower case
            return "zipFile";
        } else if ("yaml-snakeyaml".equals(name)) {
            return "yaml";
        }
        return name;
    }

    private static String asModelTitle(String name, String title) {
        // special for some data formats
        if ("json-gson".equals(name)) {
            return "JSon GSon";
        } else if ("json-jackson".equals(name)) {
            return "JSon Jackson";
        } else if ("json-johnzon".equals(name)) {
            return "JSon Johnzon";
        } else if ("json-xstream".equals(name)) {
            return "JSon XStream";
        } else if ("json-fastjson".equals(name)) {
            return "JSon Fastjson";
        } else if ("bindy-csv".equals(name)) {
            return "Bindy CSV";
        } else if ("bindy-fixed".equals(name)) {
            return "Bindy Fixed Length";
        } else if ("bindy-kvp".equals(name)) {
            return "Bindy Key Value Pair";
        } else if ("yaml-snakeyaml".equals(name)) {
            return "YAML SnakeYAML";
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
        // dataformat model
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
        boolean deprecated = "true".equals(dataFormatModel.getDeprecated());
        buffer.append("\n    \"deprecated\": ").append(deprecated).append(",");
        if (dataFormatModel.getFirstVersion() != null) {
            buffer.append("\n    \"firstVersion\": \"").append(dataFormatModel.getFirstVersion()).append("\",");
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
        private String firstVersion;
        private String label;
        private String deprecated;
        private String deprecationNote;
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

        public String getFirstVersion() {
            return firstVersion;
        }

        public void setFirstVersion(String firstVersion) {
            this.firstVersion = firstVersion;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getDeprecated() {
            return deprecated;
        }

        public void setDeprecated(String deprecated) {
            this.deprecated = deprecated;
        }

        public String getDeprecationNote() {
            return deprecationNote;
        }

        public void setDeprecationNote(String deprecationNote) {
            this.deprecationNote = deprecationNote;
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
                    + ", deprecated='" + deprecated + '\''
                    + ", javaType='" + javaType + '\''
                    + ", modelJavaType='" + modelJavaType + '\''
                    + ", groupId='" + groupId + '\''
                    + ", artifactId='" + artifactId + '\''
                    + ", version='" + version + '\''
                    + ']';
        }
    }

}
