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
 * Interface for property parses that can attempt parsing property names using a fixed property name prefix and suffix.
 */
public interface AugmentedPropertyNameAwarePropertiesParser extends PropertiesParser {
    
    /**
     * Parses the string, applying the optional {@code propertyPrefix} and
     * {@code propertySuffix} to the parsed property names, and replaces the
     * property placeholders with values from the given properties.
     * 
     * @param text the text to be parsed
     * @param properties the properties resolved which values should be looked
     *            up
     * @param prefixToken the prefix token
     * @param suffixToken the suffix token
     * @param propertyPrefix the optional property name prefix to augment parsed
     *            property names with
     * @param propertySuffix the optional property name suffix to augment parsed
     *            property names with
     * @param fallbackToUnaugmentedProperty flag indicating if the originally
     *            parsed property name should by used for resolution if there is
     *            no match to the augmented property name
     * @param defaultFallbackEnabled flag indicating if the value after colon
     *                               should be the default value to use it
     *                               the property has not been resolved
     *            
     * @return the parsed text with replaced placeholders
     *
     * @throws IllegalArgumentException if uri syntax is not valid or a property
     *             is not found
     */
    String parseUri(String text, Properties properties, String prefixToken, String suffixToken,
                    String propertyPrefix, String propertySuffix, boolean fallbackToUnaugmentedProperty, boolean defaultFallbackEnabled) throws IllegalArgumentException;
}
