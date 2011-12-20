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

import java.util.Properties;

/**
 * A parser to parse properties for a given input
 */
public interface PropertiesParser {

    /**
     * Parses the string and replaces the property placeholders with values from the given properties.
     *
     * @param text        the text to be parsed
     * @param properties  the properties resolved which values should be looked up
     * @param prefixToken the prefix token
     * @param suffixToken the suffix token
     * @return the parsed text with replaced placeholders
     * @throws IllegalArgumentException if uri syntax is not valid or a property is not found
     */
    String parseUri(String text, Properties properties, String prefixToken, String suffixToken) throws IllegalArgumentException;

    /**
     * While parsing the uri using {@link #parseUri(String, java.util.Properties, String, String) parseUri} each
     * parsed property found invokes this callback.
     * <p/>
     * This strategy method allows you to hook into the parsing and do custom lookup and return the actual value to use.
     *
     * @param key        the key
     * @param value      the value
     * @param properties the properties resolved which values should be looked up
     * @return the value to use
     */
    String parseProperty(String key, String value, Properties properties);
}
