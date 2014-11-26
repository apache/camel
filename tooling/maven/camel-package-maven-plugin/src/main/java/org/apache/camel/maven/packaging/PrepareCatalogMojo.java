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
import java.util.LinkedHashSet;
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
 * Prepares the camel catalog to include component descriptors, and generates a report with components which have not been migrated
 * to include component json descriptors.
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
    protected File outDir;

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
        getLog().info("Copying all Camel component json descriptors");

        Set<File> jsonFiles = new LinkedHashSet<File>();
        Set<File> duplicateJsonFiles = new LinkedHashSet<File>();
        Set<File> componentFiles = new LinkedHashSet<File>();
        Set<File> missingComponents = new LinkedHashSet<File>();
        Set<File> missingLabels = new LinkedHashSet<File>();
        Set<File> missingUriPaths = new LinkedHashSet<File>();
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

                        findFilesRecursive(target, jsonFiles, componentFiles, new CamelComponentsFileFilter());

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

            findFilesRecursive(target, jsonFiles, componentFiles, new CamelComponentsFileFilter());

            int after = componentFiles.size();
            int after2 = jsonFiles.size();
            if (before != after && before2 == after2) {
                missingComponents.add(coreDir);
            }
        }

        getLog().info("Found " + jsonFiles.size() + " component json files");

        // make sure to create out dir
        outDir.mkdirs();

        for (File file : jsonFiles) {
            File to = new File(outDir, file.getName());
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

        File all = new File(outDir, "../components.properties");
        try {
            FileOutputStream fos = new FileOutputStream(all, false);

            String[] names = outDir.list();
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

        printReport(jsonFiles, duplicateJsonFiles, missingComponents, missingUriPaths, missingLabels, usedLabels);
    }

    private void printReport(Set<File> json, Set<File> duplicate, Set<File> missing, Set<File> missingUriPaths,
                             Set<File> missingLabels, Map<String, Set<String>> usedLabels) {
        getLog().info("================================================================================");
        getLog().info("");
        getLog().info("Camel component catalog report");
        getLog().info("");
        getLog().info("\tComponents found: " + json.size());
        for (File file : json) {
            getLog().info("\t\t" + asComponentName(file));
        }
        getLog().info("");
        if (!duplicate.isEmpty()) {
            getLog().warn("\tDuplicate components detected: " + duplicate.size());
            for (File file : duplicate) {
                getLog().warn("\t\t" + asComponentName(file));
            }
        }
        getLog().info("");
        if (!missingLabels.isEmpty()) {
            getLog().warn("\tMissing labels detected: " + missingLabels.size());
            for (File file : missingLabels) {
                getLog().warn("\t\t" + asComponentName(file));
            }
        }
        getLog().info("");
        if (!usedLabels.isEmpty()) {
            getLog().info("\tUsed labels: " + usedLabels.size());
            for (Map.Entry<String, Set<String>> entry : usedLabels.entrySet()) {
                getLog().info("\t\t" + entry.getKey() + ":");
                for (String name : entry.getValue()) {
                    getLog().info("\t\t\t" + name);
                }
            }
        }
        getLog().info("");
        if (!missingUriPaths.isEmpty()) {
            getLog().warn("\tMissing @UriPath detected: " + missingUriPaths.size());
            for (File file : missingUriPaths) {
                getLog().warn("\t\t" + asComponentName(file));
            }
        }
        getLog().info("");
        if (!missing.isEmpty()) {
            getLog().warn("\tMissing components detected: " + missing.size());
            for (File name : missing) {
                getLog().warn("\t\t" + name.getName());
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

    private void findFilesRecursive(File dir, Set<File> found, Set<File> components, FileFilter filter) {
        File[] files = dir.listFiles(filter);
        if (files != null) {
            for (File file : files) {
                boolean jsonFile = file.isFile() && file.getName().endsWith(".json");
                boolean componentFile = file.isFile() && file.getName().equals("component.properties");
                if (jsonFile) {
                    found.add(file);
                } else if (componentFile) {
                    components.add(file);
                } else if (file.isDirectory()) {
                    findFilesRecursive(file, found, components, filter);
                }
            }
        }
    }

    private class CamelComponentsFileFilter implements FileFilter {

        @Override
        public boolean accept(File pathname) {
            return pathname.isDirectory() || pathname.getName().endsWith(".json")
                    || (pathname.isFile() && pathname.getName().equals("component.properties"));
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

}