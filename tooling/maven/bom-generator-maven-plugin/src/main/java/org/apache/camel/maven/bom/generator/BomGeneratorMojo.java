package org.apache.camel.maven.bom.generator;

import java.io.File;
import java.io.FileWriter;
import java.util.LinkedList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Exclusion;
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


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            DependencyManagement mng = project.getDependencyManagement();

            List<Dependency> filteredDependencies = filter(mng.getDependencies());

            Document pom = loadBasePom();

            // transform
            overwriteDependencyManagement(pom, filteredDependencies);

            writePom(pom);

        } catch (Exception ex) {
            throw new MojoExecutionException("Cannot generate the output BOM file", ex);
        }
    }

    private List<Dependency> filter(List<Dependency> dependencyList) {
        List<Dependency> outDependencies = new LinkedList<>();

        DependencyMatcher inclusions = new DependencyMatcher(dependencies.getIncludes());
        DependencyMatcher exclusions = new DependencyMatcher(dependencies.getExcludes());

        for (Dependency dep : dependencyList) {
            boolean accept = inclusions.matches(dep) && !exclusions.matches(dep);
            getLog().debug(dep + (accept ? " included in the BOM" : " excluded from BOM"));

            if (accept) {
                outDependencies.add(dep);
            }
        }

        return outDependencies;
    }

    private Document loadBasePom() throws Exception {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document pom = builder.parse(sourcePom);
        return pom;
    }

    private void writePom(Document pom) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        DOMSource source = new DOMSource(pom);

        targetPom.getParentFile().mkdirs();

        try (FileWriter out = new FileWriter(targetPom)) {
            StreamResult result = new StreamResult(out);
            transformer.transform(source, result);
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

            if(dep.getExclusions()!=null) {

                Element exclsEl = pom.createElement("exclusions");

                for(Exclusion e : dep.getExclusions()) {
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


}
