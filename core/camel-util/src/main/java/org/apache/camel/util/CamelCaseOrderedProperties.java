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
package org.apache.camel.util;

import java.util.Properties;

/**
 * This class is a camelCase ordered {@link Properties} where the key/values are stored in the order they are added or
 * loaded.
 * <p/>
 * The keys are stored in the original case, for example a key of <code>camel.main.stream-caching-enabled</code> is
 * stored as <code>camel.main.stream-caching-enabled</code>.
 * <p/>
 * However the lookup of a value by key with the <tt>get</tt> methods, will support camelCase or dash style.
 * <p/>
 * Note: This implementation is only intended as implementation detail for Camel tooling such as camel-jbang, and has
 * only been designed to provide the needed functionality. The complex logic for loading properties has been kept from
 * the JDK {@link Properties} class.
 */
public final class CamelCaseOrderedProperties extends BaseOrderedProperties {

    @Override
    public synchronized Object get(Object key) {
        return getProperty(key.toString());
    }

    @Override
    public String getProperty(String key) {
        String answer = super.getProperty(key);
        if (answer == null) {
            answer = super.getProperty(StringHelper.dashToCamelCase(key));
        }
        if (answer == null) {
            answer = super.getProperty(StringHelper.camelCaseToDash(key));
        }
        return answer;
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        String answer = getProperty(key);
        if (answer == null) {
            answer = defaultValue;
        }
        return answer;
    }

}
