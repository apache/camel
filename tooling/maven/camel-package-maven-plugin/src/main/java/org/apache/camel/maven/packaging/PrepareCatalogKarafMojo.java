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
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.tooling.util.FileUtil;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

/**
 * Prepares the Karaf provider camel catalog to include component it supports
 */
@Mojo(name = "prepare-catalog-karaf", threadSafe = true)
public class PrepareCatalogKarafMojo extends AbstractMojo {

    /**
     * The maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    /**
     * The output directory for components catalog
     */
    @Parameter(defaultValue = "${project.build.directory}/classes/org/apache/camel/catalog/karaf/components")
    protected File componentsOutDir;

    /**
     * The output directory for dataformats catalog
     */
    @Parameter(defaultValue = "${project.build.directory}/classes/org/apache/camel/catalog/karaf/dataformats")
    protected File dataFormatsOutDir;

    /**
     * The output directory for languages catalog
     */
    @Parameter(defaultValue = "${project.build.directory}/classes/org/apache/camel/catalog/karaf/languages")
    protected File languagesOutDir;

    /**
     * The output directory for others catalog
     */
    @Parameter(defaultValue = "${project.build.directory}/classes/org/apache/camel/catalog/karaf/others")
    protected File othersOutDir;

    /**
     * The karaf features directory
     */
    @Parameter(defaultValue = "${project.build.directory}/../../../platforms/karaf/features/src/main/resources/")
    protected File featuresDir;

    /**
     * The components directory where all the Apache Camel components are
     */
    @Parameter(defaultValue = "${project.build.directory}/../../../components")
    protected File componentsDir;

    /**
     * The camel-core directory
     */
    @Parameter(defaultValue = "${project.build.directory}/../../../core/camel-core-engine")
    protected File coreDir;

    /**
     * The camel-base directory
     */
    @Parameter(defaultValue = "${project.build.directory}/../../../core/camel-base")
    protected File baseDir;

    /**
     * Maven ProjectHelper.
     */
    @Component
    private MavenProjectHelper projectHelper;

