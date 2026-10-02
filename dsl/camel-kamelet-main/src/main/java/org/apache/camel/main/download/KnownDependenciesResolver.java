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
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.tooling.maven.MavenGav;

public final class KnownDependenciesResolver {

    private final Map<String, String> mappings = new HashMap<>();
    // the package of each component, only used for the imports of a source (see mavenGavForImport)
    private final Map<String, String> componentPackages = new HashMap<>();
    private final CamelContext camelContext;
    private final String springBootVersion;
    private final String quarkusVersion;

    public KnownDependenciesResolver(CamelContext camelContext, String springBootVersion, String quarkusVersion) {
        this.camelContext = camelContext;
        this.springBootVersion = springBootVersion;
        this.quarkusVersion = quarkusVersion;
    }

    public void loadKnownDependencies() {
        doLoadKnownDependencies("camel-main-known-dependencies.properties", false);
        doLoadKnownDependencies("camel-component-known-dependencies.properties", true);
        // third-party libraries mapped by package, generated from src/main/known-third-party-libraries.properties
        doLoadKnownDependencies("camel-thirdparty-known-dependencies.properties", false);
    }

    public void loadKnownFactoryFinderDependencies() {
        doLoadKnownDependencies("camel-factoryfinder-known-dependencies.properties", false);
    }

    private void doLoadKnownDependencies(String name, boolean byPackage) {
        try {
            Enumeration<URL> resources = getClass().getClassLoader().getResources(name);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                try (InputStream is = resource.openStream()) {
                    Properties prop = new Properties();
                    prop.load(is);
                    Map<String, String> map = new HashMap<>();
                    for (String key : prop.stringPropertyNames()) {
                        String value = prop.getProperty(key);
                        map.put(key, value);
                    }
                    if (byPackage) {
                        addPackageMappings(map, componentPackages);
                    }
                    addMappings(map);
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }

    /**
     * The package of each class, unless the package is shared by classes of different dependencies, or is a base
     * package such as <tt>org.apache.camel</tt> that would match every Camel class.
     */
    private static void addPackageMappings(Map<String, String> classes, Map<String, String> packages) {
        Set<String> shared = new HashSet<>();
        for (Map.Entry<String, String> entry : classes.entrySet()) {
            String key = entry.getKey();
            int pos = key.lastIndexOf('.');
            if (pos == -1) {
                continue;
            }
            String pkg = key.substring(0, pos);
            if (pkg.chars().filter(ch -> ch == '.').count() < 3) {
                continue;
            }
            String existing = packages.putIfAbsent(pkg, entry.getValue());
            if (existing != null && !existing.equals(entry.getValue())) {
                shared.add(pkg);
            }
        }
        shared.forEach(packages::remove);
    }

    public void addMappings(Map<String, String> mappings) {
        this.mappings.putAll(mappings);
    }

    public MavenGav mavenGavForClass(String className) {
        return toMavenGav(findGav(mappings, className));
    }

    /**
     * The dependency of a class a source imports: as {@link #mavenGavForClass(String)}, and also any class of a Camel
     * component (such as the constants of its headers), not only the component class itself.
     * <p/>
     * Only for imports: a class that is looked up at runtime is often only probed for, which must not download a
     * component.
     */
    public MavenGav mavenGavForImport(String className) {
        String gav = findGav(mappings, className);
        if (gav == null) {
            gav = findGav(componentPackages, className);
        }
        return toMavenGav(gav);
    }

    private MavenGav toMavenGav(String gav) {
        MavenGav answer = null;
        if (gav != null) {
            answer = MavenGav.parseGav(gav, camelContext.getVersion());
        }
        if (answer != null) {
            String v = answer.getVersion();
            if (springBootVersion != null && "${spring-boot-version}".equals(v)) {
                answer.setVersion(springBootVersion);
            } else if (quarkusVersion != null && "${quarkus-version}".equals(v)) {
                answer.setVersion(quarkusVersion);
            }
        }
        return answer;
    }

    private static String findGav(Map<String, String> mappings, String prefix) {
        String gav = mappings.get(prefix);
        while (gav == null && prefix.lastIndexOf(".") != -1) {
            prefix = prefix.substring(0, prefix.lastIndexOf("."));
            gav = mappings.get(prefix);
        }
        return gav;
    }
}
