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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.LRUCache;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <a href="http://camel.apache.org/properties">properties</a> component.
 *
 * @version 
 */
public class PropertiesComponent extends DefaultComponent {

    public static final String PREFIX_TOKEN = "{{";
    public static final String SUFFIX_TOKEN = "}}";

    // must be non greedy patterns
    private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{env:(.*?)\\}", Pattern.DOTALL);
    private static final Pattern SYS_PATTERN = Pattern.compile("\\$\\{(.*?)\\}", Pattern.DOTALL);

    private static final transient Logger LOG = LoggerFactory.getLogger(PropertiesComponent.class);
    private final Map<String[], Properties> cacheMap = new LRUCache<String[], Properties>(1000);
    private PropertiesResolver propertiesResolver = new DefaultPropertiesResolver();
    private PropertiesParser propertiesParser = new DefaultPropertiesParser();
    private String[] locations;
    private boolean cache = true;
    
    public PropertiesComponent() {
    }
    
    public PropertiesComponent(String location) {
        setLocation(location);
    }

    public PropertiesComponent(String... locations) {
        setLocations(locations);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String[] paths = locations;

        // override default locations
        String locations = getAndRemoveParameter(parameters, "locations", String.class);
        if (locations != null) {
            LOG.trace("Overriding default locations with location: {}", locations);
            paths = locations.split(",");
        }
        String endpointUri = parseUri(remaining, paths);
        LOG.debug("Endpoint uri parsed as: {}", endpointUri);
        return getCamelContext().getEndpoint(endpointUri);
    }

    public String parseUri(String uri) throws Exception {
        return parseUri(uri, locations);
    }

    public String parseUri(String uri, String... paths) throws Exception {
        ObjectHelper.notNull(paths, "paths");

        // location may contain JVM system property or OS environment variables
        // so we need to parse those
        String[] locations = parseLocations(paths);

        // check cache first
        Properties prop = cache ? cacheMap.get(locations) : null;
        if (prop == null) {
            prop = propertiesResolver.resolveProperties(getCamelContext(), locations);
            if (cache) {
                cacheMap.put(locations, prop);
            }
        }

        // enclose tokens if missing
        if (!uri.contains(PREFIX_TOKEN) && !uri.startsWith(PREFIX_TOKEN)) {
            uri = PREFIX_TOKEN + uri;
        }
        if (!uri.contains(SUFFIX_TOKEN) && !uri.endsWith(SUFFIX_TOKEN)) {
            uri = uri + SUFFIX_TOKEN;
        }

        LOG.trace("Parsing uri {} with properties: {}", uri, prop);
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
    protected void doStart() throws Exception {
        ServiceHelper.startService(cacheMap);
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(cacheMap);
        super.doStop();
    }

    private String[] parseLocations(String[] locations) {
        String[] answer = new String[locations.length];

        for (int i = 0; i < locations.length; i++) {
            String location = locations[i];
            LOG.trace("Parsing location: {} ", location);

            Matcher matcher = ENV_PATTERN.matcher(location);
            while (matcher.find()) {
                String key = matcher.group(1);
                String value = System.getenv(key);
                if (ObjectHelper.isEmpty(value)) {
                    throw new IllegalArgumentException("Cannot find system environment with key: " + key);
                }
                // must quoute the replacement to have it work as literal replacement
                value = Matcher.quoteReplacement(value);
                location = matcher.replaceFirst(value);
                // must match again as location is changed
                matcher = ENV_PATTERN.matcher(location);
            }

            matcher = SYS_PATTERN.matcher(location);
            while (matcher.find()) {
                String key = matcher.group(1);
                String value = System.getProperty(key);
                if (ObjectHelper.isEmpty(value)) {
                    throw new IllegalArgumentException("Cannot find JVM system property with key: " + key);
                }
                // must quoute the replacement to have it work as literal replacement
                value = Matcher.quoteReplacement(value);
                location = matcher.replaceFirst(value);
                // must match again as location is changed
                matcher = SYS_PATTERN.matcher(location);
            }

            LOG.debug("Parsed location: {} ", location);
            answer[i] = location;
        }

        return answer;
    }

}
