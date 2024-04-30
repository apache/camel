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
package org.apache.camel.maven;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Predicate;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.util.FileUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Injects EIP documentation to camel schema.
 */
@Mojo(name = "eip-documentation-enricher", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
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
     * Path to camel core-model project root directory.
     */
    @Parameter(defaultValue = "${project.build.directory}/sources/camel-core-model")
    public File camelCoreModelDir;

    /**
     * Path to camel core xml project root directory.
     */
    @Parameter(defaultValue = "${project.build.directory}/sources/camel-core-xml")
    public File camelCoreXmlDir;

    /**
     * Path to camel spring project root directory.
     */
    @Parameter(defaultValue = "${project.build.directory}/sources/camel-spring")
    public File camelSpringDir;

    /**
     * Sub path to itself
     */
    @Parameter(defaultValue = "${project.build.directory}/classes")
    public String targetDir;

    /**
     * Sub path from camel core directory to model directory with generated json files for components.
     */
    @Parameter(defaultValue = "META-INF/org/apache/camel/model")
    public String pathToModelDir;

    /**
     * Sub path from camel core xml directory to model directory with generated json files for components.
     */
    @Parameter(defaultValue = "META-INF/org/apache/camel/core/xml")
    public String pathToCoreXmlModelDir;

    /**
     * Sub path from camel spring directory to model directory with generated json files for components.
     */
    @Parameter(defaultValue = "META-INF/org/apache/camel/spring")
    public String pathToSpringModelDir;

    /**
     * Optional file pattern to delete files after the documentation enrichment is complete.
     */
    @Parameter
    public String deleteFilesAfterRun;

    /**
     * The maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        if (pathToModelDir == null) {
            throw new MojoExecutionException("pathToModelDir parameter must not be null");
        }

        // skip if input file does not exist
        if (inputCamelSchemaFile == null || !inputCamelSchemaFile.exists()) {
            getLog().info("Input Camel schema file: " + inputCamelSchemaFile + " does not exist. Skip EIP document enrichment");
            return;
        }

        // is current dir blueprint
        boolean blueprint = targetDir != null && targetDir.contains("camel-blueprint");

        validateExists(inputCamelSchemaFile, "inputCamelSchemaFile");
        validateIsFile(inputCamelSchemaFile, "inputCamelSchemaFile");
        validateExists(camelCoreXmlDir, "camelCoreXmlDir");
        validateIsDirectory(camelCoreModelDir, "camelCoreModelDir");
        validateIsDirectory(camelCoreXmlDir, "camelCoreXmlDir");
        if (blueprint) {
            validateExists(camelSpringDir, "camelSpringDir");
            validateIsDirectory(camelSpringDir, "camelSpringDir");
        }
        try {
            runPlugin(blueprint);
        } catch (Exception e) {
            throw new MojoExecutionException("Error during plugin execution", e);
        }
        if (deleteFilesAfterRun != null) {
            deleteFilesAfterDone(deleteFilesAfterRun);
        }
    }

    private void runPlugin(boolean blueprint) throws Exception {
        Document document = XmlHelper.buildNamespaceAwareDocument(inputCamelSchemaFile);
        XPath xPath = XmlHelper.buildXPath(new CamelSpringNamespace());
        DomFinder domFinder = new DomFinder(document, xPath);
        DocumentationEnricher documentationEnricher = new DocumentationEnricher(document);

        // include schema files from camel-core-model, camel-core-xml and from camel-spring
        Set<File> files = new HashSet<>();
        PackageHelper.findJsonFiles(new File(camelCoreModelDir, pathToModelDir), files);
        PackageHelper.findJsonFiles(new File(camelCoreXmlDir, pathToCoreXmlModelDir), files);
        // spring/blueprint should not include SpringErrorHandlerDefinition (duplicates a file from camel-core-model)
        Predicate<File> filter = f -> !f.getName().equals("errorHandler.json");
        if (blueprint) {
            PackageHelper.findJsonFiles(new File(camelSpringDir, pathToSpringModelDir), files, filter);
        } else {
            PackageHelper.findJsonFiles(new File(targetDir, pathToSpringModelDir), files, filter);
        }
        Map<String, File> jsonFiles = new HashMap<>();
        files.forEach(f -> jsonFiles.put(PackageHelper.asName(f.toPath()), f));

        NodeList elementsAndTypes = domFinder.findElementsAndTypes();
        documentationEnricher.enrichTopLevelElementsDocumentation(elementsAndTypes, jsonFiles);
        Map<String, String> typeToNameMap = buildTypeToNameMap(elementsAndTypes);
        Set<String> injectedTypes = new LinkedHashSet<>();

        getLog().debug("Found " + typeToNameMap.size() + " models to use when enriching the XSD schema");
        int enriched = 0;

        for (Map.Entry<String, String> entry : typeToNameMap.entrySet()) {
            String elementType = entry.getKey();
            String elementName = entry.getValue();
            if (jsonFileExistsForElement(jsonFiles, elementName)) {
                enriched++;
                getLog().debug("Enriching " + elementName);
                File file = jsonFiles.get(elementName);
                injectChildElementsDocumentation(domFinder, documentationEnricher, file, elementType, injectedTypes);
            } else {
                boolean ignore = "ExpressionDefinition".equalsIgnoreCase(elementName);
                if (!ignore) {
                    getLog().warn("Cannot find json metadata to use for enriching element " + elementName);
                }
            }
        }
        getLog().info("Enriched " + enriched + " models out of " + typeToNameMap.size() + " models");

        String xml = transformToXml(document, XmlHelper.buildTransformer());
        xml = fixXmlOutput(xml);
        xml = removeEmptyLines(xml);

        saveToFile(document, outputCamelSchemaFile, xml);
    }

    public static String fixXmlOutput(String xml) {
        xml = xml.replaceAll("><!\\[CDATA\\[", ">\n<![CDATA[");
        xml = xml.replaceAll("\\h+<!\\[CDATA\\[", "<![CDATA[");
        xml = xml.replaceAll("(\\h*)]]><", "]]>\n$1<");
        return removeEmptyLines(xml);
    }

    public static String removeEmptyLines(String xml) {
        StringJoiner sj = new StringJoiner("\n");
        for (String l : xml.split("\n")) {
            if (!l.isBlank()) {
                sj.add(l);
            }
        }
        return sj.toString();
    }

    private String transformToXml(Document document, Transformer transformer) throws TransformerException {
        StringWriter sw = new StringWriter();
        StreamResult result = new StreamResult(sw);
        DOMSource source = new DOMSource(document);
        transformer.transform(source, result);
        return sw.toString();
    }

    private boolean jsonFileExistsForElement(
            Map<String, File> jsonFiles,
            String elementName) {
        return jsonFiles.containsKey(elementName);
    }

    private void deleteFilesAfterDone(String deleteFiles) {
        String[] names = deleteFiles.split(",");
        for (String name : names) {
            File file = new File(name);
            FileUtil.deleteFile(file);
        }
    }

    /**
     * Recursively injects documentation to complex type attributes and elements and it's parents.
     */
    private void injectChildElementsDocumentation(
            DomFinder domFinder,
            DocumentationEnricher documentationEnricher,
            File jsonFile,
            String type,
            Set<String> injectedTypes)
            throws XPathExpressionException {
        if (injectedTypes.contains(type)) {
            return;
        }

        injectedTypes.add(type);
        NodeList attributeElements = domFinder.findAttributesElements(type);
        if (attributeElements.getLength() > 0) {
            documentationEnricher.enrichElementDocumentation(getLog(), attributeElements, jsonFile);
        }
        NodeList elementElements = domFinder.findElementsElements(type);
        if (elementElements.getLength() > 0) {
            documentationEnricher.enrichElementDocumentation(getLog(), elementElements, jsonFile);
        }

        String baseType = domFinder.findBaseType(type);
        if (baseType != null && !StringUtils.isEmpty(baseType)) {
            baseType = truncateTypeNamespace(baseType);
            injectChildElementsDocumentation(domFinder, documentationEnricher, jsonFile, baseType, injectedTypes);
        }
    }

    private Map<String, String> buildTypeToNameMap(NodeList elementsAndTypes) {
        Map<String, String> typeToNameMap = new LinkedHashMap<>();
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
        return baseType.replace("tns:", "");
    }

    private void saveToFile(Document document, File outputFile, String xml) throws IOException {
        try (FileOutputStream os = new FileOutputStream(outputFile)) {
            os.write(xml.getBytes(StandardCharsets.UTF_8));
        }
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
