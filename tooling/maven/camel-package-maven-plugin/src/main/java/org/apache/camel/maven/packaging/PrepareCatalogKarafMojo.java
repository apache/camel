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
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import static org.w3c.dom.Node.ELEMENT_NODE;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import static org.apache.camel.maven.packaging.PackageHelper.loadText;

/**
 * Prepares the Karaf provider camel catalog to include component it supports
 *
 * @goal prepare-catalog-karaf
 */
public class PrepareCatalogKarafMojo extends AbstractMojo {

    public static final int BUFFER_SIZE = 128 * 1024;

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
     * @parameter default-value="${project.build.directory}/classes/org/apache/camel/catalog/karaf/components"
     */
    protected File componentsOutDir;

    /**
     * The output directory for dataformats catalog
     *
     * @parameter default-value="${project.build.directory}/classes/org/apache/camel/catalog/karaf/dataformats"
     */
    protected File dataFormatsOutDir;

    /**
     * The output directory for languages catalog
     *
     * @parameter default-value="${project.build.directory}/classes/org/apache/camel/catalog/karaf/languages"
     */
    protected File languagesOutDir;

    /**
     * The output directory for others catalog
     *
     * @parameter default-value="${project.build.directory}/classes/org/apache/camel/catalog/karaf/others"
     */
    protected File othersOutDir;

    /**
     * The karaf features directory
     *
     * @parameter default-value="${project.build.directory}/../../../platforms/karaf/features/src/main/resources/"
     */
    protected File featuresDir;

    /**
     * The components directory where all the Apache Camel components are
     *
     * @parameter default-value="${project.build.directory}/../../../components"
     */
    protected File componentsDir;