    /**
     * Execute goal.
     *
     * @throws MojoExecutionException execution of the main class or one of the
     *             threads it generated failed.
     * @throws MojoFailureException something bad happened...
     */
    @Override
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
                        } else if ("camel-servicenow".equals(dir.getName())) {
                            target = new File(dir, "camel-servicenow-component/target/classes");
                        } else {
                            // this module must be active with a source folder
                            File src = new File(dir, "src");
                            boolean active = src.isDirectory() && src.exists();
                            if (!active) {
                                continue;
                            }
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

        // copy json files
        Path outDir = componentsOutDir.toPath();
        copyFiles(outDir, jsonFiles);
        generateJsonList(outDir, "../components.properties");
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

                        // this module must be active with a source folder
                        File src = new File(dir, "src");
                        boolean active = src.isDirectory() && src.exists();
                        if (!active) {
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

        // copy json files
        Path outDir = dataFormatsOutDir.toPath();
        copyFiles(outDir, jsonFiles);
        generateJsonList(outDir, "../dataformats.properties");
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
                    // the directory must be in the list of known features (or
                    // known languages)
                    if (!features.contains(dir.getName()) && !dir.getName().equals("camel-bean") && !dir.getName().equals("camel-xpath")) {
                        continue;
                    }

                    // this module must be active with a source folder
                    File src = new File(dir, "src");
                    boolean active = src.isDirectory() && src.exists();
                    if (!active) {
                        continue;
                    }

                    if (dir.isDirectory() && !"target".equals(dir.getName())) {
                        File target = new File(dir, "target/classes");
                        findLanguageFilesRecursive(target, jsonFiles, languageFiles, new CamelLanguagesFileFilter());
                    }
                }
            }
        }
        if (baseDir != null && baseDir.isDirectory()) {
            File target = new File(baseDir, "target/classes");
            findLanguageFilesRecursive(target, jsonFiles, languageFiles, new CamelLanguagesFileFilter());
            // also look in camel-xml-jaxp
            target = new File(baseDir, "../camel-xml-jaxp/target/classes");
            findLanguageFilesRecursive(target, jsonFiles, languageFiles, new CamelLanguagesFileFilter());
        }

        getLog().info("Found " + languageFiles.size() + " language.properties files");
        getLog().info("Found " + jsonFiles.size() + " language json files");

        // copy json files
        Path outDir = languagesOutDir.toPath();
        copyFiles(outDir, jsonFiles);
        generateJsonList(outDir, "../languages.properties");
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
                    boolean special = "camel-core-osgi".equals(dir.getName()) || "camel-core-xml".equals(dir.getName()) || "camel-http-base".equals(dir.getName())
                                      || "camel-http-common".equals(dir.getName()) || "camel-jetty-common".equals(dir.getName());
                    boolean special2 = "camel-as2".equals(dir.getName()) || "camel-box".equals(dir.getName()) || "camel-olingo2".equals(dir.getName())
                                       || "camel-olingo4".equals(dir.getName()) || "camel-servicenow".equals(dir.getName()) || "camel-salesforce".equals(dir.getName());
                    boolean special3 = "camel-debezium-common".equals(dir.getName());
                    if (special || special2 || special3) {
                        continue;
                    }

                    // this module must be active with a source folder
                    File src = new File(dir, "src");
                    boolean active = src.isDirectory() && src.exists();
                    if (!active) {
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

        // copy json files
        Path outDir = othersOutDir.toPath();
        copyFiles(outDir, jsonFiles);
        generateJsonList(outDir, "../others.properties");
    }

    private void findComponentFilesRecursive(File dir, Set<File> found, Set<File> components, FileFilter filter) {
        File[] files = dir.listFiles(filter);
        if (files != null) {
            for (File file : files) {
                // skip files in root dirs as Camel does not store information
                // there but others may do
                boolean rootDir = "classes".equals(dir.getName()) || "META-INF".equals(dir.getName());
                boolean jsonFile = !rootDir && file.isFile() && file.getName().endsWith(PackageHelper.JSON_SUFIX);
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
                // skip files in root dirs as Camel does not store information
                // there but others may do
                boolean rootDir = "classes".equals(dir.getName()) || "META-INF".equals(dir.getName());
                boolean jsonFile = !rootDir && file.isFile() && file.getName().endsWith(PackageHelper.JSON_SUFIX);
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
                // skip files in root dirs as Camel does not store information
                // there but others may do
                boolean rootDir = "classes".equals(dir.getName()) || "META-INF".equals(dir.getName());
                boolean jsonFile = !rootDir && file.isFile() && file.getName().endsWith(PackageHelper.JSON_SUFIX);
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
                // skip files in root dirs as Camel does not store information
                // there but others may do
                boolean rootDir = "classes".equals(dir.getName()) || "META-INF".equals(dir.getName());
                boolean jsonFile = rootDir && file.isFile() && file.getName().endsWith(PackageHelper.JSON_SUFIX);
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

    private static class CamelComponentsFileFilter implements FileFilter {

        @Override
        public boolean accept(File pathname) {
            if (pathname.isDirectory() && pathname.getName().equals("model")) {
                // do not check the camel-core model packages as there is no
                // components there
                return false;
            }
            if (pathname.isFile() && pathname.getName().endsWith(PackageHelper.JSON_SUFIX)) {
                // must be a components json file
                try {
                    String json = PackageHelper.loadText(pathname);
                    return "component".equals(PackageHelper.getSchemaKind(json));
                } catch (IOException e) {
                    // ignore
                }
            }
            return pathname.isDirectory() || (pathname.isFile() && pathname.getName().equals("component.properties"));
        }
    }

    private static class CamelDataFormatsFileFilter implements FileFilter {

        @Override
        public boolean accept(File pathname) {
            if (pathname.isDirectory() && pathname.getName().equals("model")) {
                // do not check the camel-core model packages as there is no
                // components there
                return false;
            }
            if (pathname.isFile() && pathname.getName().endsWith(PackageHelper.JSON_SUFIX)) {
                // must be a dataformat json file
                try {
                    String json = PackageHelper.loadText(pathname);
                    return "dataformat".equals(PackageHelper.getSchemaKind(json));
                } catch (IOException e) {
                    // ignore
                }
            }
            return pathname.isDirectory() || (pathname.isFile() && pathname.getName().equals("dataformat.properties"));
        }
    }

    private static class CamelLanguagesFileFilter implements FileFilter {

        @Override
        public boolean accept(File pathname) {
            if (pathname.isDirectory() && pathname.getName().equals("model")) {
                // do not check the camel-core model packages as there is no
                // components there
                return false;
            }
            if (pathname.isFile() && pathname.getName().endsWith(PackageHelper.JSON_SUFIX)) {
                // must be a language json file
                try {
                    String json = PackageHelper.loadText(pathname);
                    return "language".equals(PackageHelper.getSchemaKind(json));
                } catch (IOException e) {
                    // ignore
                }
            }
            return pathname.isDirectory() || (pathname.isFile() && pathname.getName().equals("language.properties"));
        }
    }

    private static class CamelOthersFileFilter implements FileFilter {

        @Override
        public boolean accept(File pathname) {
            if (pathname.isFile() && pathname.getName().endsWith(PackageHelper.JSON_SUFIX)) {
                // must be a language json file
                try {
                    String json = PackageHelper.loadText(pathname);
                    return "other".equals(PackageHelper.getSchemaKind(json));
                } catch (IOException e) {
                    // ignore
                }
            }
            return pathname.isDirectory() || (pathname.isFile() && pathname.getName().equals("other.properties"));
        }
    }

    public static void copyFiles(Path outDir, Collection<File> files) throws MojoFailureException {
        for (File file : files) {
            Path to = outDir.resolve(file.getName());
            try {
                FileUtil.updateFile(file.toPath(), to);
            } catch (IOException e) {
                throw new MojoFailureException("Cannot copy file from " + file + " -> " + to, e);
            }
        }
    }

    public static Set<String> generateJsonList(Path outDir, String outFile) throws MojoFailureException {
        Path all = outDir.resolve(outFile);
        try {
            Set<String> answer = Files.list(outDir).filter(p -> p.getFileName().toString().endsWith(PackageHelper.JSON_SUFIX)).map(p -> p.getFileName().toString())
                // strip out .json from the name
                .map(n -> n.substring(0, n.length() - PackageHelper.JSON_SUFIX.length())).sorted().collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
            String data = String.join("\n", answer) + "\n";
            FileUtil.updateFile(all, data);
            return answer;
        } catch (IOException e) {
            throw new MojoFailureException("Error writing to file " + all);
        }
    }

    public static void copyFile(File from, File to) throws IOException {
        FileUtil.updateFile(from.toPath(), to.toPath());
    }

    private Set<String> findKarafFeatures() throws MojoExecutionException, MojoFailureException {
        // load features.xml file and parse it

        Set<String> answer = new LinkedHashSet<>();
        File file = new File(featuresDir, "features.xml");
        try (InputStream is = new FileInputStream(file)) {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setIgnoringComments(true);
            dbf.setIgnoringElementContentWhitespace(true);
            dbf.setNamespaceAware(false);
            dbf.setValidating(false);
            dbf.setXIncludeAware(false);
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
            Document dom = dbf.newDocumentBuilder().parse(is);

            NodeList children = dom.getElementsByTagName("features");
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
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

            getLog().info("Found " + answer.size() + " Camel features in file: " + file);

        } catch (Exception e) {
            throw new MojoExecutionException("Error reading features.xml file", e);
        }

        return answer;
    }

}
