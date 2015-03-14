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
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.apache.commons.lang.StringUtils;
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
    public File inputCamelSchemaFile;

    /**
     * Path to camel EIP schema with enriched documentation.
     */
    @Parameter(required = true)
    public File outputCamelSchemaFile;

    /**
     * Path to camel core project root directory.
     */
    @Parameter(defaultValue = "${project.build.directory}/../../..//camel-core")
    public File camelCoreDir;

    /**
     * Sub path from camel core directory to model directory with generated json files for components.
     */
    @Parameter(defaultValue = "target/classes/org/apache/camel/model")
    public String pathToModelDir;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (pathToModelDir == null) {
            throw new MojoExecutionException("pathToModelDir parameter must not be null");
        }
        validateExists(inputCamelSchemaFile, "inputCamelSchemaFile");
        validateIsFile(inputCamelSchemaFile, "inputCamelSchemaFile");
        validateExists(camelCoreDir, "camelCoreDir");
        validateIsDirectory(camelCoreDir, "camelCoreDir");
        try {
            runPlugin();
        } catch (Exception e) {
            throw new MojoExecutionException("Error during plugin execution", e);
        }
    }

    private void runPlugin() throws Exception {
        File rootDir = new File(camelCoreDir, pathToModelDir);
        Document document = XmlHelper.buildNamespaceAwareDocument(inputCamelSchemaFile);
        XPath xPath = XmlHelper.buildXPath(new CamelSpringNamespace());
        DomFinder domFinder = new DomFinder(document, xPath);
        DocumentationEnricher documentationEnricher = new DocumentationEnricher(document);
        Map<String, File> jsonFiles = PackageHelper.findJsonFiles(rootDir);

        NodeList elementsAndTypes = domFinder.findElementsAndTypes();
        documentationEnricher.enrichTopLevelElementsDocumentation(elementsAndTypes, jsonFiles);
        Map<String, String> typeToNameMap = buildTypeToNameMap(elementsAndTypes);
        Set<String> injectedTypes = new HashSet<String>();

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

        saveToFile(document, outputCamelSchemaFile, XmlHelper.buildTransformer());
    }

    private boolean jsonFileExistsForElement(Map<String, File> jsonFiles,
                                             String elementName) {
        return jsonFiles.containsKey(elementName);
    }

    /**
     * Recursively injects documentation to complex type attributes and it's parents.
     */
    private void injectAttributesDocumentation(DomFinder domFinder,
                                               DocumentationEnricher documentationEnricher,
                                               File jsonFile,
                                               String type,
                                               Set<String> injectedTypes) throws XPathExpressionException, IOException {
        if (injectedTypes.contains(type)) {
            return;
        }

        injectedTypes.add(type);
        NodeList attributeElements = domFinder.findAttributesElements(type);
        if (attributeElements.getLength() > 0) {
            documentationEnricher.enrichTypeAttributesDocumentation(attributeElements, jsonFile);
        }

        String baseType = domFinder.findBaseType(type);
        if (baseType != null && !StringUtils.isEmpty(baseType)) {
            baseType = truncateTypeNamespace(baseType);
            injectAttributesDocumentation(domFinder, documentationEnricher, jsonFile, baseType, injectedTypes);
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


    private void saveToFile(Document document, File outputFile, Transformer transformer) throws FileNotFoundException, TransformerException {
        StreamResult result = new StreamResult(new FileOutputStream(outputFile));
        DOMSource source = new DOMSource(document);
        transformer.transform(source, result);
    }

    private void validateIsFile(File file, String name) throws MojoExecutionException {
        if (!file.isFile()) {
            throw new MojoExecutionException(name + "is not a file");
        }
    }

    private void validateIsDirectory(File file, String name) throws MojoExecutionException {
        if (!file.isDirectory()) {
            throw new MojoExecutionException(name + "is not a directory");
        }
    }

    private void validateExists(File file, String name) throws MojoExecutionException {
        if (file == null || !file.exists()) {
            throw new MojoExecutionException(name + " does not exist");
        }
    }
}
