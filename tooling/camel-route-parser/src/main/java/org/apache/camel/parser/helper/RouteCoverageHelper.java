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
package org.apache.camel.parser.helper;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.parser.model.CoverageData;

/**
 * Helper to provide route coverage details.
 */
public final class RouteCoverageHelper {

    private RouteCoverageHelper() {
    }

    public static List<CoverageData> parseDumpRouteCoverageByRouteId(String directory, String routeId) throws Exception {
        List<CoverageData> answer = new ArrayList<>();

        File[] files = new File(directory).listFiles(f -> f.getName().endsWith(".xml"));
        if (files == null) {
            return answer;
        }

        CamelCatalog catalog = new DefaultCamelCatalog(true);

        for (File file : files) {
            try (FileInputStream fis = new FileInputStream(file)) {
                Document dom = XmlLineNumberParser.parseXml(fis);
                NodeList routes = dom.getElementsByTagName("route");
                for (int i = 0; i < routes.getLength(); i++) {
                    Node route = routes.item(i);
                    String id = route.getAttributes().getNamedItem("id").getNodeValue();
                    // must be the target route
                    if (routeId.equals(id)) {
                        // parse each route and build a Map<String, Integer> with the no of messages processed
                        // where String is the EIP name
                        AtomicInteger counter = new AtomicInteger();
                        parseRouteData(catalog, route, answer, counter);
                    }
                }
            }
        }

        return answer;
    }

    private static void parseRouteData(CamelCatalog catalog, Node node, List<CoverageData> data, AtomicInteger counter) {
        // must be a known EIP model
        String key = node.getNodeName();
        boolean valid = catalog.findModelNames().contains(key); // skip route as we use from instead
        if (!valid) {
            return;
        }

        // only calculate for elements within the route
        if (!"route".equals(key)) {
            Integer count = 0;
            Node total = node.getAttributes().getNamedItem("exchangesTotal");
            if (total != null) {
                count = Integer.valueOf(total.getNodeValue());
            }
            CoverageData holder = data.size() > counter.get() ? data.get(counter.get()) : null;
            if (holder != null && holder.getNode().equals(key)) {
                count += holder.getCount();
            }
            if (holder == null) {
                // add new
                data.add(counter.get(), new CoverageData(key, count));
            } else {
                // replace existing
                data.set(counter.get(), new CoverageData(key, count));
            }
            // advance counter
            counter.incrementAndGet();
        }

        // any children
        NodeList children = node.getChildNodes();
        if (children != null) {
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child instanceof Element) {
                    parseRouteData(catalog, child, data, counter);
                }
            }
        }
    }

}
