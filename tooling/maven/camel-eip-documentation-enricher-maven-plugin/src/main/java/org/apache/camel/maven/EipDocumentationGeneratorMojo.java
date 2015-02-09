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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Mojo(name = "eip-documentation-enricher", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresProject = true,
    defaultPhase = LifecyclePhase.PACKAGE)
public class EipDocumentationGeneratorMojo extends AbstractMojo {

  /**
   * Project's source directory as specified in the POM.
   */
  @Parameter(required = true)
  File inputCamelSchemaFile;

  @Parameter(required = true)
  File outputCamelSchemaFile;

  @Parameter(defaultValue = "${project.build.directory}/../../..//camel-core")
  File camelCoreDir;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    File rootDir = new File(camelCoreDir, Constants.PATH_TO_MODEL_DIR);
    DomParser domParser = new DomParser();
    DocumentationEnricher documentationEnricher = new DocumentationEnricher();
    Map<String, File> jsonFiles = PackageHelper.findJsonFiles(rootDir);
    XPath xPath = buildXPath(new CamelSpringNamespace());
    try {
      Document document = buildNamespaceAwareDocument(inputCamelSchemaFile);
      NodeList elementsAndTypes = domParser.findElementsAndTypes(document, xPath);
      documentationEnricher.enrichTopLevelElementsDocumentation
          (document, elementsAndTypes, jsonFiles);
      Map<String, String> typeToNameMap = buildTypeToNameMap(elementsAndTypes);
      for (Map.Entry<String, String> entry : typeToNameMap.entrySet()) {
        NodeList attributeElements = domParser.findAttributesElements(document, xPath, entry.getKey());
        if (jsonFiles.containsKey(entry.getValue())){
          documentationEnricher.enrichTypeAttributesDocumentation
              (document, attributeElements, jsonFiles.get(entry.getValue()));
        }
      }
      saveToFile(document, outputCamelSchemaFile, buildTransformer());
    } catch (Exception e) {
      getLog().error(e);
    }
  }

  private Map<String, String> buildTypeToNameMap(NodeList elementsAndTypes) {
    Map<String, String> typeToNameMap = new HashMap<>();
    for (int i = 0; i < elementsAndTypes.getLength(); i++) {
      Element item = (Element) elementsAndTypes.item(i);
      String name = item.getAttribute(Constants.NAME_ATTRIBUTE_NAME);
      String type = item.getAttribute(Constants.TYPE_ATTRIBUTE_NAME);
      if (name != null && type != null) {
        type = type.replaceAll("tns:", "");
        if (getLog().isDebugEnabled()) {
          getLog().debug(String.format("Putting attributes type:'%s', name:'%s'", name, type));
        }
        typeToNameMap.put(type, name);
      }
    }
    return typeToNameMap;
  }

  private XPath buildXPath(NamespaceContext namespaceContext) {
    XPath xPath = XPathFactory.newInstance().newXPath();
    xPath.setNamespaceContext(namespaceContext);
    return xPath;
  }

  private Transformer buildTransformer() throws TransformerConfigurationException {
    Transformer transformer =
        TransformerFactory.newInstance().newTransformer();
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    transformer.setOutputProperty(
        "{http://xml.apache.org/xslt}indent-amount", "2");
    return transformer;
  }

  public Document buildNamespaceAwareDocument(File xml) throws ParserConfigurationException, IOException, SAXException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    DocumentBuilder builder = factory.newDocumentBuilder();
    return builder.parse(xml);
  }

  private void saveToFile(Document document, File outputFile, Transformer transformer) throws IOException, TransformerException {
    StreamResult result =
        new StreamResult(new FileOutputStream(outputFile));
    DOMSource source = new DOMSource(document);
    transformer.transform(source, result);
  }
}
