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
package org.apache.camel.support;

import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.util.HostUtils;
import org.apache.camel.util.URISupport;

/**
 * Helper class for rest-dsl components.
 */
public final class RestComponentHelper {
    
    private RestComponentHelper() {
    }
    
    /**
     * 
     * @param queryMap the map of Endpoint options to apply the HTTP restrict settings to
     * @param verb the HTTP verb for the route
     * @param addOptions should OPTIONS verb be added.
     * @return the map of Endpoint Properties with HTTP Restrict Options set
     */
    public static Map<String, Object> addHttpRestrictParam(Map<String, Object> queryMap, String verb, boolean addOptions) {
        String restrict = verb.toUpperCase(Locale.US);
        if (addOptions) {
            restrict += ",OPTIONS";
        }
        queryMap.put("httpMethodRestrict", restrict);
        return queryMap;
    }
    
    /**
     * 
     * Creates an Endpoint Property Map based on properies set in the component's RestConfiguration.
     * 
     * @param componentName the Rest Component name
     * @param config the RestConfiguration
     * @return the map of Endpoint Properties set in the RestConfiguration
     */
    public static Map<String, Object> initRestEndpointProperties(String componentName, RestConfiguration config) {
        Map<String, Object> map = new HashMap<>();
        // build query string, and append any endpoint configuration properties
        if (config.getComponent() == null || config.getComponent().equals(componentName)) {
            // setup endpoint options
            if (config.getEndpointProperties() != null && !config.getEndpointProperties().isEmpty()) {
                map.putAll(config.getEndpointProperties());
            }
        }
        return map;
    }
    
    /**
     * 
     * Sets the Rest consumer host based on RestConfiguration
     * 
     * @param host the existing host configuration
     * @param config the RestConfiguration
     * @return the host based on RestConfiguration
     * @throws UnknownHostException thrown when local host or local ip can't be resolved via network interfaces.
     */
    public static String resolveRestHostName(String host, RestConfiguration config) throws UnknownHostException {
        if (config.getHostNameResolver() == RestConfiguration.RestHostNameResolver.allLocalIp) {
            host = "0.0.0.0";
        } else if (config.getHostNameResolver() == RestConfiguration.RestHostNameResolver.localHostName) {
            host = HostUtils.getLocalHostName();
        } else if (config.getHostNameResolver() == RestConfiguration.RestHostNameResolver.localIp) {
            host = HostUtils.getLocalIp();
        }
        return host;
    }
    
    /**
     * 
     * Creates the Rest consumers url based on component and url options.
     * 
     * @param componentName the name of the rest component
     * @param verb the HTTP verb
     * @param path the HTTP path of the route
     * @param queryMap the endpoint query options
     * @return a string of the component route url
     * @throws URISyntaxException - is thrown if uri has invalid syntax.
     */
    public static String createRestConsumerUrl(String componentName, String verb, String path, Map<String, Object> queryMap) throws URISyntaxException {
        String query = URISupport.createQueryString(queryMap);
        return applyFormatAndQuery("%s:%s:%s", query, componentName, verb, path);
    }
    
    /**
     * 
     * Creates the Rest consumers url based on component and url options.
     * 
     * @param componentName the name of the rest component
     * @param path the HTTP path of the route
     * @param queryMap the endpoint query options
     * @return a string of the component route url
     * @throws URISyntaxException - is thrown if uri has invalid syntax.
     */
    public static String createRestConsumerUrl(String componentName, String path, Map<String, Object> queryMap) throws URISyntaxException {
        String query = URISupport.createQueryString(queryMap);
        return applyFormatAndQuery("%s:/%s", query, componentName, path);
    }
    
    /**
     * 
     * Creates the Rest consumers url based on component and url options.
     * 
     * @param componentName the name of the rest component
     * @param scheme the scheme of the HTTP route http/https
     * @param host the host of the HTTP route
     * @param port the port the route will be exposed through
     * @param path the HTTP path of the route
     * @param queryMap the endpoint query options
     * @return a string of the component route url
     * @throws URISyntaxException - is thrown if uri has invalid syntax.
     */
    public static String createRestConsumerUrl(String componentName, String scheme, String host, int port, String path, Map<String, Object> queryMap) throws URISyntaxException {
        
        String query = URISupport.createQueryString(queryMap);
        
        return applyFormatAndQuery("%s:%s://%s:%s/%s", query, componentName, scheme, host, port, path);
    }
    
    private static String applyFormatAndQuery(String format, String query, Object... formatOptions) {
        // get the endpoint
        StringBuilder urlBuilder = new StringBuilder(String.format(format, formatOptions));

        if (!query.isEmpty()) {
            urlBuilder.append("?");
            urlBuilder.append(query);
        }
        return urlBuilder.toString();
    }
}
