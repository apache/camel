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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A parser to parse a string which contains property placeholders
 *
 * @version 
 */
public class DefaultPropertiesParser implements PropertiesParser {
    protected final transient Logger log = LoggerFactory.getLogger(getClass());
    
    public String parseUri(String text, Properties properties, String prefixToken, String suffixToken) throws IllegalArgumentException {
        String answer = text;
        boolean done = false;

        // the placeholders can contain nested placeholders so we need to do recursive parsing
        // we must therefore also do circular reference check and must keep a list of visited keys
        List<String> visited = new ArrayList<String>();
        while (!done) {
            List<String> replaced = new ArrayList<String>();
            answer = doParseUri(answer, properties, replaced, prefixToken, suffixToken);

            // check the replaced with the visited to avoid circular reference
            for (String replace : replaced) {
                if (visited.contains(replace)) {
                    throw new IllegalArgumentException("Circular reference detected with key [" + replace + "] from text: " + text);
                }
            }
            // okay all okay so add the replaced as visited
            visited.addAll(replaced);

            // are we done yet
            done = !answer.contains(prefixToken);
        }
        return answer;
    }

    public String parseProperty(String key, String value, Properties properties) {
        return value;
    }

    private String doParseUri(String uri, Properties properties, List<String> replaced, String prefixToken, String suffixToken) {
        StringBuilder sb = new StringBuilder();

        int pivot = 0;
        int size = uri.length();
        while (pivot < size) {
            int idx = uri.indexOf(prefixToken, pivot);
            if (idx < 0) {
                sb.append(createConstantPart(uri, pivot, size));
                break;
            } else {
                if (pivot < idx) {
                    sb.append(createConstantPart(uri, pivot, idx));
                }
                pivot = idx + prefixToken.length();
                int endIdx = uri.indexOf(suffixToken, pivot);
                if (endIdx < 0) {
                    throw new IllegalArgumentException("Expecting " + suffixToken + " but found end of string from text: " + uri);
                }
                String key = uri.substring(pivot, endIdx);

                String part = createPlaceholderPart(key, properties, replaced);
                if (part == null) {
                    throw new IllegalArgumentException("Property with key [" + key + "] not found in properties from text: " + uri);
                }
                sb.append(part);
                pivot = endIdx + suffixToken.length();
            }
        }
        return sb.toString();
    }

    private String createConstantPart(String uri, int start, int end) {
        return uri.substring(start, end);
    }

    private String createPlaceholderPart(String key, Properties properties, List<String> replaced) {
        // keep track of which parts we have replaced
        replaced.add(key);
        
        String propertyValue = System.getProperty(key);
        if (propertyValue != null) {
            log.debug("Found a JVM system property: {} with value: {} to be used.", key, propertyValue);
        } else if (properties != null) {
            propertyValue = properties.getProperty(key);
        }

        return parseProperty(key, propertyValue, properties);
    }

}