    /**
     * The camel-core directory where camel-core components are
     *
     * @parameter default-value="${project.build.directory}/../../../camel-core"
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
     * @throws MojoExecutionException execution of the main class or one of the
     *                                                        threads it generated failed.
     * @throws MojoFailureException   something bad happened...
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        Set<String> features = findKarafFeatures();
        executeComponents(features);
        executeDataFormats(features);
        executeLanguages(features);
        executeOthers(features);
    }

    protected void executeComponents(Set<String> features) throws MojoExecutionException, MojoFailureException {
        getLog().info("Copying all Camel component json descriptors");

        // lets use sorted set/maps
        Set<File> jsonFiles = new TreeSet<>();
        Set<File> componentFiles = new TreeSet<>();

        // find all json files in components and camel-core
        if (componentsDir != null && componentsDir.isDirectory()) {
            File[] components = componentsDir.listFiles();
            if (components != null) {
                for (File dir : components) {
                    if (dir.isDirectory() && !"target".equals(dir.getName())) {
                        File target = new File(dir, "target/classes");

                        // the directory must be in the list of known features
                        if (!features.contains(dir.getName())) {
                            continue;
                        }

                        // special for some components which is in a sub dir
                        if ("camel-as2".equals(dir.getName())) {
                            target = new File(dir, "camel-as2-component/target/classes");
                        } else if ("camel-box".equals(dir.getName())) {
                            target = new File(dir, "camel-box-component/target/classes");
                        } else if ("camel-salesforce".equals(dir.getName())) {
                            target = new File(dir, "camel-salesforce-component/target/classes");
                        } else if ("camel-linkedin".equals(dir.getName())) {
                            target = new File(dir, "camel-linkedin-component/target/classes");
                        } else if ("camel-servicenow".equals(dir.getName())) {
                            target = new File(dir, "camel-servicenow-component/target/classes");
                        }

                        findComponentFilesRecursive(target, jsonFiles, componentFiles, new CamelComponentsFileFilter());
                    }
                }
            }
        }
        if (coreDir != null && coreDir.isDirectory()) {
            File target = new File(coreDir, "target/classes");
            findComponentFilesRecursive(target, jsonFiles, componentFiles, new CamelComponentsFileFilter());
        }

        getLog().info("Found " + componentFiles.size() + " component.properties files");
        getLog().info("Found " + jsonFiles.size() + " component json files");

        // make sure to create out dir
        componentsOutDir.mkdirs();

        for (File file : jsonFiles) {
            File to = new File(componentsOutDir, file.getName());
            try {
                copyFile(file, to);
            } catch (IOException e) {
                throw new MojoFailureException("Cannot copy file from " + file + " -> " + to, e);
            }
        }

        File all = new File(componentsOutDir, "../components.properties");
        try {
            FileOutputStream fos = new FileOutputStream(all, false);

            String[] names = componentsOutDir.list();
            List<String> components = new ArrayList<>();
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
    }

    protected void executeDataFormats(Set<String> features) throws MojoExecutionException, MojoFailureException {
        getLog().info("Copying all Camel dataformat json descriptors");

        // lets use sorted set/maps
        Set<File> jsonFiles = new TreeSet<>();
        Set<File> dataFormatFiles = new TreeSet<>();

        // find all data formats from the components directory
        if (componentsDir != null && componentsDir.isDirectory()) {
            File[] dataFormats = componentsDir.listFiles();
            if (dataFormats != null) {
                for (File dir : dataFormats) {
                    if (dir.isDirectory() && !"target".equals(dir.getName())) {
                        // the directory must be in the list of known features
                        if (!features.contains(dir.getName())) {
                            continue;
                        }
                        File target = new File(dir, "target/classes");
                        findDataFormatFilesRecursive(target, jsonFiles, dataFormatFiles, new CamelDataFormatsFileFilter());
                    }
                }
            }
        }
        if (coreDir != null && coreDir.isDirectory()) {
            File target = new File(coreDir, "target/classes");
            findDataFormatFilesRecursive(target, jsonFiles, dataFormatFiles, new CamelDataFormatsFileFilter());
        }

        getLog().info("Found " + dataFormatFiles.size() + " dataformat.properties files");
        getLog().info("Found " + jsonFiles.size() + " dataformat json files");

        // make sure to create out dir
        dataFormatsOutDir.mkdirs();

        for (File file : jsonFiles) {
            File to = new File(dataFormatsOutDir, file.getName());
            try {
                copyFile(file, to);
            } catch (IOException e) {
                throw new MojoFailureException("Cannot copy file from " + file + " -> " + to, e);
            }
        }

        File all = new File(dataFormatsOutDir, "../dataformats.properties");
        try {
            FileOutputStream fos = new FileOutputStream(all, false);

            String[] names = dataFormatsOutDir.list();
            List<String> dataFormats = new ArrayList<>();
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
    }

    protected void executeLanguages(Set<String> features) throws MojoExecutionException, MojoFailureException {
        getLog().info("Copying all Camel language json descriptors");

        // lets use sorted set/maps
        Set<File> jsonFiles = new TreeSet<>();
        Set<File> languageFiles = new TreeSet<>();

        // find all languages from the components directory
        if (componentsDir != null && componentsDir.isDirectory()) {
            File[] languages = componentsDir.listFiles();
            if (languages != null) {
                for (File dir : languages) {
                    // the directory must be in the list of known features
                    if (!features.contains(dir.getName())) {
                        continue;
                    }
                    if (dir.isDirectory() && !"target".equals(dir.getName())) {
                        File target = new File(dir, "target/classes");
                        findLanguageFilesRecursive(target, jsonFiles, languageFiles, new CamelLanguagesFileFilter());
                    }
                }
            }
        }
        if (coreDir != null && coreDir.isDirectory()) {
            File target = new File(coreDir, "target/classes");
            findLanguageFilesRecursive(target, jsonFiles, languageFiles, new CamelLanguagesFileFilter());
        }

        getLog().info("Found " + languageFiles.size() + " language.properties files");
        getLog().info("Found " + jsonFiles.size() + " language json files");

        // make sure to create out dir
        languagesOutDir.mkdirs();

        for (File file : jsonFiles) {
            File to = new File(languagesOutDir, file.getName());
            try {
                copyFile(file, to);
            } catch (IOException e) {
                throw new MojoFailureException("Cannot copy file from " + file + " -> " + to, e);
            }
        }

        File all = new File(languagesOutDir, "../languages.properties");
        try {
            FileOutputStream fos = new FileOutputStream(all, false);

            String[] names = languagesOutDir.list();
            List<String> languages = new ArrayList<>();
            // sort the names
            for (String name : names) {
                if (name.endsWith(".json")) {
                    // strip out .json from the name
                    String languageName = name.substring(0, name.length() - 5);
                    languages.add(languageName);
                }
            }

            Collections.sort(languages);
            for (String name : languages) {
                fos.write(name.getBytes());
                fos.write("\n".getBytes());
            }

            fos.close();

        } catch (IOException e) {
            throw new MojoFailureException("Error writing to file " + all);
        }
    }

    protected void executeOthers(Set<String> features) throws MojoExecutionException, MojoFailureException {
        getLog().info("Copying all Camel other json descriptors");

        // lets use sorted set/maps
        Set<File> jsonFiles = new TreeSet<>();
        Set<File> otherFiles = new TreeSet<>();

        // find all languages from the components directory
        if (componentsDir != null && componentsDir.isDirectory()) {
            File[] others = componentsDir.listFiles();
            if (others != null) {
                for (File dir : others) {
                    // the directory must be in the list of known features
                    if (!features.contains(dir.getName())) {
                        continue;
                    }

                    // skip these special cases
                    // (camel-jetty is a placeholder, as camel-jetty9 is the actual component)
                    boolean special = "camel-core-osgi".equals(dir.getName())
                        || "camel-core-xml".equals(dir.getName())
                        || "camel-http-common".equals(dir.getName())
                        || "camel-jetty".equals(dir.getName())
                        || "camel-jetty-common".equals(dir.getName());
                    boolean special2 = "camel-as2".equals(dir.getName())
                        || "camel-box".equals(dir.getName())
                        || "camel-linkedin".equals(dir.getName())
                        || "camel-olingo2".equals(dir.getName())
                        || "camel-olingo4".equals(dir.getName())
                        || "camel-servicenow".equals(dir.getName())
                        || "camel-salesforce".equals(dir.getName());
                    if (special || special2) {
                        continue;
                    }

                    if (dir.isDirectory() && !"target".equals(dir.getName())) {
                        File target = new File(dir, "target/classes");
                        findOtherFilesRecursive(target, jsonFiles, otherFiles, new CamelOthersFileFilter());
                    }
                }
            }
        }
        getLog().info("Found " + otherFiles.size() + " other.properties files");
        getLog().info("Found " + jsonFiles.size() + " other json files");

        // make sure to create out dir
        othersOutDir.mkdirs();

        for (File file : jsonFiles) {
            File to = new File(othersOutDir, file.getName());
            try {
                copyFile(file, to);
            } catch (IOException e) {
                throw new MojoFailureException("Cannot copy file from " + file + " -> " + to, e);
            }
        }

        File all = new File(othersOutDir, "../others.properties");
        try {
            FileOutputStream fos = new FileOutputStream(all, false);

            String[] names = othersOutDir.list();
            List<String> others = new ArrayList<>();
            // sort the names
            for (String name : names) {
                if (name.endsWith(".json")) {
                    // strip out .json from the name
                    String otherName = name.substring(0, name.length() - 5);
                    others.add(otherName);
                }
            }

            Collections.sort(others);
            for (String name : others) {
                fos.write(name.getBytes());
                fos.write("\n".getBytes());
            }

            fos.close();

        } catch (IOException e) {
            throw new MojoFailureException("Error writing to file " + all);
        }
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

    private void findDataFormatFilesRecursive(File dir, Set<File> found, Set<File> dataFormats, FileFilter filter) {
        File[] files = dir.listFiles(filter);
        if (files != null) {
            for (File file : files) {
                // skip files in root dirs as Camel does not store information there but others may do
                boolean rootDir = "classes".equals(dir.getName()) || "META-INF".equals(dir.getName());
                boolean jsonFile = !rootDir && file.isFile() && file.getName().endsWith(".json");
                boolean dataFormatFile = !rootDir && file.isFile() && file.getName().equals("dataformat.properties");
                if (jsonFile) {
                    found.add(file);
                } else if (dataFormatFile) {
                    dataFormats.add(file);
                } else if (file.isDirectory()) {
                    findDataFormatFilesRecursive(file, found, dataFormats, filter);
                }
            }
        }
    }

    private void findLanguageFilesRecursive(File dir, Set<File> found, Set<File> languages, FileFilter filter) {
        File[] files = dir.listFiles(filter);
        if (files != null) {
            for (File file : files) {
                // skip files in root dirs as Camel does not store information there but others may do
                boolean rootDir = "classes".equals(dir.getName()) || "META-INF".equals(dir.getName());
                boolean jsonFile = !rootDir && file.isFile() && file.getName().endsWith(".json");
                boolean languageFile = !rootDir && file.isFile() && file.getName().equals("language.properties");
                if (jsonFile) {
                    found.add(file);
                } else if (languageFile) {
                    languages.add(file);
                } else if (file.isDirectory()) {
                    findLanguageFilesRecursive(file, found, languages, filter);
                }
            }
        }
    }

    private void findOtherFilesRecursive(File dir, Set<File> found, Set<File> others, FileFilter filter) {
        File[] files = dir.listFiles(filter);
        if (files != null) {
            for (File file : files) {
                // skip files in root dirs as Camel does not store information there but others may do
                boolean rootDir = "classes".equals(dir.getName()) || "META-INF".equals(dir.getName());
                boolean jsonFile = rootDir && file.isFile() && file.getName().endsWith(".json");
                boolean otherFile = !rootDir && file.isFile() && file.getName().equals("other.properties");
                if (jsonFile) {
                    found.add(file);
                } else if (otherFile) {
                    others.add(file);
                } else if (file.isDirectory()) {
                    findOtherFilesRecursive(file, found, others, filter);
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
            if (pathname.isFile() && pathname.getName().endsWith(".json")) {
                // must be a components json file
                try {
                    String json = loadText(new FileInputStream(pathname));
                    return json != null && json.contains("\"kind\": \"component\"");
                } catch (IOException e) {
                    // ignore
                }
            }
            return pathname.isDirectory() || (pathname.isFile() && pathname.getName().equals("component.properties"));
        }
    }

    private class CamelDataFormatsFileFilter implements FileFilter {

        @Override
        public boolean accept(File pathname) {
            if (pathname.isDirectory() && pathname.getName().equals("model")) {
                // do not check the camel-core model packages as there is no components there
                return false;
            }
            if (pathname.isFile() && pathname.getName().endsWith(".json")) {
                // must be a dataformat json file
                try {
                    String json = loadText(new FileInputStream(pathname));
                    return json != null && json.contains("\"kind\": \"dataformat\"");
                } catch (IOException e) {
                    // ignore
                }
            }
            return pathname.isDirectory() || (pathname.isFile() && pathname.getName().equals("dataformat.properties"));
        }
    }

    private class CamelLanguagesFileFilter implements FileFilter {

        @Override
        public boolean accept(File pathname) {
            if (pathname.isDirectory() && pathname.getName().equals("model")) {
                // do not check the camel-core model packages as there is no components there
                return false;
            }
            if (pathname.isFile() && pathname.getName().endsWith(".json")) {
                // must be a language json file
                try {
                    String json = loadText(new FileInputStream(pathname));
                    return json != null && json.contains("\"kind\": \"language\"");
                } catch (IOException e) {
                    // ignore
                }
            }
            return pathname.isDirectory() || (pathname.isFile() && pathname.getName().equals("language.properties"));
        }
    }

    private class CamelOthersFileFilter implements FileFilter {

        @Override
        public boolean accept(File pathname) {
            if (pathname.isFile() && pathname.getName().endsWith(".json")) {
                // must be a language json file
                try {
                    String json = loadText(new FileInputStream(pathname));
                    return json != null && json.contains("\"kind\": \"other\"");
                } catch (IOException e) {
                    // ignore
                }
            }
            return pathname.isDirectory() || (pathname.isFile() && pathname.getName().equals("other.properties"));
        }
    }

    public static void copyFile(File from, File to) throws IOException {
        FileChannel in = null;
        FileChannel out = null;
        try (FileInputStream fis = new FileInputStream(from); FileOutputStream fos = new FileOutputStream(to)) {
            try {
                in = fis.getChannel();
                out = fos.getChannel();

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
    }

    private Set<String> findKarafFeatures() throws MojoExecutionException, MojoFailureException {
        // load features.xml file and parse it

        Set<String> answer = new LinkedHashSet<>();
        try {
            InputStream is = new FileInputStream(new File(featuresDir, "features.xml"));

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setIgnoringComments(true);
            dbf.setIgnoringElementContentWhitespace(true);
            dbf.setNamespaceAware(false);
            dbf.setValidating(false);
            dbf.setXIncludeAware(false);
            Document dom = dbf.newDocumentBuilder().parse(is);

            NodeList children = dom.getElementsByTagName("features");
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == ELEMENT_NODE) {
                    NodeList children2 = child.getChildNodes();
                    for (int j = 0; j < children2.getLength(); j++) {
                        Node child2 = children2.item(j);
                        if ("feature".equals(child2.getNodeName())) {
                            String artifactId = child2.getAttributes().getNamedItem("name").getTextContent();
                            if (artifactId != null && artifactId.startsWith("camel-")) {
                                answer.add(artifactId);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading features.xml file", e);
        }

        return answer;
    }

}
