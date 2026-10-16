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
import java.io.FileInputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import javax.inject.Inject;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.tooling.model.ArtifactModel;
import org.apache.camel.tooling.model.BaseModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.util.FileUtil;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.build.BuildContext;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;

import static org.apache.camel.tooling.util.PackageHelper.loadText;

/**
 * Prepares camel-kamelet-main
 */
@Mojo(name = "prepare-kamelet-main", threadSafe = true)
public class PrepareKameletMainMojo extends AbstractMojo {

    /**
     * The maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    /**
     * build context to check changed files and mark them for refresh (used for m2e compatibility)
     */
    protected final BuildContext buildContext;

    /**
     * The camel-catalog directory
     */
    @Parameter(defaultValue = "${project.directory}/../../../catalog/camel-catalog")
    protected File catalogDir;

    @Parameter(defaultValue = "${project.directory}/../../../components")
    protected File componentsDir;

    @Parameter(defaultValue = "src/generated/")
    protected File genDir;

    /**
     * The third-party libraries camel run downloads on demand, mapped by package (CAMEL-24809): the input of
     * camel-thirdparty-known-dependencies.properties.
     */
    @Parameter(defaultValue = "src/main/known-third-party-libraries.properties")
    protected File thirdPartyLibraries;

    /**
     * Resolve every third-party jar and check that the mapped package is in it. Downloads the jars, so it is off by
     * default and meant for CI: -Dcamel.known-dependencies.verify=true
     */
    @Parameter(defaultValue = "false", property = "camel.known-dependencies.verify")
    protected boolean verifyThirdPartyJars;

    private final RepositorySystem repoSystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
    private RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    private List<RemoteRepository> repositories;

    private static final Pattern PROPERTY_VERSION = Pattern.compile("^\\$\\{([^}]+)\\}$");
    private static final Pattern BOM_VERSION = Pattern.compile("^@bom\\(([^:]+):([^:]+):\\$\\{([^}]+)\\}\\)$");

    private final Map<Path, BaseModel<?>> allModels = new HashMap<>();
    private String licenseHeader;

    @Inject
    public PrepareKameletMainMojo(BuildContext buildContext, RepositorySystem repoSystem) {
        this.buildContext = buildContext;
        this.repoSystem = repoSystem;
    }

