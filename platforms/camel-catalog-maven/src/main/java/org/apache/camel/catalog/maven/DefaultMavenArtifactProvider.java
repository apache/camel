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
package org.apache.camel.catalog.maven;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import groovy.grape.Grape;
import groovy.lang.GroovyClassLoader;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.CollectionStringBuffer;
import org.apache.camel.catalog.connector.CamelConnectorCatalog;

import static org.apache.camel.catalog.maven.ComponentArtifactHelper.extractComponentJavaType;
import static org.apache.camel.catalog.maven.ComponentArtifactHelper.loadComponentJSonSchema;
import static org.apache.camel.catalog.maven.ComponentArtifactHelper.loadComponentProperties;
import static org.apache.camel.catalog.maven.ConnectorArtifactHelper.loadJSonSchemas;

/**
 * Default {@link MavenArtifactProvider} which uses Groovy Grape to download the artifact.
 */
public class DefaultMavenArtifactProvider implements MavenArtifactProvider {

    private String cacheDirectory;
    private boolean log;

    /**
     * Sets whether to log errors and warnings to System.out.
     * By default nothing is logged.
     */
    public void setLog(boolean log) {
        this.log = log;
    }

    @Override
    public void setCacheDirectory(String directory) {
        this.cacheDirectory = directory;
    }

    @Override
    public void addMavenRepository(String name, String url) {
        Map<String, Object> repo = new HashMap<>();
        repo.put("name", name);
        repo.put("root", url);
        Grape.addResolver(repo);
    }

    @Override
    public Set<String> addArtifactToCatalog(CamelCatalog camelCatalog, CamelConnectorCatalog camelConnectorCatalog,
                                            String groupId, String artifactId, String version) {
        final Set<String> names = new LinkedHashSet<>();

        try {
            if (cacheDirectory != null) {
                if (log) {
                    System.out.println("DEBUG: Using cache directory: " + cacheDirectory);
                }
                System.setProperty("grape.root", cacheDirectory);
            }

            Grape.setEnableAutoDownload(true);

            try (final GroovyClassLoader classLoader = new GroovyClassLoader()) {

                Map<String, Object> param = new HashMap<>();
                param.put("classLoader", classLoader);
                param.put("group", groupId);
                param.put("module", artifactId);
                param.put("version", version);
                // no need to download transitive dependencies as we only need to check the component or connector itself
                param.put("validate", false);
                param.put("transitive", false);

                if (log) {
                    System.out.println("Downloading " + groupId + ":" + artifactId + ":" + version);
                }
                Grape.grab(param);

                // the classloader can load content from the downloaded JAR
                if (camelCatalog != null) {
                    scanCamelComponents(camelCatalog, classLoader, names);
                }
                if (camelConnectorCatalog != null) {
                    scanCamelConnectors(camelConnectorCatalog, classLoader, groupId, artifactId, version, names);
                }
            }

        } catch (Exception e) {
            if (log) {
                System.out.println("WARN: Error during add components from artifact " + groupId + ":" + artifactId + ":" + version + " due " + e.getMessage());
            }
        }

        return names;
    }

    protected void scanCamelComponents(CamelCatalog camelCatalog, ClassLoader classLoader, Set<String> names) {
        // is there any custom Camel components in this library?
        Properties properties = loadComponentProperties(log, classLoader);
        if (properties != null) {
            String components = (String) properties.get("components");
            if (components != null) {
                String[] part = components.split("\\s");
                for (String scheme : part) {
                    if (!camelCatalog.findComponentNames().contains(scheme)) {
                        // find the class name
                        String javaType = extractComponentJavaType(log, classLoader, scheme);
                        if (javaType != null) {
                            String json = loadComponentJSonSchema(log, classLoader, scheme);
                            if (json != null) {
                                if (log) {
                                    System.out.println("Adding component: " + scheme);
                                }
                                camelCatalog.addComponent(scheme, javaType, json);
                                names.add(scheme);
                            }
                        }
                    }
                }
            }
        }
    }

    protected void scanCamelConnectors(CamelConnectorCatalog camelConnectorCatalog, ClassLoader classLoader,
                                       String groupId, String artifactId, String version,
                                       Set<String> names) {
        String[] json = loadJSonSchemas(log, classLoader);
        if (json != null) {
            if (!camelConnectorCatalog.hasConnector(groupId, artifactId, version)) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode tree = mapper.readTree(json[0]);
                    String name = tree.get("name").textValue();
                    String scheme = tree.get("scheme").textValue();
                    String javaType = tree.get("javaType").textValue();
                    String description = tree.get("description").textValue();
                    Iterator<JsonNode> it = tree.withArray("labels").iterator();

                    CollectionStringBuffer csb = new CollectionStringBuffer(",");
                    while (it.hasNext()) {
                        String text = it.next().textValue();
                        csb.append(text);
                    }

                    if (log) {
                        System.out.println("Adding connector: " + name + " with scheme: " + scheme);
                    }
                    camelConnectorCatalog.addConnector(groupId, artifactId, version,
                        name, scheme, javaType, description, csb.toString(), json[0], json[1], json[2]);

                    names.add(name);
                } catch (Throwable e) {
                    if (log) {
                        System.out.println("WARN: Error parsing Connector JSon due " + e.getMessage());
                    }
                }
            }
        }
    }

}

