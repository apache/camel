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
package org.apache.camel.maven.bom.generator;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;


import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

/**
 * Generate BOM by flattening the current project's dependency management section and applying exclusions.
 *
 * @goal generate
 * @phase validate
 */
public class BomGeneratorMojo extends AbstractMojo {

    /**
     * The maven project.
     *
     * @parameter property="project"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * The source pom template file.
     *
     * @parameter default-value="${basedir}/pom.xml"
     */
    protected File sourcePom;

    /**
     * The pom file.
     *
     * @parameter default-value="${project.build.directory}/${project.name}-pom.xml"
     */
    protected File targetPom;


    /**
     * The user configuration
     *
     * @parameter
     * @readonly
     */
    protected DependencySet dependencies;

    /**
     * The conflict checks configured by the user
     *
     * @parameter
     * @readonly
     */
    protected ExternalBomConflictCheckSet checkConflicts;

    /**
     * Used to look up Artifacts in the remote repository.
     *
     * @component role="org.apache.maven.artifact.factory.ArtifactFactory"
     * @required
     * @readonly
     */
    protected ArtifactFactory artifactFactory;

    /**
     * Used to look up Artifacts in the remote repository.
     *
     * @component role="org.apache.maven.artifact.resolver.ArtifactResolver"
     * @required
     * @readonly
     */
    protected ArtifactResolver artifactResolver;

    /**
     * List of Remote Repositories used by the resolver
     *
     * @parameter property="project.remoteArtifactRepositories"
     * @readonly
     * @required
     */
    protected List remoteRepositories;

    /**
     * Location of the local repository.
     *
     * @parameter property="localRepository"
     * @readonly
     * @required
     */
    protected ArtifactRepository localRepository;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            DependencyManagement mng = project.getDependencyManagement();

            List<Dependency> filteredDependencies = enhance(filter(mng.getDependencies()));

            Set<String> externallyManagedDependencies = getExternallyManagedDependencies();
            checkConflictsWithExternalBoms(filteredDependencies, externallyManagedDependencies);

            Document pom = loadBasePom();

            // transform
            overwriteDependencyManagement(pom, filteredDependencies);

