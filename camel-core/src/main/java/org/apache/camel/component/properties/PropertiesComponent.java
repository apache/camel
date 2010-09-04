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
package org.apache.camel.component.properties;

import java.util.Map;
import java.util.Properties;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.LRUCache;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The <a href="http://camel.apache.org/properties">properties</a> component.
 *
 * @version $Revision$
 */
public class PropertiesComponent extends DefaultComponent {

    public static final String PREFIX_TOKEN = "{{";
    public static final String SUFFIX_TOKEN = "}}";

    private static final transient Log LOG = LogFactory.getLog(PropertiesComponent.class);
    private final Map<String[], Properties> cacheMap = new LRUCache<String[], Properties>(1000);
    private PropertiesResolver propertiesResolver = new DefaultPropertiesResolver();
    private PropertiesParser propertiesParser = new DefaultPropertiesParser();
    private String[] locations;
    private boolean cache = true;

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String[] paths = locations;

        // override default locations
        String locations = getAndRemoveParameter(parameters, "locations", String.class);
        if (locations != null) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Overriding default locations with location: " + locations);
            }
            paths = locations.split(",");
        }
        
        String endpointUri = parseUri(remaining, paths);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Endpoint uri parsed as: " + endpointUri);
        }
        return getCamelContext().getEndpoint(endpointUri);
    }

    public String parseUri(String uri) throws Exception {
        return parseUri(uri, locations);
    }

    public String parseUri(String uri, String... paths) throws Exception {
        ObjectHelper.notNull(paths, "paths");

        // check cache first
        Properties prop = cache ? cacheMap.get(paths) : null;
        if (prop == null) {
            prop = propertiesResolver.resolveProperties(getCamelContext(), paths);
            if (cache) {
                cacheMap.put(paths, prop);
            }
        }

        // enclose tokens if missing
        if (!uri.contains(PREFIX_TOKEN) && !uri.startsWith(PREFIX_TOKEN)) {
            uri = PREFIX_TOKEN + uri;
        }
        if (!uri.contains(SUFFIX_TOKEN) && !uri.endsWith(SUFFIX_TOKEN)) {
            uri = uri + SUFFIX_TOKEN;
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Parsing uri " + uri + " with properties: " + prop);
        }
        return propertiesParser.parseUri(uri, prop, PREFIX_TOKEN, SUFFIX_TOKEN);
    }

    public String[] getLocations() {
        return locations;
    }

    public void setLocations(String[] locations) {
        this.locations = locations;
    }

    public void setLocation(String location) {
        setLocations(location.split(","));
    }

    public PropertiesResolver getPropertiesResolver() {
        return propertiesResolver;
    }

    public void setPropertiesResolver(PropertiesResolver propertiesResolver) {
        this.propertiesResolver = propertiesResolver;
    }

    public PropertiesParser getPropertiesParser() {
        return propertiesParser;
    }

    public void setPropertiesParser(PropertiesParser propertiesParser) {
        this.propertiesParser = propertiesParser;
    }

    public boolean isCache() {
        return cache;
    }

    public void setCache(boolean cache) {
        this.cache = cache;
    }

    @Override
    protected void doStop() throws Exception {
        cacheMap.clear();
        super.doStop();
    }

}
