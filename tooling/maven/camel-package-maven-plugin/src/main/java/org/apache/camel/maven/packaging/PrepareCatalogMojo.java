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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

/**
 * Prepares the camel catalog to include component, data format, and eip descriptors,
 * and generates a report.
 *
 * @goal prepare-catalog
 * @execute phase="process-resources"
 */
public class PrepareCatalogMojo extends AbstractMojo {

    public static final int BUFFER_SIZE = 128 * 1024;

    private static final Pattern LABEL_PATTERN = Pattern.compile("\\\"label\\\":\\s\\\"([\\w,]+)\\\"");

    /**
     * The maven project.
     *
     * @parameter property="project"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * The output directory for components catalog
     *
     * @parameter default-value="${project.build.directory}/classes/org/apache/camel/catalog/components"
     */
    protected File componentsOutDir;

    /**
     * The output directory for dataformats catalog
     *
     * @parameter default-value="${project.build.directory}/classes/org/apache/camel/catalog/dataformats"
     */
    protected File dataFormatsOutDir;

    /**
     * The output directory for models catalog
     *
     * @parameter default-value="${project.build.directory}/classes/org/apache/camel/catalog/models"
     */
    protected File modelsOutDir;

    /**
     * The components directory where all the Apache Camel components are
     *
     * @parameter default-value="${project.build.directory}/../../..//components"
     */
    protected File componentsDir;

