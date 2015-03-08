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
package org.apache.camel.maven;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Injects EIP documentation to camel schema.
 */
@Mojo(name = "eip-documentation-enricher", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresProject = true,
        defaultPhase = LifecyclePhase.PREPARE_PACKAGE)
public class EipDocumentationEnricherMojo extends AbstractMojo {

    /**
     * Path to camel EIP schema.
     */
    @Parameter(required = true)
    File inputCamelSchemaFile;

    /**
     * Path to camel EIP schema with enriched documentation.
     */
    @Parameter(required = true)
    File outputCamelSchemaFile;

    /**
     * Path to camel core project root directory.
     */
    @Parameter(defaultValue = "${project.build.directory}/../../..//camel-core")
    File camelCoreDir;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Set<String> injectedTypes = new HashSet<String>();
        File rootDir = new File(camelCoreDir, Constants.PATH_TO_MODEL_DIR);
        Document document = buildNamespaceAwareDocument(inputCamelSchemaFile);
        XPath xPath = buildXPath(new CamelSpringNamespace());
        DomFinder domFinder = new DomFinder(document, xPath);
        DocumentationEnricher documentationEnricher = new DocumentationEnricher(document);
        Map<String, File> jsonFiles = PackageHelper.findJsonFiles(rootDir);
        try {
            NodeList elementsAndTypes = domFinder.findElementsAndTypes();
            documentationEnricher.enrichTopLevelElementsDocumentation(elementsAndTypes, jsonFiles);
            Map<String, String> typeToNameMap = buildTypeToNameMap(elementsAndTypes);
            for (Map.Entry<String, String> entry : typeToNameMap.entrySet()) {
                String elementType = entry.getKey();
                String elementName = entry.getValue();
                if (jsonFileExistsForElement(jsonFiles, elementName)) {
                    injectAttributesDocumentation(domFinder,
                            documentationEnricher,
                            jsonFiles.get(elementName),
                            elementType,
                            injectedTypes);
                }
            }
            saveToFile(document, outputCamelSchemaFile, buildTransformer());
        } catch (XPathExpressionException e) {
            throw new MojoExecutionException("Error during documentation enrichment", e);
        } catch (IOException e) {
            throw new MojoExecutionException("Error during documentation enrichment", e);
        }
    }

    private boolean jsonFileExistsForElement(Map<String, File> jsonFiles,
                                             String elementName) {
        return jsonFiles.containsKey(elementName);
    }

    private void injectAttributesDocumentation(DomFinder domFinder,
                                               DocumentationEnricher documentationEnricher,
                                               File jsonFile,
                                               String type,
                                               Set<String> injectedTypes) throws XPathExpressionException, IOException {
        NodeList attributeElements = domFinder.findAttributesElements(type);
        if (attributeElements.getLength() > 0) {
            documentationEnricher.enrichTypeAttributesDocumentation(attributeElements, jsonFile);
            injectedTypes.add(type);
            String baseType = domFinder.findBaseType(type);
            baseType = truncateTypeNamespace(baseType);
            if (baseType != null && !injectedTypes.contains(baseType)) {
                injectAttributesDocumentation(domFinder, documentationEnricher, jsonFile, baseType, injectedTypes);
            }
        }
    }

    private Map<String, String> buildTypeToNameMap(NodeList elementsAndTypes) {
        Map<String, String> typeToNameMap = new HashMap<String, String>();
        for (int i = 0; i < elementsAndTypes.getLength(); i++) {
            Element item = (Element) elementsAndTypes.item(i);
            String name = item.getAttribute(Constants.NAME_ATTRIBUTE_NAME);
            String type = item.getAttribute(Constants.TYPE_ATTRIBUTE_NAME);
            if (name != null && type != null) {
                type = truncateTypeNamespace(type);
                if (getLog().isDebugEnabled()) {
                    getLog().debug(String.format("Putting attributes type:'%s', name:'%s'", name, type));
                }
                typeToNameMap.put(type, name);
            }
        }
        return typeToNameMap;
    }

    private String truncateTypeNamespace(String baseType) {
        return baseType.replaceAll("tns:", "");
    }

    private XPath buildXPath(NamespaceContext namespaceContext) {
        XPath xPath = XPathFactory.newInstance().newXPath();
        xPath.setNamespaceContext(namespaceContext);
        return xPath;
    }

    private Transformer buildTransformer() throws MojoExecutionException {
        Transformer transformer;
        try {
            transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        } catch (TransformerConfigurationException e) {
            throw new MojoExecutionException("Error during building transformer", e);
        }
        return transformer;
    }

    public Document buildNamespaceAwareDocument(File xml) throws MojoExecutionException {
        Document result;
        DocumentBuilder builder;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try {
            builder = factory.newDocumentBuilder();
            result =  builder.parse(xml);
        } catch (SAXException e) {
            throw new MojoExecutionException("Error during building a document", e);
        } catch (ParserConfigurationException e) {
            throw new MojoExecutionException("Error during building a document", e);
        } catch (IOException  e) {
            throw new MojoExecutionException("Error during building a document", e);
        }
        return result;
    }

    private void saveToFile(Document document, File outputFile, Transformer transformer) throws MojoExecutionException {
        try {
            StreamResult result = new StreamResult(new FileOutputStream(outputFile));
            DOMSource source = new DOMSource(document);
            transformer.transform(source, result);
        } catch (TransformerException e) {
            throw new MojoExecutionException("Error during saving to file", e);
        } catch (FileNotFoundException e) {
            throw new MojoExecutionException("Error during saving to file", e);
        }
    }
}
