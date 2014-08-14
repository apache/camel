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
package org.apache.camel.view;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.RouteDefinition;

/**
 * @version 
 */
@Deprecated
public abstract class GraphGeneratorSupport extends GraphSupport {
    protected String dir;
    protected int clusterCounter;
    protected String extension;

    private final boolean makeParentDirs = true;
    private Map<String, List<RouteDefinition>> routeGroupMap;

    protected GraphGeneratorSupport(String dir, String extension) {
        this.dir = dir;
        this.extension = extension;
    }

    public String getRoutesText(CamelContext context) throws IOException {
        // used by web console
        List<RouteDefinition> routes = ((ModelCamelContext)context).getRouteDefinitions();
        routeGroupMap = createRouteGroupMap(routes);
        return createRouteMapText();
    }

    private String createRouteMapText() {
        StringWriter buffer = new StringWriter();
        PrintWriter writer = new PrintWriter(buffer);
        generateFile(writer, routeGroupMap);
        writer.close();
        return buffer.toString();
    }

    public void drawRoutes(CamelContext context) throws IOException {
        File parent = new File(dir);
        if (makeParentDirs) {
            parent.mkdirs();
        }
        List<RouteDefinition> routes = context.getRouteDefinitions();
        routeGroupMap = createRouteGroupMap(routes);

        // generate the global file
        generateFile(parent, "routes" + extension, routeGroupMap);

        if (routeGroupMap.size() >= 1) {
            Set<Map.Entry<String, List<RouteDefinition>>> entries = routeGroupMap.entrySet();
            for (Map.Entry<String, List<RouteDefinition>> entry : entries) {

                Map<String, List<RouteDefinition>> map = new HashMap<String, List<RouteDefinition>>();
                String group = entry.getKey();
                map.put(group, entry.getValue());

                // generate the file containing just the routes in this group
                generateFile(parent, group + extension, map);
            }
        }
    }

    private void generateFile(File parent, String fileName, Map<String, List<RouteDefinition>> map) throws IOException {
        nodeMap.clear();
        clusterCounter = 0;

        PrintWriter writer = new PrintWriter(new FileWriter(new File(parent, fileName)));
        try {
            generateFile(writer, map);
        } finally {
            writer.close();
        }
    }

    protected abstract void generateFile(PrintWriter writer, Map<String, List<RouteDefinition>> map);

}
