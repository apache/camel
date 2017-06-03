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
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Properties;
import java.util.Set;

import org.apache.camel.catalog.CamelCatalog;

import static org.apache.camel.catalog.maven.ComponentArtifactHelper.extractComponentJavaType;
import static org.apache.camel.catalog.maven.ComponentArtifactHelper.loadComponentJSonSchema;
import static org.apache.camel.catalog.maven.ComponentArtifactHelper.loadComponentProperties;

/**
 * Nexus repository that can scan for custom Camel components and add to the {@link org.apache.camel.catalog.CamelCatalog}.
 */
public class ComponentCatalogNexusRepository extends BaseNexusRepository {

    private CamelCatalog camelCatalog;

    public ComponentCatalogNexusRepository() {
        super("component");
    }

    public CamelCatalog getCamelCatalog() {
        return camelCatalog;
    }

    /**
     * Sets the {@link CamelCatalog} to be used.
     */
    public void setCamelCatalog(CamelCatalog camelCatalog) {
        this.camelCatalog = camelCatalog;
    }

    @Override
    public void start() {
        if (camelCatalog == null) {
            throw new IllegalArgumentException("CamelCatalog must be configured");
        }

        super.start();
    }

    @Override
    public void onNewArtifacts(Set<NexusArtifactDto> newArtifacts) {
        // now download the new artifact JARs and look inside to find more details
        for (NexusArtifactDto dto : newArtifacts) {
            try {
                logger.debug("Processing new artifact: {}:{}:{}", dto.getGroupId(), dto.getArtifactId(), dto.getVersion());
                String url = createArtifactURL(dto);
                URL jarUrl = new URL(url);
                addCustomCamelComponentsFromArtifact(dto, jarUrl);
            } catch (Throwable e) {
                logger.warn("Error downloading component JAR " + dto.getArtifactLink() + ". This exception is ignored. " + e.getMessage());
            }
        }
    }

    /**
     * Adds the component to the {@link CamelCatalog}
     *
     * @param dto           the artifact
     * @param camelCatalog  the Camel Catalog
     * @param scheme        component name
     * @param javaType      component java class
     * @param json          component json schema
     */
    protected void addComponent(NexusArtifactDto dto, CamelCatalog camelCatalog, String scheme, String javaType, String json) {
        camelCatalog.addComponent(scheme, javaType, json);
        logger.info("Added component: {}:{}:{} to Camel Catalog", dto.getGroupId(), dto.getArtifactId(), dto.getVersion());
    }

    /**
     * Adds any discovered third party Camel components from the artifact.
     */
    private void addCustomCamelComponentsFromArtifact(NexusArtifactDto dto, URL jarUrl) {
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{jarUrl})) {
            // is there any custom Camel components in this library?
            Properties properties = loadComponentProperties(log, classLoader);
            String components = (String) properties.get("components");
            if (components != null) {
                String[] part = components.split("\\s");
                for (String scheme : part) {
                    if (!getCamelCatalog().findComponentNames().contains(scheme)) {
                        // find the class name
                        String javaType = extractComponentJavaType(log, classLoader, scheme);
                        if (javaType != null) {
                            String json = loadComponentJSonSchema(log, classLoader, scheme);
                            if (json != null) {
                                addComponent(dto, getCamelCatalog(), scheme, javaType, json);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.warn("Error scanning JAR for custom Camel components", e);
        }
    }

}
