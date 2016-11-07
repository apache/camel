/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.catalog.karaf;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultRuntimeProvider;
import org.apache.camel.catalog.JSonSchemaHelper;
import org.apache.camel.catalog.RuntimeProvider;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.w3c.dom.Node.ELEMENT_NODE;

public class KarafRuntimeProvider implements RuntimeProvider {

    private static final String FEATURES = "org/apache/camel/catalog/karaf/features.xml";
    private CamelCatalog camelCatalog;
    private DefaultRuntimeProvider defaultProvider = new DefaultRuntimeProvider();

    private Map<String, List<Map<String, String>>> rowsCache = new HashMap<>();

    @Override
    public CamelCatalog getCamelCatalog() {
        return camelCatalog;
    }

    @Override
    public void setCamelCatalog(CamelCatalog camelCatalog) {
        this.camelCatalog = camelCatalog;
        this.defaultProvider.setCamelCatalog(camelCatalog);
    }

    @Override
    public String getProviderName() {
        return "karaf";
    }

    @Override
    public List<String> findComponentNames() {
        // find the component name from all the default components
        List<String> allNames = defaultProvider.findComponentNames();

        List<String> answer = new ArrayList<>();

        // filter out to only include what's in the karaf features file
        InputStream is = camelCatalog.getVersionManager().getResourceAsStream(FEATURES);
        if (is != null) {
            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setIgnoringComments(true);
                dbf.setIgnoringElementContentWhitespace(true);
                dbf.setNamespaceAware(false);
                dbf.setValidating(false);
                dbf.setXIncludeAware(false);
                Document dom = dbf.newDocumentBuilder().parse(is);
                NodeList children = dom.getElementsByTagName("features");

                for (int i = 0; i < children.getLength(); i++) {
                    Node child = children.item(i);
                    if (child.getNodeType() == ELEMENT_NODE) {
                        NodeList children2 = child.getChildNodes();
                        for (int j = 0; j < children2.getLength(); j++) {
                            Node child2 = children2.item(j);
                            if ("feature".equals(child2.getNodeName())) {
                                // the name attribute is the maven artifact id of the component
                                String artifactId = child2.getAttributes().getNamedItem("name").getTextContent();
                                if (artifactId != null && artifactId.startsWith("camel-")) {
                                    // find the component name based on the artifact id
                                    String componentName = componentNameFromArtifactId(artifactId, allNames);
                                    if (componentName != null) {
                                        answer.add(componentName);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        }

        System.out.println("Total components " + allNames.size() + " karaf supports " + answer.size());

        // clear temporary cache
        rowsCache.clear();

        return answer;
    }

    @Override
    public List<String> findDataFormatNames() {
        // karaf support all data formats
        return defaultProvider.findDataFormatNames();
    }

    @Override
    public List<String> findLanguageNames() {
        // karaf support all languages
        return defaultProvider.findLanguageNames();
    }

    private String componentNameFromArtifactId(String artifactId, List<String> allNames) {
        // try a quick shortcut that is faster
        String quick = artifactId.startsWith("camel-") ? artifactId.substring(6) : null;
        if (quick != null) {
            String json = camelCatalog.componentJSonSchema(quick);
            if (json != null) {
                List<Map<String, String>> rows = rowsCache.get(quick);
                if (rows == null) {
                    rows = JSonSchemaHelper.parseJsonSchema("component", json, false);
                    rowsCache.put(quick, rows);
                }
                String componentArtifactId = getArtifactId(rows);
                if (artifactId.equals(componentArtifactId)) {
                    return quick;
                }
            }
        }

        for (String name : allNames) {
            String json = camelCatalog.componentJSonSchema(name);
            if (json != null) {
                List<Map<String, String>> rows = rowsCache.get(quick);
                if (rows == null) {
                    rows = JSonSchemaHelper.parseJsonSchema("component", json, false);
                    rowsCache.put(quick, rows);
                }
                String componentArtifactId = getArtifactId(rows);
                if (artifactId.equals(componentArtifactId)) {
                    return name;
                }
            }
        }
        return null;
    }

    public static String getArtifactId(List<Map<String, String>> rows) {
        for (Map<String, String> row : rows) {
            if (row.get("artifactId") != null) {
                return row.get("artifactId");
            }
        }
        return null;
    }

}
