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
package org.apache.camel.dsl.yaml.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.dsl.yaml.common.exception.YamlDeserializationException;

/**
 * Default YAML deserializer resolver provider that discovers resolver class names from the classpath.
 */
public class DefaultYamlDeserializerResolverProvider implements YamlDeserializerResolverProvider {

    @Override
    public Map<String, YamlDeserializerResolver> findResolvers(CamelContext camelContext) {
        Map<String, YamlDeserializerResolver> resolvers = new LinkedHashMap<>();
        for (String resolverClassName : findResolverClassNames(camelContext)) {
            resolvers.put(resolverClassName, newResolver(camelContext, resolverClassName));
        }
        return resolvers;
    }

    private YamlDeserializerResolver newResolver(CamelContext camelContext, String resolverClassName) {
        try {
            Class<YamlDeserializerResolver> resolverType
                    = camelContext.getClassResolver().resolveMandatoryClass(resolverClassName,
                            YamlDeserializerResolver.class);
            return camelContext.getInjector().newInstance(resolverType, false);
        } catch (Exception e) {
            String message = "Error loading YAML deserializer resolver " + resolverClassName + " from "
                             + YamlDeserializerResolver.RESOURCE_PATH;
            throw new YamlDeserializationException(message, e);
        }
    }

    private List<String> findResolverClassNames(CamelContext camelContext) {
        Set<String> resolverClassNames = new LinkedHashSet<>();

        for (URL resolverResource : findResolverResources(camelContext)) {
            resolverClassNames.addAll(readResolverClassNames(resolverResource));
        }

        List<String> sortedResolverClassNames = new ArrayList<>(resolverClassNames);
        sortedResolverClassNames.sort(String::compareTo);
        return sortedResolverClassNames;
    }

    private List<String> readResolverClassNames(URL resolverResource) {
        List<String> resolverClassNames = new ArrayList<>();

        try (BufferedReader reader
                = new BufferedReader(new InputStreamReader(resolverResource.openStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String resolverClassName = parseResolverClassName(line);
                if (!resolverClassName.isEmpty()) {
                    resolverClassNames.add(resolverClassName);
                }
            }
        } catch (IOException e) {
            throw new YamlDeserializationException(
                    "Error reading YAML deserializer resolver resource from "
                                                   + YamlDeserializerResolver.RESOURCE_PATH + " ("
                                                   + safeResourceDescription(resolverResource) + ")",
                    e);
        }

        return resolverClassNames;
    }

    private static String safeResourceDescription(URL resolverResource) {
        if (resolverResource == null || resolverResource.getProtocol() == null) {
            return "unknown resource";
        }
        return resolverResource.getProtocol() + " resource";
    }

    private static String parseResolverClassName(String line) {
        int comment = line.indexOf('#');
        String uncommentedLine = comment != -1 ? line.substring(0, comment) : line;
        return uncommentedLine.trim();
    }

    private Set<URL> findResolverResources(CamelContext camelContext) {
        Set<URL> resolverResources = new LinkedHashSet<>();

        try {
            addResolverResources(resolverResources,
                    camelContext.getClassResolver().loadAllResourcesAsURL(YamlDeserializerResolver.RESOURCE_PATH));
            addResolverResources(resolverResources, camelContext.getApplicationContextClassLoader());
            for (ClassLoader classLoader : camelContext.getClassResolver().getClassLoaders()) {
                addResolverResources(resolverResources, classLoader);
            }
        } catch (IOException e) {
            throw new YamlDeserializationException(
                    "Error locating YAML deserializer resolvers from " + YamlDeserializerResolver.RESOURCE_PATH, e);
        }

        return resolverResources;
    }

    private static void addResolverResources(Set<URL> resolverResources, ClassLoader classLoader) throws IOException {
        if (classLoader != null) {
            addResolverResources(resolverResources, classLoader.getResources(YamlDeserializerResolver.RESOURCE_PATH));
        }
    }

    private static void addResolverResources(Set<URL> resolverResources, Enumeration<URL> resources) {
        if (resources != null) {
            while (resources.hasMoreElements()) {
                resolverResources.add(resources.nextElement());
            }
        }
    }
}
