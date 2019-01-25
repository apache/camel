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
package org.apache.camel.support;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PropertyPlaceholdersHelper {

    private static final Logger LOG = LoggerFactory.getLogger(PropertyPlaceholdersHelper.class);

    private PropertyPlaceholdersHelper() {
    }

    /**
     * Inspects the given definition and resolves any property placeholders from its properties.
     * <p/>
     * This implementation will check all the getter/setter pairs on this instance and for all the values
     * (which is a String type) will be property placeholder resolved.
     *
     * @param camelContext the Camel context
     * @param object   the object
     * @throws Exception is thrown if property placeholders was used and there was an error resolving them
     * @see CamelContext#resolvePropertyPlaceholders(String)
     * @see org.apache.camel.spi.PropertiesComponent
     */
    public static void resolvePropertyPlaceholders(CamelContext camelContext, Object object) throws Exception {
        LOG.trace("Resolving property placeholders for: {}", object);

        // find all getter/setter which we can use for property placeholders
        Map<String, Object> properties = new HashMap<>();
        IntrospectionSupport.getProperties(object, properties, null);

        if (!properties.isEmpty()) {
            LOG.trace("There are {} properties on: {}", properties.size(), object);
            // lookup and resolve properties for String based properties
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                // the name is always a String
                String name = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof String) {
                    // value must be a String, as a String is the key for a property placeholder
                    String text = (String) value;
                    text = camelContext.resolvePropertyPlaceholders(text);
                    if (text != value) {
                        // invoke setter as the text has changed
                        boolean changed = IntrospectionSupport.setProperty(camelContext.getTypeConverter(), object, name, text);
                        if (!changed) {
                            throw new IllegalArgumentException("No setter to set property: " + name + " to: " + text + " on: " + object);
                        }
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Changed property [{}] from: {} to: {}", name, value, text);
                        }
                    }
                }
            }
        }
    }

}
