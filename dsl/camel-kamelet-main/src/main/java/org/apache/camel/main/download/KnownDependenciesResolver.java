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
package org.apache.camel.main.download;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.tooling.maven.MavenGav;

public final class KnownDependenciesResolver {

    private final Map<String, String> mappings = new HashMap<>();
    private final CamelContext camelContext;

    public KnownDependenciesResolver(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public void loadKnownDependencies() {
        doLoadKnownDependencies("/camel-main-known-dependencies.properties");
        doLoadKnownDependencies("/camel-component-known-dependencies.properties");
    }

    private void doLoadKnownDependencies(String name) {
        try {
            InputStream is = getClass().getResourceAsStream(name);
            if (is != null) {
                Properties prop = new Properties();
                prop.load(is);
                Map<String, String> map = new HashMap<>();
                for (String key : prop.stringPropertyNames()) {
                    String value = prop.getProperty(key);
                    map.put(key, value);
                }
                addMappings(map);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    public void addMappings(Map<String, String> mappings) {
        this.mappings.putAll(mappings);
    }

    public MavenGav mavenGavForClass(String className) {
        String gav = mappings.get(className);
        if (gav != null) {
            return MavenGav.parseGav(gav, camelContext.getVersion());
        }
        return null;
    }
}
