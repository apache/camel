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

import java.util.Map;

import io.atlasmap.api.AtlasContextFactory;
import org.apache.camel.Endpoint;
import org.apache.camel.component.atlasmap.AtlasMapEndpoint.TargetMapMode;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.ResourceHelper;

/**
 * The <a href="http://camel.apache.org/atlasmap-component.html">AtlasMap Component</a> is for working with AtlasMap
 * Data Mapper.
 */
@Component("atlasmap")
public class AtlasMapComponent extends DefaultComponent {

    @Metadata(label = "advanced")
    private AtlasContextFactory atlasContextFactory;

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

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        boolean cache = getAndRemoveParameter(parameters, "contentCache", Boolean.class, Boolean.TRUE);
        String sourceMapName = getAndRemoveParameter(parameters, "sourceMapName", String.class);
        String targetMapName = getAndRemoveParameter(parameters, "targetMapName", String.class);
        TargetMapMode targetMapMode = getAndRemoveParameter(parameters, "targetMapMode", TargetMapMode.class);

        AtlasMapEndpoint endpoint = new AtlasMapEndpoint(uri, this, remaining);
        setProperties(endpoint, parameters);
        endpoint.setContentCache(cache);
        endpoint.setSourceMapName(sourceMapName);
        endpoint.setTargetMapName(targetMapName);
        endpoint.setAtlasContextFactory(getAtlasContextFactory());
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
}
