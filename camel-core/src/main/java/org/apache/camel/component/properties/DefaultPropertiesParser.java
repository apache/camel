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

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A parser to parse a string which contains property placeholders
 *
 * @version 
 */
public class DefaultPropertiesParser implements AugmentedPropertyNameAwarePropertiesParser {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    
    @Override
    public String parseUri(String text, Properties properties, String prefixToken, String suffixToken) throws IllegalArgumentException {
        return parseUri(text, properties, prefixToken, suffixToken, null, null, false);
    }

    public String parseUri(String text, Properties properties, String prefixToken, String suffixToken,
                           String propertyPrefix, String propertySuffix, boolean fallbackToUnaugmentedProperty) throws IllegalArgumentException {
        String answer = text;
        boolean done = false;

        // the placeholders can contain nested placeholders so we need to do recursive parsing
        // we must therefore also do circular reference check and must keep a list of visited keys
        List<String> visited = new ArrayList<String>();
        while (!done) {
            List<String> replaced = new ArrayList<String>();
            answer = doParseUri(answer, properties, replaced, prefixToken, suffixToken, propertyPrefix, propertySuffix, fallbackToUnaugmentedProperty);

            // check the replaced with the visited to avoid circular reference
            for (String replace : replaced) {
                if (visited.contains(replace)) {
                    throw new IllegalArgumentException("Circular reference detected with key [" + replace + "] from text: " + text);
                }
            }
            // okay all okay so add the replaced as visited
            visited.addAll(replaced);

            // we are done when we can no longer find any prefix tokens in the answer
            done = findTokenPosition(answer, 0, prefixToken) == -1;
        }
        return answer;
    }

    public String parseProperty(String key, String value, Properties properties) {
        return value;
    }

    private String doParseUri(String uri, Properties properties, List<String> replaced, String prefixToken, String suffixToken,
                              String propertyPrefix, String propertySuffix, boolean fallbackToUnaugmentedProperty) {
        StringBuilder sb = new StringBuilder();

        int pivot = 0;
        int size = uri.length();
        while (pivot < size) {
            int idx = findTokenPosition(uri, pivot, prefixToken);
            if (idx < 0) {
                sb.append(createConstantPart(uri, pivot, size));
                break;
            } else {
                if (pivot < idx) {
                    sb.append(createConstantPart(uri, pivot, idx));
                }
                pivot = idx + prefixToken.length();
                int endIdx = findTokenPosition(uri, pivot, suffixToken);
                if (endIdx < 0) {
                    throw new IllegalArgumentException("Expecting " + suffixToken + " but found end of string from text: " + uri);
                }
                String key = uri.substring(pivot, endIdx);
                String augmentedKey = key;
                
                if (propertyPrefix != null) {
                    log.debug("Augmenting property key [{}] with prefix: {}", key, propertyPrefix);
                    augmentedKey = propertyPrefix + augmentedKey;
                }
                
                if (propertySuffix != null) {
                    log.debug("Augmenting property key [{}] with suffix: {}", key, propertySuffix);
                    augmentedKey = augmentedKey + propertySuffix;
                }

                String part = createPlaceholderPart(augmentedKey, properties, replaced, prefixToken, suffixToken);
                
                // Note: Only fallback to unaugmented when the original key was actually augmented
                if (part == null && fallbackToUnaugmentedProperty && (propertyPrefix != null || propertySuffix != null)) {
                    log.debug("Property wth key [{}] not found, attempting with unaugmented key: {}", augmentedKey, key);
                    part = createPlaceholderPart(key, properties, replaced, prefixToken, suffixToken);
                }
                
                if (part == null) {
                    StringBuilder esb = new StringBuilder();
                    esb.append("Property with key [").append(augmentedKey).append("] ");
                    if (fallbackToUnaugmentedProperty && (propertyPrefix != null || propertySuffix != null)) {
                        esb.append("(and original key [").append(key).append("]) ");
                    }
                    esb.append("not found in properties from text: ").append(uri);
                    throw new IllegalArgumentException(esb.toString());
                }
                sb.append(part);
                pivot = endIdx + suffixToken.length();
            }
        }
        return sb.toString();
    }
    
    private int findTokenPosition(String uri, int pivot, String token) {
        int idx = uri.indexOf(token, pivot);
        while (idx > 0) {
            // grab part as the previous char + token + next char, to test if the token is quoted
            String part = null;
            int len = idx + token.length() + 1;
            if (uri.length() >= len) {
                part = uri.substring(idx - 1, len);
            }
            if (StringHelper.isQuoted(part)) {
                // the token was quoted, so regard it as a literal
                // and then try to find from next position
                pivot = idx + token.length() + 1;
                idx = uri.indexOf(token, pivot);
            } else {
                // found token
                return idx;
            }
        }
        return idx;
    }
    
    private boolean isNestProperty(String uri, String prefixToken, String suffixToken) {
        if (ObjectHelper.isNotEmpty(uri)) {
            uri = uri.trim();
            if (uri.startsWith(prefixToken) && uri.endsWith(suffixToken)) {
                return true;
            }
        }
        return false;
    }
    
    private String takeOffNestTokes(String uri, String prefixToken, String suffixToken) {
        int start = prefixToken.length(); 
        int end = uri.length() - suffixToken.length();
        return uri.substring(start, end); 
    }

    private String createConstantPart(String uri, int start, int end) {
        return uri.substring(start, end);
    }

    private String createPlaceholderPart(String key, Properties properties, List<String> replaced, String prefixToken, String suffixToken) {
        // keep track of which parts we have replaced
        replaced.add(key);
        
        String propertyValue = System.getProperty(key);
        if (propertyValue != null) {
            log.debug("Found a JVM system property: {} with value: {} to be used.", key, propertyValue);
        } else if (properties != null) {
            propertyValue = properties.getProperty(key);
        }
        
        // we need to check if the propertyValue is nested
        // we need to check if there is cycle dependency of the nested properties
        List<String> visited = new ArrayList<String>();
        while (isNestProperty(propertyValue, prefixToken, suffixToken)) {
            visited.add(key);
            // need to take off the token first
            String value = takeOffNestTokes(propertyValue, prefixToken, suffixToken);
            key = parseUri(value, properties, prefixToken, suffixToken);
            if (visited.contains(key)) {
                throw new IllegalArgumentException("Circular reference detected with key [" + key + "] from text: " + propertyValue);
            }
            propertyValue = System.getProperty(key);
            if (propertyValue != null) {
                log.debug("Found a JVM system property: {} with value: {} to be used.", key, propertyValue);
            } else if (properties != null) {
                propertyValue = properties.getProperty(key);
            }
        }

        return parseProperty(key, propertyValue, properties);
    }

}