            writePom(pom);

        } catch (MojoFailureException ex) {
            throw ex;
        } catch (MojoExecutionException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MojoExecutionException("Cannot generate the output BOM file", ex);
        }
    }

    private List<Dependency> enhance(List<Dependency> dependencyList) {

        for (Dependency dep : dependencyList) {
            if (dep.getGroupId().startsWith(project.getGroupId()) && project.getVersion().equals(dep.getVersion())) {
                dep.setVersion("${project.version}");
            }
        }

        return dependencyList;
    }

    private List<Dependency> filter(List<Dependency> dependencyList) {
        List<Dependency> outDependencies = new ArrayList<>();

        DependencyMatcher inclusions = new DependencyMatcher(dependencies.getIncludes());
        DependencyMatcher exclusions = new DependencyMatcher(dependencies.getExcludes());

        for (Dependency dep : dependencyList) {
            boolean accept = inclusions.matches(dep) && !exclusions.matches(dep);
            getLog().debug(dep + (accept ? " included in the BOM" : " excluded from BOM"));

            if (accept) {
                outDependencies.add(dep);
            }
        }

        Collections.sort(outDependencies, (d1, d2) -> (d1.getGroupId() + ":" + d1.getArtifactId()).compareTo(d2.getGroupId() + ":" + d2.getArtifactId()));

        return outDependencies;
    }

    private Document loadBasePom() throws Exception {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document pom = builder.parse(sourcePom);

        XPath xpath = XPathFactory.newInstance().newXPath();

        XPathExpression parentVersion = xpath.compile("/project/parent/version");
        setActualVersion(pom, parentVersion);

        XPathExpression projectVersion = xpath.compile("/project/version");
        setActualVersion(pom, projectVersion);

        return pom;
    }

    private void setActualVersion(Document pom, XPathExpression path) throws XPathExpressionException {
        Node node = (Node) path.evaluate(pom, XPathConstants.NODE);
        if (node != null && node.getTextContent() != null && node.getTextContent().trim().equals("${project.version}")) {
            node.setTextContent(project.getVersion());
        }
    }

    private void writePom(Document pom) throws Exception {
        XPathExpression xpath = XPathFactory.newInstance().newXPath().compile("//text()[normalize-space(.) = '']");
        NodeList emptyNodes = (NodeList) xpath.evaluate(pom, XPathConstants.NODESET);

        // Remove empty text nodes
        for (int i = 0; i < emptyNodes.getLength(); i++) {
            Node emptyNode = emptyNodes.item(i);
            emptyNode.getParentNode().removeChild(emptyNode);
        }

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        DOMSource source = new DOMSource(pom);

        targetPom.getParentFile().mkdirs();

        String content;
        try (StringWriter out = new StringWriter()) {
            StreamResult result = new StreamResult(out);
            transformer.transform(source, result);
            content = out.toString();
        }

        // Fix header formatting problem
        content = content.replaceFirst("-->", "-->\n");
        writeFileIfChanged(content, targetPom);
    }

    private void writeFileIfChanged(String content, File file) throws IOException {
        boolean write = true;

        if (file.exists()) {
            try (FileReader fr = new FileReader(file)) {
                String oldContent = IOUtils.toString(fr);
                if (!content.equals(oldContent)) {
                    getLog().debug("Writing new file " + file.getAbsolutePath());
                    fr.close();
                } else {
                    getLog().debug("File " + file.getAbsolutePath() + " left unchanged");
                    write = false;
                }
            }
        } else {
            File parent = file.getParentFile();
            parent.mkdirs();
        }

        if (write) {
            try (FileWriter fw = new FileWriter(file)) {
                IOUtils.write(content, fw);
            }
        }
    }


    private void overwriteDependencyManagement(Document pom, List<Dependency> dependencies) throws Exception {

        XPath xpath = XPathFactory.newInstance().newXPath();
        XPathExpression expr = xpath.compile("/project/dependencyManagement/dependencies");

        NodeList nodes = (NodeList) expr.evaluate(pom, XPathConstants.NODESET);
        if (nodes.getLength() == 0) {
            throw new IllegalStateException("No dependencies found in the dependencyManagement section of the current pom");
        }

        Node dependenciesSection = nodes.item(0);
        // cleanup the dependency management section
        while (dependenciesSection.hasChildNodes()) {
            Node child = dependenciesSection.getFirstChild();
            dependenciesSection.removeChild(child);
        }

        for (Dependency dep : dependencies) {
            Element dependencyEl = pom.createElement("dependency");

            Element groupIdEl = pom.createElement("groupId");
            groupIdEl.setTextContent(dep.getGroupId());
            dependencyEl.appendChild(groupIdEl);

            Element artifactIdEl = pom.createElement("artifactId");
            artifactIdEl.setTextContent(dep.getArtifactId());
            dependencyEl.appendChild(artifactIdEl);

            Element versionEl = pom.createElement("version");
            versionEl.setTextContent(dep.getVersion());
            dependencyEl.appendChild(versionEl);

            if (!"jar".equals(dep.getType())) {
                Element typeEl = pom.createElement("type");
                typeEl.setTextContent(dep.getType());
                dependencyEl.appendChild(typeEl);
            }

            if (dep.getClassifier() != null) {
                Element classifierEl = pom.createElement("classifier");
                classifierEl.setTextContent(dep.getClassifier());
                dependencyEl.appendChild(classifierEl);
            }

            if (dep.getScope() != null && !"compile".equals(dep.getScope())) {
                Element scopeEl = pom.createElement("scope");
                scopeEl.setTextContent(dep.getScope());
                dependencyEl.appendChild(scopeEl);
            }

            if (dep.getExclusions() != null && !dep.getExclusions().isEmpty()) {

                Element exclsEl = pom.createElement("exclusions");

                for (Exclusion e : dep.getExclusions()) {
                    Element exclEl = pom.createElement("exclusion");

                    Element groupIdExEl = pom.createElement("groupId");
                    groupIdExEl.setTextContent(e.getGroupId());
                    exclEl.appendChild(groupIdExEl);

                    Element artifactIdExEl = pom.createElement("artifactId");
                    artifactIdExEl.setTextContent(e.getArtifactId());
                    exclEl.appendChild(artifactIdExEl);

                    exclsEl.appendChild(exclEl);
                }

                dependencyEl.appendChild(exclsEl);
            }


            dependenciesSection.appendChild(dependencyEl);
        }


    }

    private void checkConflictsWithExternalBoms(Collection<Dependency> dependencies, Set<String> external) throws MojoFailureException {
        Set<String> errors = new TreeSet<>();
        for (Dependency d : dependencies) {
            String key = comparisonKey(d);
            if (external.contains(key)) {
                errors.add(key);
            }
        }

        if (errors.size() > 0) {
            StringBuilder msg = new StringBuilder();
            msg.append("Found ").append(errors.size()).append(" conflicts between the current managed dependencies and the external BOMS:\n");
            for (String error : errors) {
                msg.append(" - ").append(error).append("\n");
            }

            throw new MojoFailureException(msg.toString());
        }
    }

    private Set<String> getExternallyManagedDependencies() throws Exception {
        Set<String> provided = new HashSet<>();
        if (checkConflicts != null && checkConflicts.getBoms() != null) {
            for (ExternalBomConflictCheck check : checkConflicts.getBoms()) {
                Set<String> bomProvided = getProvidedDependencyManagement(check.getGroupId(), check.getArtifactId(), check.getVersion());
                provided.addAll(bomProvided);
            }
        }

        return provided;
    }

    private Set<String> getProvidedDependencyManagement(String groupId, String artifactId, String version) throws Exception {
        return getProvidedDependencyManagement(groupId, artifactId, version, new TreeSet<>());
    }

    private Set<String> getProvidedDependencyManagement(String groupId, String artifactId, String version, Set<String> gaChecked) throws Exception {
        String ga = groupId + ":" + artifactId;
        gaChecked.add(ga);
        Artifact bom = resolveArtifact(groupId, artifactId, version, "pom");
        MavenProject bomProject = loadExternalProjectPom(bom.getFile());

        Set<String> provided = new HashSet<>();
        if (bomProject.getDependencyManagement() != null && bomProject.getDependencyManagement().getDependencies() != null) {
            for (Dependency dep : bomProject.getDependencyManagement().getDependencies()) {
                if ("pom".equals(dep.getType()) && "import".equals(dep.getScope())) {
                    String subGa = dep.getGroupId() + ":" + dep.getArtifactId();
                    if (!gaChecked.contains(subGa)) {
                        Set<String> sub = getProvidedDependencyManagement(dep.getGroupId(), dep.getArtifactId(), resolveVersion(bomProject, dep.getVersion()), gaChecked);
                        provided.addAll(sub);
                    }
                } else {
                    provided.add(comparisonKey(dep));
                }
            }
        }

        return provided;
    }

    private String resolveVersion(MavenProject project, String version) {
        if (version.contains("${")) {
            int start = version.indexOf("${");
            int end = version.indexOf("}");
            if (end > start) {
                String prop = version.substring(start + 2, end);
                String resolved = project.getProperties().getProperty(prop);
                if (resolved != null) {
                    version = version.substring(0, start) + resolved + version.substring(end + 1);
                }
            }
        }
        return version;
    }

    private String comparisonKey(Dependency dependency) {
        return dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + (dependency.getType() != null ? dependency.getType() : "jar");
    }

    private Artifact resolveArtifact(String groupId, String artifactId, String version, String type) throws Exception {

        Artifact art = artifactFactory.createArtifact(groupId, artifactId, version, "runtime", type);

        artifactResolver.resolve(art, remoteRepositories, localRepository);

        return art;
    }

    private MavenProject loadExternalProjectPom(File pomFile) throws Exception {
        try (FileReader reader = new FileReader(pomFile)) {
            MavenXpp3Reader mavenReader = new MavenXpp3Reader();
            Model model = mavenReader.read(reader);

            MavenProject project = new MavenProject(model);
            project.setFile(pomFile);
            return project;
        }
    }

}
