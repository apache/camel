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
package org.apache.camel.catalog.nexus;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Properties;
import java.util.Set;

import static org.apache.camel.catalog.CatalogHelper.loadText;

/**
 * Nexus repository that can scan for custom Camel components and add to the {@link org.apache.camel.catalog.CamelCatalog}.
 */
public class ComponentNexusRepository extends BaseNexusRepository {

    public ComponentNexusRepository() {
        super("component");
    }

    @Override
    public void onNewArtifacts(Set<NexusArtifactDto> newArtifacts) {
        // now download the new artifact JARs and look inside to find more details
        for (NexusArtifactDto dto : newArtifacts) {
            try {
                // download using url classloader reader
                URL jarUrl = new URL(dto.getArtifactLink());
                addCustomCamelComponentsFromArtifact(dto, jarUrl);
            } catch (Exception e) {
                log.warn("Error downloading component JAR " + dto.getArtifactLink() + ". This exception is ignored. " + e.getMessage());
            }
        }
    }


    /**
     * Adds any discovered third party Camel components from the artifact.
     */
    private void addCustomCamelComponentsFromArtifact(NexusArtifactDto dto, URL jarUrl ) {
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{jarUrl});) {
            // is there any custom Camel components in this library?
            Properties properties = loadComponentProperties(classLoader);
            if (properties != null) {
                String components = (String) properties.get("components");
                if (components != null) {
                    String[] part = components.split("\\s");
                    for (String scheme : part) {
                        if (!getCamelCatalog().findComponentNames().contains(scheme)) {
                            // find the class name
                            String javaType = extractComponentJavaType(classLoader, scheme);
                            if (javaType != null) {
                                String json = loadComponentJSonSchema(classLoader, scheme);
                                if (json != null) {
                                    log.info("Added component: {}:{}:{} to Camel Catalog", dto.getGroupId(), dto.getArtifactId(), dto.getVersion());
                                    getCamelCatalog().addComponent(scheme, javaType, json);
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Error scanning JAR for custom Camel components", e);
        }
    }

    private Properties loadComponentProperties(URLClassLoader classLoader) {
        Properties answer = new Properties();
        try {
            // load the component files using the recommended way by a component.properties file
            InputStream is = classLoader.getResourceAsStream("META-INF/services/org/apache/camel/component.properties");
            if (is != null) {
                answer.load(is);
            }
        } catch (Throwable e) {
            log.warn("Error loading META-INF/services/org/apache/camel/component.properties file", e);
        }
        return answer;
    }

    private String extractComponentJavaType(URLClassLoader classLoader, String scheme) {
        try {
            InputStream is = classLoader.getResourceAsStream("META-INF/services/org/apache/camel/component/" + scheme);
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                return (String) props.get("class");
            }
        } catch (Throwable e) {
            log.warn("Error loading META-INF/services/org/apache/camel/component/" + scheme + " file", e);
        }

        return null;
    }

    private String loadComponentJSonSchema(URLClassLoader classLoader, String scheme) {
        String answer = null;

        String path = null;
        String javaType = extractComponentJavaType(classLoader, scheme);
        if (javaType != null) {
            int pos = javaType.lastIndexOf(".");
            path = javaType.substring(0, pos);
            path = path.replace('.', '/');
            path = path + "/" + scheme + ".json";
        }

        if (path != null) {
            try {
                InputStream is = classLoader.getResourceAsStream(path);
                if (is != null) {
                    answer = loadText(is);
                }
            } catch (Throwable e) {
                log.warn("Error loading " + path + " file", e);
            }
        }

        return answer;
    }
}