    /**
     * The camel-core directory where camel-core components are
     *
     * @parameter default-value="${project.build.directory}/../../..//camel-core"
     */
    protected File coreDir;

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
        executeModel();
        executeComponents();
        executeDataFormats();
    }

    protected void executeModel() throws MojoExecutionException, MojoFailureException {
        getLog().info("Copying all Camel model json descriptors");

        // lets use sorted set/maps
        Set<File> jsonFiles = new TreeSet<File>();
        Set<File> duplicateJsonFiles = new TreeSet<File>();
        Set<File> missingLabels = new TreeSet<File>();
        Map<String, Set<String>> usedLabels = new TreeMap<String, Set<String>>();

        // find all json files in camel-core
        if (coreDir != null && coreDir.isDirectory()) {
            File target = new File(coreDir, "target/classes/org/apache/camel/model");
            PackageHelper.findJsonFiles(target, jsonFiles, new PackageHelper.CamelComponentsModelFilter());
        }

        getLog().info("Found " + jsonFiles.size() + " model json files");

        // make sure to create out dir
        modelsOutDir.mkdirs();

        for (File file : jsonFiles) {
            File to = new File(modelsOutDir, file.getName());
            if (to.exists()) {
                duplicateJsonFiles.add(to);
                getLog().warn("Duplicate model name detected: " + to);
            }
            try {
                copyFile(file, to);
            } catch (IOException e) {
                throw new MojoFailureException("Cannot copy file from " + file + " -> " + to, e);
            }

            // check if we have a label as we want the eip to include labels
            try {
                String text = loadText(new FileInputStream(file));
                // just do a basic label check
                if (text.contains("\"label\": \"\"")) {
                    missingLabels.add(file);
                } else {
                    String name = asComponentName(file);
                    Matcher matcher = LABEL_PATTERN.matcher(text);
                    // grab the label, and remember it in the used labels
                    if (matcher.find()) {
                        String label = matcher.group(1);
                        String[] labels = label.split(",");
                        for (String s : labels) {
                            Set<String> models = usedLabels.get(s);
                            if (models == null) {
                                models = new TreeSet<String>();
                                usedLabels.put(s, models);
                            }
                            models.add(name);
                        }
                    }
                }
            } catch (IOException e) {
                // ignore
            }
        }

        File all = new File(modelsOutDir, "../models.properties");
        try {
            FileOutputStream fos = new FileOutputStream(all, false);

            String[] names = modelsOutDir.list();
            List<String> models = new ArrayList<String>();
            // sort the names
            for (String name : names) {
                if (name.endsWith(".json")) {
                    // strip out .json from the name
                    String modelName = name.substring(0, name.length() - 5);
                    models.add(modelName);
                }
            }

            Collections.sort(models);
            for (String name : models) {
                fos.write(name.getBytes());
                fos.write("\n".getBytes());
            }

            fos.close();

        } catch (IOException e) {
            throw new MojoFailureException("Error writing to file " + all);
        }

        printModelsReport(jsonFiles, duplicateJsonFiles, missingLabels, usedLabels);
    }

    protected void executeComponents() throws MojoExecutionException, MojoFailureException {
        getLog().info("Copying all Camel component json descriptors");

        // lets use sorted set/maps
        Set<File> jsonFiles = new TreeSet<File>();
        Set<File> duplicateJsonFiles = new TreeSet<File>();
        Set<File> componentFiles = new TreeSet<File>();
        Set<File> missingComponents = new TreeSet<File>();
        Set<File> missingLabels = new TreeSet<File>();
        Set<File> missingUriPaths = new TreeSet<File>();
        Map<String, Set<String>> usedLabels = new TreeMap<String, Set<String>>();

        // find all json files in components and camel-core
        if (componentsDir != null && componentsDir.isDirectory()) {
            File[] components = componentsDir.listFiles();
            if (components != null) {
                for (File dir : components) {
                    if (dir.isDirectory() && !"target".equals(dir.getName())) {
                        File target = new File(dir, "target/classes");

                        int before = componentFiles.size();
                        int before2 = jsonFiles.size();

                        findComponentFilesRecursive(target, jsonFiles, componentFiles, new CamelComponentsFileFilter());

                        int after = componentFiles.size();
                        int after2 = jsonFiles.size();
                        if (before != after && before2 == after2) {
                            missingComponents.add(dir);
                        }

                    }
                }
            }
        }
        if (coreDir != null && coreDir.isDirectory()) {
            File target = new File(coreDir, "target/classes");

            int before = componentFiles.size();
            int before2 = jsonFiles.size();

            findComponentFilesRecursive(target, jsonFiles, componentFiles, new CamelComponentsFileFilter());

            int after = componentFiles.size();
            int after2 = jsonFiles.size();
            if (before != after && before2 == after2) {
                missingComponents.add(coreDir);
            }
        }

        getLog().info("Found " + jsonFiles.size() + " component json files");

        // make sure to create out dir
        componentsOutDir.mkdirs();

        for (File file : jsonFiles) {
            File to = new File(componentsOutDir, file.getName());
            if (to.exists()) {
                duplicateJsonFiles.add(to);
                getLog().warn("Duplicate component name detected: " + to);
            }
            try {
                copyFile(file, to);
            } catch (IOException e) {
                throw new MojoFailureException("Cannot copy file from " + file + " -> " + to, e);
            }

            // check if we have a label as we want the components to include labels
            try {
                String text = loadText(new FileInputStream(file));
                // just do a basic label check
                if (text.contains("\"label\": \"\"")) {
                    missingLabels.add(file);
                } else {
                    String name = asComponentName(file);
                    Matcher matcher = LABEL_PATTERN.matcher(text);
                    // grab the label, and remember it in the used labels
                    if (matcher.find()) {
                        String label = matcher.group(1);
                        String[] labels = label.split(",");
                        for (String s : labels) {
                            Set<String> components = usedLabels.get(s);
                            if (components == null) {
                                components = new TreeSet<String>();
                                usedLabels.put(s, components);
                            }
                            components.add(name);
                        }
                    }
                }
                if (!text.contains("\"kind\": \"path\"")) {
                    missingUriPaths.add(file);
                }
            } catch (IOException e) {
                // ignore
            }
        }

        File all = new File(componentsOutDir, "../components.properties");
        try {
            FileOutputStream fos = new FileOutputStream(all, false);

            String[] names = componentsOutDir.list();
            List<String> components = new ArrayList<String>();
            // sort the names
            for (String name : names) {
                if (name.endsWith(".json")) {
                    // strip out .json from the name
                    String componentName = name.substring(0, name.length() - 5);
                    components.add(componentName);
                }
            }

            Collections.sort(components);
            for (String name : components) {
                fos.write(name.getBytes());
                fos.write("\n".getBytes());
            }

            fos.close();

        } catch (IOException e) {
            throw new MojoFailureException("Error writing to file " + all);
        }

        printComponentsReport(jsonFiles, duplicateJsonFiles, missingComponents, missingUriPaths, missingLabels, usedLabels);
    }

    protected void executeDataFormats() throws MojoExecutionException, MojoFailureException {
        getLog().info("Copying all Camel dataformat json descriptors");

        // lets use sorted set/maps
        Set<File> jsonFiles = new TreeSet<File>();
        Set<File> dataFormatFiles = new TreeSet<File>();
        Set<File> missingLabels = new TreeSet<File>();
        Map<String, Set<String>> usedLabels = new TreeMap<String, Set<String>>();
        Map<String, String> javaTypes = new HashMap<String, String>();

        // find all data formats from the components directory
        if (componentsDir != null && componentsDir.isDirectory()) {
            File[] dataFormats = componentsDir.listFiles();
            if (dataFormats != null) {
                for (File dir : dataFormats) {
                    if (dir.isDirectory() && !"target".equals(dir.getName())) {
                        File target = new File(dir, "target/classes");
                        findDataFormatFilesRecursive(target, dataFormatFiles, new CamelDataFormatsFileFilter());
                    }
                    File javaTypesDir = new File(dir, "target/classes/META-INF/services/org/apache/camel/dataformat");
                    findDataFormatJavaTypes(javaTypesDir, javaTypes);
                }
            }
        }
        if (coreDir != null && coreDir.isDirectory()) {
            File target = new File(coreDir, "target/classes");
            findDataFormatFilesRecursive(target, dataFormatFiles, new CamelDataFormatsFileFilter());
        }
        if (coreDir != null && coreDir.isDirectory()) {
            File target = new File(coreDir, "target/classes/org/apache/camel/model/dataformat");
            findDataFormatJsonFiles(target, jsonFiles);
        }

        getLog().info("Found " + dataFormatFiles.size() + " dataformat.properties json files");
        getLog().info("Found " + jsonFiles.size() + " dataformat json files");

        // make sure to create out dir
        dataFormatsOutDir.mkdirs();

        // need to capture which maven projects we have data formats within, so we can enrich the .json files with that data
        Map<String, DataFormatModel> models = new HashMap<String, DataFormatModel>();

        for (File file : dataFormatFiles) {
            try {
                String text = loadText(new FileInputStream(file));
                Map<String, String> map = parseAsMap(text);

                String[] names = map.get("dataFormats").split(" ");
                for (String name : names) {
                    DataFormatModel model = new DataFormatModel();
                    model.setName(name);

                    // TODO: we should likely use the description from the json files as-is
                    String doc = map.get("projectDescription");
                    if (doc != null) {
                        model.setDescription(doc);
                    } else {
                        model.setDescription("");
                    }
                    if (map.containsKey("groupId")) {
                        model.setGroupId(map.get("groupId"));
                    } else {
                        model.setGroupId("");
                    }
                    if (map.containsKey("artifactId")) {
                        model.setArtifactId(map.get("artifactId"));
                    } else {
                        model.setArtifactId("");
                    }
                    if (map.containsKey("version")) {
                        model.setVersionId(map.get("version"));
                    } else {
                        model.setVersionId("");
                    }
                    if (javaTypes.containsKey(name)) {
                        model.setJavaType(javaTypes.get(name));
                    } else {
                        model.setJavaType("");
                    }

                    models.put(name, model);
                    getLog().debug("Dataformat model: " + model);
                }
            } catch (IOException e) {
                // ignore
            }
        }


        for (File file : jsonFiles) {
            // TODO: enrich json file with model data from above

            File to = new File(dataFormatsOutDir, file.getName());
            try {
                copyFile(file, to);
            } catch (IOException e) {
                throw new MojoFailureException("Cannot copy file from " + file + " -> " + to, e);
            }

            // check if we have a label as we want the data format to include labels
            try {
                String text = loadText(new FileInputStream(file));
                // just do a basic label check
                if (text.contains("\"label\": \"\"")) {
                    missingLabels.add(file);
                } else {
                    String name = asComponentName(file);
                    Matcher matcher = LABEL_PATTERN.matcher(text);
                    // grab the label, and remember it in the used labels
                    if (matcher.find()) {
                        String label = matcher.group(1);
                        String[] labels = label.split(",");
                        for (String s : labels) {
                            Set<String> dataFormats = usedLabels.get(s);
                            if (dataFormats == null) {
                                dataFormats = new TreeSet<String>();
                                usedLabels.put(s, dataFormats);
                            }
                            dataFormats.add(name);
                        }
                    }
                }
            } catch (IOException e) {
                // ignore
            }
        }

        File all = new File(dataFormatsOutDir, "../dataformats.properties");
        try {
            FileOutputStream fos = new FileOutputStream(all, false);

            String[] names = dataFormatsOutDir.list();
            List<String> dataFormats = new ArrayList<String>();
            // sort the names
            for (String name : names) {
                if (name.endsWith(".json")) {
                    // strip out .json from the name
                    String dataFormatName = name.substring(0, name.length() - 5);
                    dataFormats.add(dataFormatName);
                }
            }

            Collections.sort(dataFormats);
            for (String name : dataFormats) {
                fos.write(name.getBytes());
                fos.write("\n".getBytes());
            }

            fos.close();

        } catch (IOException e) {
            throw new MojoFailureException("Error writing to file " + all);
        }

        printDataFormatsReport(jsonFiles, missingLabels, usedLabels);
    }

    private void printModelsReport(Set<File> json, Set<File> duplicate, Set<File> missingLabels, Map<String, Set<String>> usedLabels) {
        getLog().info("================================================================================");

        getLog().info("");
        getLog().info("Camel model catalog report");
        getLog().info("");
        getLog().info("\tModels found: " + json.size());
        for (File file : json) {
            getLog().info("\t\t" + asComponentName(file));
        }
        if (!duplicate.isEmpty()) {
            getLog().info("");
            getLog().warn("\tDuplicate models detected: " + duplicate.size());
            for (File file : duplicate) {
                getLog().warn("\t\t" + asComponentName(file));
            }
        }
        if (!missingLabels.isEmpty()) {
            getLog().info("");
            getLog().warn("\tMissing labels detected: " + missingLabels.size());
            for (File file : missingLabels) {
                getLog().warn("\t\t" + asComponentName(file));
            }
        }
        if (!usedLabels.isEmpty()) {
            getLog().info("");
            getLog().info("\tUsed labels: " + usedLabels.size());
            for (Map.Entry<String, Set<String>> entry : usedLabels.entrySet()) {
                getLog().info("\t\t" + entry.getKey() + ":");
                for (String name : entry.getValue()) {
                    getLog().info("\t\t\t" + name);
                }
            }
        }
        getLog().info("");
        getLog().info("================================================================================");
    }

    private void printComponentsReport(Set<File> json, Set<File> duplicate, Set<File> missing, Set<File> missingUriPaths,
                                       Set<File> missingLabels, Map<String, Set<String>> usedLabels) {
        getLog().info("================================================================================");
        getLog().info("");
        getLog().info("Camel component catalog report");
        getLog().info("");
        getLog().info("\tComponents found: " + json.size());
        for (File file : json) {
            getLog().info("\t\t" + asComponentName(file));
        }
        if (!duplicate.isEmpty()) {
            getLog().info("");
            getLog().warn("\tDuplicate components detected: " + duplicate.size());
            for (File file : duplicate) {
                getLog().warn("\t\t" + asComponentName(file));
            }
        }
        if (!missingLabels.isEmpty()) {
            getLog().info("");
            getLog().warn("\tMissing labels detected: " + missingLabels.size());
            for (File file : missingLabels) {
                getLog().warn("\t\t" + asComponentName(file));
            }
        }
        if (!usedLabels.isEmpty()) {
            getLog().info("");
            getLog().info("\tUsed labels: " + usedLabels.size());
            for (Map.Entry<String, Set<String>> entry : usedLabels.entrySet()) {
                getLog().info("\t\t" + entry.getKey() + ":");
                for (String name : entry.getValue()) {
                    getLog().info("\t\t\t" + name);
                }
            }
        }
        if (!missingUriPaths.isEmpty()) {
            getLog().info("");
            getLog().warn("\tMissing @UriPath detected: " + missingUriPaths.size());
            for (File file : missingUriPaths) {
                getLog().warn("\t\t" + asComponentName(file));
            }
        }
        if (!missing.isEmpty()) {
            getLog().info("");
            getLog().warn("\tMissing components detected: " + missing.size());
            for (File name : missing) {
                getLog().warn("\t\t" + name.getName());
            }
        }
        getLog().info("");
        getLog().info("================================================================================");
    }

    private void printDataFormatsReport(Set<File> json, Set<File> missingLabels, Map<String, Set<String>> usedLabels) {
        getLog().info("================================================================================");
        getLog().info("");
        getLog().info("Camel data format catalog report");
        getLog().info("");
        getLog().info("\tDataFormats found: " + json.size());
        for (File file : json) {
            getLog().info("\t\t" + asComponentName(file));
        }
        if (!missingLabels.isEmpty()) {
            getLog().info("");
            getLog().warn("\tMissing labels detected: " + missingLabels.size());
            for (File file : missingLabels) {
                getLog().warn("\t\t" + asComponentName(file));
            }
        }
        if (!usedLabels.isEmpty()) {
            getLog().info("");
            getLog().info("\tUsed labels: " + usedLabels.size());
            for (Map.Entry<String, Set<String>> entry : usedLabels.entrySet()) {
                getLog().info("\t\t" + entry.getKey() + ":");
                for (String name : entry.getValue()) {
                    getLog().info("\t\t\t" + name);
                }
            }
        }
        getLog().info("");
        getLog().info("================================================================================");
    }

    private static String asComponentName(File file) {
        String name = file.getName();
        if (name.endsWith(".json")) {
            return name.substring(0, name.length() - 5);
        }
        return name;
    }

    private void findComponentFilesRecursive(File dir, Set<File> found, Set<File> components, FileFilter filter) {
        File[] files = dir.listFiles(filter);
        if (files != null) {
            for (File file : files) {
                // skip files in root dirs as Camel does not store information there but others may do
                boolean rootDir = "classes".equals(dir.getName()) || "META-INF".equals(dir.getName());
                boolean jsonFile = !rootDir && file.isFile() && file.getName().endsWith(".json");
                boolean componentFile = !rootDir && file.isFile() && file.getName().equals("component.properties");
                if (jsonFile) {
                    found.add(file);
                } else if (componentFile) {
                    components.add(file);
                } else if (file.isDirectory()) {
                    findComponentFilesRecursive(file, found, components, filter);
                }
            }
        }
    }

    private void findDataFormatFilesRecursive(File dir, Set<File> dataFormats, FileFilter filter) {
        File[] files = dir.listFiles(filter);
        if (files != null) {
            for (File file : files) {
                // skip files in root dirs as Camel does not store information there but others may do
                boolean rootDir = "classes".equals(dir.getName()) || "META-INF".equals(dir.getName());
                boolean dataFormatFile = !rootDir && file.isFile() && file.getName().equals("dataformat.properties");
                if (dataFormatFile) {
                    dataFormats.add(file);
                } else if (file.isDirectory()) {
                    findDataFormatFilesRecursive(file, dataFormats, filter);
                }
            }
        }
    }

    private void findDataFormatJavaTypes(File dir, Map<String, String> javaTypes) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                // name of file is the key
                String name = file.getName();
                try {
                    String data = loadText(new FileInputStream(file));
                    Map<String, String> map = parseAsMap(data);
                    String javaType = map.get("class");
                    if (name != null && javaType != null) {
                        javaTypes.put(name, javaType);
                    }
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private void findDataFormatJsonFiles(File dir, Set<File> jsonFiles) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                boolean jsonFile = file.isFile() && file.getName().endsWith(".json");
                if (jsonFile) {
                    jsonFiles.add(file);
                }
            }
        }
    }

    private class CamelComponentsFileFilter implements FileFilter {

        @Override
        public boolean accept(File pathname) {
            if (pathname.isDirectory() && pathname.getName().equals("model")) {
                // do not check the camel-core model packages as there is no components there
                return false;
            }
            return pathname.isDirectory() || pathname.getName().endsWith(".json")
                    || (pathname.isFile() && pathname.getName().equals("component.properties"));
        }
    }

    private class CamelDataFormatsFileFilter implements FileFilter {

        @Override
        public boolean accept(File pathname) {
            if (pathname.isDirectory() && pathname.getName().equals("model")) {
                // do not check the camel-core model packages as there is no components there
                return false;
            }
            return pathname.isDirectory() || (pathname.isFile() && pathname.getName().equals("dataformat.properties"));
        }
    }

    public static void copyFile(File from, File to) throws IOException {
        FileChannel in = null;
        FileChannel out = null;
        try {
            in = new FileInputStream(from).getChannel();
            out = new FileOutputStream(to).getChannel();

            long size = in.size();
            long position = 0;
            while (position < size) {
                position += in.transferTo(position, BUFFER_SIZE, out);
            }
        } finally {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }

    /**
     * Loads the entire stream into memory as a String and returns it.
     * <p/>
     * <b>Notice:</b> This implementation appends a <tt>\n</tt> as line
     * terminator at the of the text.
     * <p/>
     * Warning, don't use for crazy big streams :)
     */
    public static String loadText(InputStream in) throws IOException {
        StringBuilder builder = new StringBuilder();
        InputStreamReader isr = new InputStreamReader(in);
        try {
            BufferedReader reader = new LineNumberReader(isr);
            while (true) {
                String line = reader.readLine();
                if (line != null) {
                    builder.append(line);
                    builder.append("\n");
                } else {
                    break;
                }
            }
            return builder.toString();
        } finally {
            isr.close();
            in.close();
        }
    }

    protected Map<String, String> parseAsMap(String data) {
        Map<String, String> answer = new HashMap<String, String>();
        String[] lines = data.split("\n");
        for (String line : lines) {
            int idx = line.indexOf('=');
            if (idx != -1) {
                String key = line.substring(0, idx);
                String value = line.substring(idx + 1);
                // remove ending line break for the values
                value = value.trim().replaceAll("\n", "");
                answer.put(key.trim(), value);
            }
        }
        return answer;
    }

    private static class DataFormatModel {
        private String name;
        private String description;
        private String javaType;
        private String groupId;
        private String artifactId;
        private String versionId;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
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

        public String getVersionId() {
            return versionId;
        }

        public void setVersionId(String versionId) {
            this.versionId = versionId;
        }

        @Override
        public String toString() {
            return "DataFormatModel["
                    + "name='" + name + '\''
                    + ", description='" + description + '\''
                    + ", javaType='" + javaType + '\''
                    + ", groupId='" + groupId + '\''
                    + ", artifactId='" + artifactId + '\''
                    + ", versionId='" + versionId + '\''
                    + ']';
        }
    }

}