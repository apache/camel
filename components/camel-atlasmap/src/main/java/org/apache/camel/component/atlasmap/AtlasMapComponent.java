/*
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

package org.apache.camel.component.atlasmap;

import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import io.atlasmap.api.AtlasContextFactory;
import io.atlasmap.core.DefaultAtlasContextFactory;
import org.apache.camel.Endpoint;
import org.apache.camel.component.atlasmap.AtlasMapEndpoint.TargetMapMode;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <a href="http://camel.apache.org/atlasmap-component.html">AtlasMap Component</a> is for working with AtlasMap
 * Data Mapper.
 */
@Component("atlasmap")
public class AtlasMapComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(AtlasMapComponent.class);

    @Metadata(label = "advanced")
    private AtlasContextFactory atlasContextFactory;

    @Metadata(label = "advanced")
    private String propertiesFile;

    public AtlasContextFactory getAtlasContextFactory() {
        return atlasContextFactory;
    }

    /**
     * To use the {@link AtlasContextFactory} otherwise a new engine is created.
     *
     * @param atlasContextFactory {@link AtlasContextFactory}
     */
    public void setAtlasContextFactory(AtlasContextFactory atlasContextFactory) {
        this.atlasContextFactory = atlasContextFactory;
    }

    /**
     * The URI of the properties file which is used for AtlasContextFactory initialization.
     *
     * @param file property file path
     */
    public void setPropertiesFile(String file) {
        propertiesFile = file;
    }

    public String getPropertiesFile() {
        return propertiesFile;
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        boolean cache = getAndRemoveParameter(parameters, "contentCache", Boolean.class, Boolean.TRUE);
        String sourceMapName = getAndRemoveParameter(parameters, "sourceMapName", String.class);
        String targetMapName = getAndRemoveParameter(parameters, "targetMapName", String.class);
        TargetMapMode targetMapMode = getAndRemoveParameter(parameters, "targetMapMode", TargetMapMode.class);
        boolean forceReload = getAndRemoveParameter(parameters, "forceAdmReload", Boolean.class, Boolean.FALSE);
        AtlasMapEndpoint endpoint = new AtlasMapEndpoint(uri, this, remaining);
        setProperties(endpoint, parameters);
        endpoint.setContentCache(cache);
        endpoint.setSourceMapName(sourceMapName);
        endpoint.setTargetMapName(targetMapName);
        endpoint.setForceReload(forceReload);
        endpoint.setAtlasContextFactory(getOrCreateAtlasContextFactory());
        if (targetMapMode != null) {
            endpoint.setTargetMapMode(targetMapMode);
        }

        // if its a http resource then append any remaining parameters and update the
        // resource uri
        if (ResourceHelper.isHttpUri(remaining)) {
            String remainingAndParameters = ResourceHelper.appendParameters(remaining, parameters);
            endpoint.setResourceUri(remainingAndParameters);
        }

        return endpoint;
    }

    private synchronized AtlasContextFactory getOrCreateAtlasContextFactory() throws Exception {
        if (atlasContextFactory != null) {
            return atlasContextFactory;
        }

        atlasContextFactory = DefaultAtlasContextFactory.getInstance();
        atlasContextFactory.addClassLoader(getCamelContext().getApplicationContextClassLoader());
        // load the properties from property file which may overrides the default ones
        if (ObjectHelper.isNotEmpty(getPropertiesFile())) {
            Properties properties = new Properties();
            InputStream reader = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(),
                    getPropertiesFile());
            try {
                properties.load(reader);
                LOG.info("Loaded the Atlas properties file {}", getPropertiesFile());
            } finally {
                IOHelper.close(reader, getPropertiesFile(), LOG);
            }
            LOG.debug("Initializing AtlasContextFactory with properties {}", properties);
            atlasContextFactory.setProperties(properties);
        }
        return atlasContextFactory;
    }

}
