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
package org.apache.camel.component.properties;

import java.util.Iterator;
import java.util.List;

/**
 * Default {@link PropertiesLookup} which lookup properties from a {@link java.util.Properties} with all existing properties.
 */
public class DefaultPropertiesLookup implements PropertiesLookup {

    private final List<LocationPropertiesSource> locationSources;
    private final List<PropertiesSource> sources;

    public DefaultPropertiesLookup(List<LocationPropertiesSource> locationSources, List<PropertiesSource> sources) {
        this.locationSources = locationSources;
        this.sources = sources;
    }

    @Override
    public String lookup(String name) {
        String answer = null;
        // try till first found source
        Iterator<LocationPropertiesSource> it = locationSources.iterator();
        while (answer == null && it.hasNext()) {
            answer = it.next().getProperty(name);
        }
        if (answer == null && sources != null) {
            // try till first found source
            Iterator<PropertiesSource> it2 = sources.iterator();
            while (answer == null && it2.hasNext()) {
                answer = it2.next().getProperty(name);
            }
        }
        return answer;
    }
}
