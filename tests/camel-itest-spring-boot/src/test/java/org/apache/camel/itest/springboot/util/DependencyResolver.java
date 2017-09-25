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
package org.apache.camel.itest.springboot.util;

import java.io.File;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.List;
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
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.apache.camel.itest.springboot.util.LocationUtils.camelRoot;

/**
 * Resolves the currently used version of a library. Useful to run unit tests directly from the IDE, without passing additional parameters.
 * It resolves properties present in spring-boot and camel parent.
 */
public final class DependencyResolver {

    private static DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    private static XPathFactory xPathfactory = XPathFactory.newInstance();

    private DependencyResolver() {
    }

    /**
     * Retrieves a list of dependencies of the given scope
     */
    public static List<String> getDependencies(String pom, String scope) throws Exception {
        String expression = "/project/dependencies/dependency[scope='" + scope + "']";

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(pom);
        XPath xpath = xPathfactory.newXPath();
        XPathExpression expr = xpath.compile(expression);

        List<String> dependencies = new LinkedList<>();
        NodeList nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            try (StringWriter writer = new StringWriter()) {
                Transformer transformer = TransformerFactory.newInstance().newTransformer();
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                transformer.transform(new DOMSource(node), new StreamResult(writer));
                String xml = writer.toString();
                dependencies.add(xml);
            }
        }

        return dependencies;
    }

    public static String resolveModuleOrParentProperty(File modulePom, String property) {
        property = resolveProperty(modulePom, property, 0);
        if (property != null && !isResolved(property)) {
            property = resolveSpringBootParentProperty(property);
        }
        if (property != null && !isResolved(property)) {
            property = resolveCamelParentProperty(property);
        }
        if (property != null && !isResolved(property)) {
            property = resolveCamelProperty(property);
        }

        return property;
    }

    public static String resolveParentProperty(String property) {
        property = resolveSpringBootParentProperty(property);
        if (property != null && !isResolved(property)) {
            property = resolveCamelParentProperty(property);
        }

        return property;
    }

    public static String resolveSpringBootParentProperty(String property) {
        return resolveProperty(camelRoot("platforms/spring-boot/spring-boot-dm/pom.xml"), property, 0);
    }

    public static String resolveCamelParentProperty(String property) {
        return resolveProperty(camelRoot("parent/pom.xml"), property, 0);
    }

    public static String resolveCamelProperty(String property) {
        return resolveProperty(camelRoot("pom.xml"), property, 0);
    }


    private static String resolveProperty(File pom, String property, int depth) {
        try {
            property = property.trim();
            if (!property.startsWith("${") || !property.endsWith("}")) {
                throw new IllegalArgumentException("Wrong property reference: " + property);
            }

            String res;
            if (property.equals("${project.version}")) {
                res = getParentVersion(pom);
            } else {
                String p = property.substring(2);
                p = p.substring(0, p.length() - 1);
                res = getPropertyFromPom(pom, p);
                if (res == null) {
                    return property;
                }
            }

            if (res != null && !isResolved(res) && depth < 5) {
                res = resolveProperty(pom, res, depth + 1);
            }
            return res;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String getPropertyFromPom(File pom, String property) throws Exception {
        return xpath(pom, "/project/properties/" + property + "/text()");
    }

    private static String getParentVersion(File pom) throws Exception {
        return xpath(pom, "/project/parent/version/text()");
    }

    private static String xpath(File pom, String expression) throws Exception {
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(pom);
        XPath xpath = xPathfactory.newXPath();
        XPathExpression expr = xpath.compile(expression);
        String res = expr.evaluate(doc);
        if (res != null && res.trim().length() == 0) {
            res = null;
        }
        return res;
    }

    private static boolean isResolved(String value) {
        return value != null && !value.startsWith("$");
    }

}
