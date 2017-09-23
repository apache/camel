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
import java.util.Iterator;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.catalog.CollectionStringBuffer;
import org.apache.camel.catalog.connector.CamelConnectorCatalog;

import static org.apache.camel.catalog.CatalogHelper.loadText;

/**
 * Nexus repository that can scan for custom Camel connectors and add to the {@link CamelConnectorCatalog}.
 */
public class ConnectorCatalogNexusRepository extends BaseNexusRepository {

    private CamelConnectorCatalog camelConnectorCatalog;

    public ConnectorCatalogNexusRepository() {
        super("connector");
    }

    public CamelConnectorCatalog getCamelConnectorCatalog() {
        return camelConnectorCatalog;
    }

    /**
     * Sets the {@link CamelConnectorCatalog} to be used.
     */
    public void setCamelConnectorCatalog(CamelConnectorCatalog camelConnectorCatalog) {
        this.camelConnectorCatalog = camelConnectorCatalog;
    }

    @Override
    public void start() {
        if (camelConnectorCatalog == null) {
            throw new IllegalArgumentException("CamelConnectorCatalog must be configured");
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
                addCustomCamelConnectorFromArtifact(dto, jarUrl);
            } catch (Throwable e) {
                logger.warn("Error downloading connector JAR " + dto.getArtifactLink() + ". This exception is ignored. " + e.getMessage());
            }
        }
    }

    /**
     * Adds the connector to the data store
     *
     * @param dto                 the artifact
     * @param name                the name of connector
     * @param scheme              the connector scheme
     * @param javaType            the connector java type
     * @param description         the description of connector
     * @param labels              the labels of connector
     * @param connectorJson       camel-connector JSon
     * @param connectorSchemaJson camel-connector-schema JSon
     * @param componentSchemaJson camel-component-schema JSon
     */
    protected void addConnector(NexusArtifactDto dto, String name, String scheme, String javaType, String description, String labels,
                                String connectorJson, String connectorSchemaJson, String componentSchemaJson) {

        String groupId = dto.getGroupId();
        String artifactId = dto.getArtifactId();
        String version = dto.getVersion();

        camelConnectorCatalog.addConnector(groupId, artifactId, version, name, scheme, javaType, description, labels, connectorJson, connectorSchemaJson, componentSchemaJson);
        logger.info("Added connector: {}:{}:{}", dto.getGroupId(), dto.getArtifactId(), dto.getVersion());
    }

    /**
     * Adds any discovered third party Camel connectors from the artifact.
     */
    private void addCustomCamelConnectorFromArtifact(NexusArtifactDto dto, URL jarUrl) {
        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {jarUrl});) {
            String[] json = loadConnectorJSonSchema(classLoader);
            if (json != null) {

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

                addConnector(dto, name, scheme, javaType, description, csb.toString(), json[0], json[1], json[2]);
            }
        } catch (IOException e) {
            logger.warn("Error scanning JAR for custom Camel components", e);
        }
    }

    private String[] loadConnectorJSonSchema(ClassLoader classLoader) {
        String[] answer = new String[3];

        String path = "camel-connector.json";
        try {
            InputStream is = classLoader.getResourceAsStream(path);
            if (is != null) {
                answer[0] = loadText(is);
            }
        } catch (Throwable e) {
            logger.warn("Error loading " + path + " file", e);
            return null;
        }

        path = "camel-connector-schema.json";
        try {
            InputStream is = classLoader.getResourceAsStream(path);
            if (is != null) {
                answer[1] = loadText(is);
            }
        } catch (Throwable e) {
            logger.warn("Error loading " + path + " file", e);
            return null;
        }

        path = "camel-component-schema.json";
        try {
            InputStream is = classLoader.getResourceAsStream(path);
            if (is != null) {
                answer[2] = loadText(is);
            }
        } catch (Throwable e) {
            logger.warn("Error loading " + path + " file", e);
            return null;
        }

        return answer;
    }

}
