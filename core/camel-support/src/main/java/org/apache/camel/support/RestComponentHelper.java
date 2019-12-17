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

public final class RestComponentHelper {
    private RestComponentHelper() {
    }
    
    public static Map<String, Object> addHttpRestrictParam(Map<String, Object> queryMap, String verb, Boolean cors) {
        String restrict = verb.toUpperCase(Locale.US);
        if (cors) {
            restrict += ",OPTIONS";
        }
        queryMap.put("httpMethodRestrict", restrict);
        return queryMap;
    }
    
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
    
    public static String createRestConsumerUrl(String componentName, String verb, String path, Boolean cors, Map<String, Object> queryMap) throws URISyntaxException {
        String query = generateComponentQueryString(verb, cors, queryMap);
        return applyFormatAndQuery("%s:/%s?", query, componentName, path);
    }
    
    public static String createRestConsumerUrl(String componentName, String verb, String scheme, String host, int port, String path, Boolean cors, Map<String, Object> queryMap) throws URISyntaxException {
        
        String query = generateComponentQueryString(verb, cors, queryMap);
        
        return applyFormatAndQuery("%s:%s://%s:%s/%s?", query, componentName, scheme, host, port, path);
    }

    private static String generateComponentQueryString(String verb, Boolean cors, Map<String, Object> queryMap)
            throws URISyntaxException {
        addHttpRestrictParam(queryMap, verb, cors);
        
        String query = URISupport.createQueryString(queryMap);
        return query;
    }
    
    private static String applyFormatAndQuery(String format, String query, Object... formatOptions) {
     // get the endpoint
        String url = String.format(format, formatOptions);

        if (!query.isEmpty()) {
            url = url + query;
        }
        return url;
    }
}