    /**
     * Execute goal.
     *
     * @throws MojoExecutionException execution of the main class or one of the threads it generated failed.
     * @throws MojoFailureException   something bad happened...
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            updateKnownDependencies();
        } catch (Exception e) {
            throw new MojoFailureException("Error updating camel-component-known-dependencies.properties", e);
        }

        try {
            updateKnownFactoryFinders();
        } catch (Exception e) {
            throw new MojoFailureException("Error updating camel-factoryfinder-known-dependencies.properties", e);
        }
        try {
            updateKnownThirdPartyDependencies();
        } catch (MojoFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoFailureException("Error updating camel-thirdparty-known-dependencies.properties", e);
        }
    }

    /**
     * Generates camel-thirdparty-known-dependencies.properties from the curated list of third-party libraries
     * (CAMEL-24809). Each input line maps a package to groupId:artifactId:version, where the version is a ${property}
     * of the project (inherited from camel-parent) or @bom(groupId:artifactId:${property}) for a library whose version
     * a BOM manages. The version is resolved here, so the runtime needs no lookup, and a property that does not exist
     * or a literal version fails the build. With verifyThirdPartyJars every jar is resolved and the mapped package must
     * be found in it.
     */
    protected void updateKnownThirdPartyDependencies() throws Exception {
        File input = thirdPartyLibraries.isAbsolute()
                ? thirdPartyLibraries : new File(project.getBasedir(), thirdPartyLibraries.getPath());
        if (!input.exists()) {
            getLog().info("No " + input + ": camel-thirdparty-known-dependencies.properties not generated");
            return;
        }
        Properties in = new Properties();
        try (InputStream is = new FileInputStream(input)) {
            in.load(is);
        }
        Map<String, String> boms = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        Map<String, String> resolved = new TreeMap<>();
        for (String pkg : in.stringPropertyNames()) {
            String gav = in.getProperty(pkg).trim();
            int i = gav.indexOf(':');
            int j = gav.indexOf(':', i + 1);
            if (i < 0 || j < 0) {
                problems.add(pkg + " = " + gav + ": expected groupId:artifactId:version");
                continue;
            }
            String groupId = gav.substring(0, i);
            String artifactId = gav.substring(i + 1, j);
            String version = gav.substring(j + 1);
            Matcher pm = PROPERTY_VERSION.matcher(version);
            Matcher bm = BOM_VERSION.matcher(version);
            if (pm.matches()) {
                String value = project.getProperties().getProperty(pm.group(1));
                if (value == null) {
                    problems.add(pkg + ": no property " + pm.group(1) + " in parent/pom.xml");
                    continue;
                }
                version = value;
            } else if (bm.matches()) {
                String bomVersion = project.getProperties().getProperty(bm.group(3));
                if (bomVersion == null) {
                    problems.add(pkg + ": no property " + bm.group(3) + " in parent/pom.xml");
                    continue;
                }
                String bomKey = bm.group(1) + ":" + bm.group(2) + ":" + bomVersion;
                version = managedVersion(boms, bomKey, groupId, artifactId);
                if (version == null) {
                    problems.add(pkg + ": " + groupId + ":" + artifactId + " is not managed by " + bomKey);
                    continue;
                }
            } else {
                problems.add(pkg + " = " + gav + ": a literal version; add a <" + artifactId.toLowerCase(Locale.ROOT)
                             + "-version> property to parent/pom.xml and use ${...}");
                continue;
            }
            resolved.put(pkg, groupId + ":" + artifactId + ":" + version);
        }
        if (!problems.isEmpty()) {
            throw new MojoFailureException("Problems in " + input + ":\n  " + String.join("\n  ", problems));
        }
        if (verifyThirdPartyJars) {
            verifyPackagesInJars(resolved);
        }
        List<String> lines = new ArrayList<>();
        lines.add("# Generated by camel-package-maven-plugin:prepare-kamelet-main from src/main/"
                  + input.getName() + " (CAMEL-24809). Do not edit.");
        lines.add("# Third-party libraries camel run downloads on demand, mapped by package; the resolver matches the class"
                  + " and then each enclosing package.");
        for (Map.Entry<String, String> e : resolved.entrySet()) {
            lines.add(e.getKey() + " = " + e.getValue());
        }
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("license-header.txt")) {
            this.licenseHeader = loadText(is);
        } catch (Exception e) {
            throw new MojoFailureException("Error loading license-header.txt file", e);
        }
        // into target/classes, not src/generated: the file is the input with the versions resolved, so keeping both
        // in the repository would duplicate 146 lines; it is regenerated on every build and shipped in the jar
        Path out = Path.of(project.getBuild().getOutputDirectory(), "camel-thirdparty-known-dependencies.properties");
        Files.createDirectories(out.getParent());
        updateResource(buildContext, out, licenseHeader + "\n" + String.join("\n", lines) + "\n");
        getLog().info("Generated " + out.getFileName() + " with " + resolved.size() + " libraries");
    }

    private String managedVersion(Map<String, String> cache, String bomKey, String groupId, String artifactId)
            throws Exception {
        String key = bomKey + "->" + groupId + ":" + artifactId;
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        String[] parts = bomKey.split(":");
        ArtifactRequest req = new ArtifactRequest().setRepositories(repositories)
                .setArtifact(new DefaultArtifact(parts[0], parts[1], "pom", parts[2]));
        File pom = repoSystem.resolveArtifact(repoSession, req).getArtifact().getFile();
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document doc = dbf.newDocumentBuilder().parse(pom);
        String found = null;
        NodeList deps = doc.getElementsByTagName("dependency");
        for (int n = 0; n < deps.getLength() && found == null; n++) {
            Element d = (Element) deps.item(n);
            if (groupId.equals(text(d, "groupId")) && artifactId.equals(text(d, "artifactId"))) {
                found = text(d, "version");
            }
        }
        if (found != null && found.startsWith("${") && found.endsWith("}")) {
            // a BOM that versions its artifacts through its own properties
            String prop = found.substring(2, found.length() - 1);
            String value = "project.version".equals(prop) ? parts[2] : null;
            NodeList props = doc.getElementsByTagName("properties");
            for (int n = 0; n < props.getLength() && value == null; n++) {
                value = text((Element) props.item(n), prop);
            }
            found = value;
        }
        cache.put(key, found);
        return found;
    }

    private static String text(Element parent, String child) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE && child.equals(n.getNodeName())) {
                return n.getTextContent().trim();
            }
        }
        return null;
    }

    private void verifyPackagesInJars(Map<String, String> resolved) throws Exception {
        List<String> problems = new ArrayList<>();
        Map<String, File> jars = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : resolved.entrySet()) {
            String gav = e.getValue();
            File jar = jars.get(gav);
            if (jar == null) {
                String[] parts = gav.split(":");
                ArtifactRequest req = new ArtifactRequest().setRepositories(repositories)
                        .setArtifact(new DefaultArtifact(parts[0], parts[1], "jar", parts[2]));
                try {
                    jar = repoSystem.resolveArtifact(repoSession, req).getArtifact().getFile();
                } catch (Exception ex) {
                    problems.add(e.getKey() + ": cannot resolve " + gav + ": " + ex.getMessage());
                    continue;
                }
                jars.put(gav, jar);
            }
            String dir = e.getKey().replace('.', '/') + "/";
            boolean found;
            try (ZipFile zip = new ZipFile(jar)) {
                found = zip.stream().anyMatch(z -> z.getName().startsWith(dir) && z.getName().endsWith(".class"));
            }
            if (!found) {
                problems.add(e.getKey() + ": no classes under " + dir + " in " + gav);
            }
        }
        if (!problems.isEmpty()) {
            throw new MojoFailureException(
                    "Third-party known dependencies do not match their jars:\n  "
                                           + String.join("\n  ", problems));
        }
        getLog().info("Verified " + resolved.size() + " third-party packages against " + jars.size() + " jars");
    }

    protected void updateKnownDependencies() throws Exception {
        Collection<Path> allJsonFiles = new TreeSet<>();

        File path = new File(catalogDir, "src/generated/resources/org/apache/camel/catalog/components");
        for (File p : path.listFiles()) {
            String f = p.getName();
            if (f.endsWith(PackageHelper.JSON_SUFIX)) {
                allJsonFiles.add(p.toPath());
            }
        }

        for (Path p : allJsonFiles) {
            var m = JsonMapper.generateModel(p);
            if (m != null) {
                allModels.put(p, m);
            }
        }

        List<String> lines = new ArrayList<>();
        for (BaseModel<?> model : allModels.values()) {
            String fqn = model.getJavaType();
            if (model instanceof ArtifactModel) {
                String aid = ((ArtifactModel<?>) model).getArtifactId();
                if (aid.startsWith("camel-")) {
                    aid = aid.substring(6);
                }
                String line = fqn + "=camel:" + aid;
                lines.add(line);
            }
        }
        // remove duplicate
        lines = lines.stream().distinct().collect(Collectors.toList());
        // and sort
        Collections.sort(lines);

        // load license header
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("license-header.txt")) {
            this.licenseHeader = loadText(is);
        } catch (Exception e) {
            throw new MojoFailureException("Error loading license-header.txt file", e);
        }

        String source = String.join("\n", lines) + "\n";
        writeSourceIfChanged(source, "resources", "camel-component-known-dependencies.properties", genDir);
    }

    protected void updateKnownFactoryFinders() throws Exception {
        List<String> lines = findFactoryFinder(componentsDir);

        // remove duplicate
        lines = lines.stream().distinct().collect(Collectors.toList());
        // and sort
        Collections.sort(lines);

        getLog().info("Found " + lines.size() + " FactoryFinder @JdkService");

        // load license header
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("license-header.txt")) {
            this.licenseHeader = loadText(is);
        } catch (Exception e) {
            throw new MojoFailureException("Error loading license-header.txt file", e);
        }

        String source = String.join("\n", lines) + "\n";
        writeSourceIfChanged(source, "resources", "camel-factoryfinder-known-dependencies.properties", genDir);
    }

    protected boolean writeSourceIfChanged(String source, String filePath, String fileName, File outputDir)
            throws MojoFailureException {
        Path target = outputDir.toPath().resolve(filePath).resolve(fileName);

        try {
            final String code = licenseHeader + "\n" + source;

            if (getLog().isDebugEnabled()) {
                getLog().debug("Source code generated:\n" + code);
            }

            return updateResource(buildContext, target, code);
        } catch (Exception e) {
            throw new MojoFailureException("IOError with file " + target, e);
        }
    }

    public static boolean updateResource(BuildContext buildContext, Path out, String data) {
        try {
            if (FileUtil.updateFile(out, data)) {
                refresh(buildContext, out);
                return true;
            }
        } catch (IOException e) {
            throw new IOError(e);
        }
        return false;
    }

    public static void refresh(BuildContext buildContext, Path file) {
        if (buildContext != null) {
            buildContext.refresh(file.toFile());
        }
    }

    private static List<String> findFactoryFinder(File rootDir) {
        List<String> answer = new ArrayList<>();
        String serviceDir = "src/generated/resources/META-INF/services/org/apache/camel/";

        for (File f : Objects.requireNonNull(rootDir.listFiles())) {
            if (f.isDirectory()) {
                File fd = new File(f, serviceDir);
                if (fd.isDirectory()) {
                    String artifact = f.getName();
                    if (artifact.startsWith("camel-")) {
                        artifact = artifact.substring(6);
                    }
                    findFactoryFinder(answer, artifact, fd, null);
                }
                // scan nested module directories (e.g., camel-infinispan/camel-infinispan/)
                File[] children = f.listFiles();
                if (children != null) {
                    for (File nested : children) {
                        if (nested.isDirectory() && nested.getName().startsWith("camel-")) {
                            fd = new File(nested, serviceDir);
                            if (fd.isDirectory()) {
                                String nestedArtifact = nested.getName();
                                if (nestedArtifact.startsWith("camel-")) {
                                    nestedArtifact = nestedArtifact.substring(6);
                                }
                                findFactoryFinder(answer, nestedArtifact, fd, null);
                            }
                        }
                    }
                }
            }
        }

        return answer;
    }

    private static void findFactoryFinder(List<String> answer, String artifact, File dir, String subdir) {
        for (File sf : Objects.requireNonNull(dir.listFiles())) {
            if (sf.isFile() && !sf.getName().contains(".") && acceptFactoryFinderFile(sf.getName())) {
                // service marker file
                String path = subdir != null ? subdir + "/" + sf.getName() : sf.getName();
                answer.add("META-INF/services/org/apache/camel/" + path + "=camel:" + artifact);
            } else if (sf.isDirectory() && acceptFactoryFinderSubDir(sf.getName())) {
                findFactoryFinder(answer, artifact, sf, sf.getName());
            }
        }
    }

    private static boolean acceptFactoryFinderSubDir(String name) {
        return "cloud".equals(name) || "platform-http".equals(name) || "periodic-task".equals(name)
                || "resource-resolver".equals(name);
    }

    private static boolean acceptFactoryFinderFile(String name) {
        return !"TypeConverterLoader".equals(name) && !"reactive-executor".equals(name) && !"thread-pool-factory".equals(name);
    }

}
