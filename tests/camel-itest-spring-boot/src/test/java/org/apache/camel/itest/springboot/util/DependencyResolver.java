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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Resolves the currently used version of a library. Useful to run unit tests directly from the IDE, without passing additional parameters.
 * It resolves surefire properties.
 */
public final class DependencyResolver {

    private static final String DEFAULT_PREFIX = "version_";

    private static DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    private static XPathFactory xPathfactory = XPathFactory.newInstance();

    private DependencyResolver() {
    }

    /**
     * Retrieves a list of transitive exclusions included in the pom, given the coordinate of a maven dependency.
     * Useful to overcome limitations of Arquillian maven resolver.
     *
     * @param pom the pom where dependencies should be looked up
     * @param groupId the group id of the dependency
     * @param artifactId the artifact id of the dependency
     * @return the set of exclusions in the form groupId:artifactId
     * @throws Exception if anything goes wrong
     */
    public static Set<String> getExclusions(String pom, String groupId, String artifactId) throws Exception {
        String expression = "/project/dependencies/dependency[groupId='" + groupId + "' and artifactId='" + artifactId + "']/exclusions";

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(pom);
        XPath xpath = xPathfactory.newXPath();
        XPathExpression expr = xpath.compile(expression);

        NodeList nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
        if (nodes == null || nodes.getLength() < 1) {
            return Collections.emptySet();
        }

        Set<String> exclusionSet = new HashSet<>();

        NodeList exclusions = nodes.item(0).getChildNodes();
        for (int i = 0; i < exclusions.getLength(); i++) {
            Node exclusionNode = exclusions.item(i);
            if (exclusionNode instanceof Element) {
                Element exclusion = (Element) exclusionNode;
                NodeList exclGroupIds = exclusion.getElementsByTagName("groupId");
                String exclGroupIdVal = exclGroupIds.getLength() == 0 ? null : exclGroupIds.item(0).getTextContent();
                NodeList exclArtifactIds = exclusion.getElementsByTagName("artifactId");
                String exclArtifactIdVal = exclGroupIds.getLength() == 0 ? null : exclArtifactIds.item(0).getTextContent();
                if (exclGroupIdVal != null && exclArtifactIdVal != null) {
                    exclusionSet.add(exclGroupIdVal + ":" + exclArtifactIdVal);
                }
            }
        }

        return exclusionSet;
    }

    /**
     * Gets a groupId and artifactId in the form "groupId:artifactId" and returns the current version from the pom.
     * Uses {@link DependencyResolver#withVersion(String, String)} using a default prefix.
     *
     * @param groupArtifact the groupId and artifactId in the form "groupId:artifactId"
     * @return the maven canonical form of the artifact "groupId:artifactId:version"
     */
    public static String withVersion(String groupArtifact) {
        return withVersion(DEFAULT_PREFIX, groupArtifact);
    }

    /**
     * Gets a groupId and artifactId in the form "groupId:artifactId" and returns the current version from the pom.
     * Versions are resolved from system properties when using surefire, and by looking at the poms when running from IDE.
     *
     * @param prefix the prefix to use to lookup the property from surefire
     * @param groupArtifact the groupId and artifactId in the form "groupId:artifactId"
     * @return the maven canonical form of the artifact "groupId:artifactId:version"
     */
    public static String withVersion(String prefix, String groupArtifact) {
        String version = System.getProperty(prefix + groupArtifact);

        try {
            if (version == null) {
                // Usually, when running from IDE
                version = resolveSurefireProperty(prefix + groupArtifact);
            }
        } catch (Exception e) {
            // cannot use logging libs
            System.out.println("RESOLVER ERROR>> Error while retrieving version for artifact: " + groupArtifact);
            e.printStackTrace();
            return groupArtifact;
        }

        if (version == null) {
            System.out.println("RESOLVER ERROR>> Cannot determine version for maven artifact: " + groupArtifact);
            return groupArtifact;
        } else if (!isResolved(version)) {
            System.out.println("RESOLVER ERROR>> Cannot resolve version for maven artifact: " + groupArtifact + ". Missing property value: " + version);
            return groupArtifact;
        }

        return groupArtifact + ":" + version;
    }

    public static String resolveParentProperty(String property) throws Exception {
        return resolveProperty("../../parent/pom.xml", property, 0);
    }

    private static String resolveSurefireProperty(String property) throws Exception {
        property = getSurefirePropertyFromPom("pom.xml", property);
        if (property != null && !isResolved(property)) {
            property = resolveProperty("pom.xml", property, 0);
        }
        if (property != null && !isResolved(property)) {
            property = resolveProperty("../pom.xml", property, 0);
        }
        if (property != null && !isResolved(property)) {
            property = resolveProperty("../../parent/pom.xml", property, 0);
        }

        return property;
    }

    private static String resolveProperty(String pom, String property, int depth) throws Exception {
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
    }

    private static String getSurefirePropertyFromPom(String pom, String property) throws Exception {
        return xpath(pom, "//plugin[artifactId='maven-surefire-plugin']//systemProperties/property[name='" + property + "']/value/text()");
    }

    private static String getPropertyFromPom(String pom, String property) throws Exception {
        return xpath(pom, "/project/properties/" + property + "/text()");
    }

    private static String getParentVersion(String pom) throws Exception {
        return xpath(pom, "/project/parent/version/text()");
    }

    private static String xpath(String pom, String expression) throws Exception {
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
