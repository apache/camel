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

import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.camel.impl.engine.SimpleCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CAMEL-25833: a dependency added to camel.jbang.dependencies while the application runs in dev mode is downloaded in
 * the reload, as the dependencies are when the application starts.
 */
class ReloadDependencyDownloaderTest {

    private static final String[] KEYS = { "camel.jbang.dependencies", "camel.jbang.dependencies.main" };

    private final List<String> downloaded = new ArrayList<>();

    @Test
    void theDependenciesThatAreNotOnTheClasspathAreDownloaded() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("camel.jbang.dependencies", "com.github.javafaker:javafaker:1.0.2, org.acme:loaded:1.0,");
        properties.setProperty("camel.jbang.dependencies.main", "org.acme:main-only:2.0");
        properties.setProperty("camel.beans.faker", "#class:com.github.javafaker.Faker");

        List<String> restart = download(properties, Set.of("org.acme:loaded:1.0"));

        assertEquals(List.of("com.github.javafaker:javafaker:1.0.2", "org.acme:main-only:2.0"), downloaded);
        assertEquals(List.of(), restart);
    }

    @Test
    void aLibraryOnTheClasspathInAnotherVersionIsNotDownloaded() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("camel.jbang.dependencies", "com.github.javafaker:javafaker:1.0.2");

        List<String> restart = download(properties, Set.of(),
                URI.create("file:/repo/com/github/javafaker/javafaker/1.0.1/javafaker-1.0.1.jar").toURL());

        assertEquals(List.of(), downloaded);
        assertEquals(List.of("com.github.javafaker:javafaker:1.0.2"), restart);
    }

    @Test
    void propertiesWithoutDependenciesDownloadNothing() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("camel.component.log.level", "WARN");

        List<String> restart = download(properties, Set.of());

        assertEquals(List.of(), downloaded);
        assertEquals(List.of(), restart);
    }

    private List<String> download(Properties properties, Set<String> onClasspath, URL... loaded) throws Exception {
        try (SimpleCamelContext context = new SimpleCamelContext();
             URLClassLoader classLoader = new URLClassLoader(loaded, null)) {
            context.addService(recordingDownloader(context, onClasspath, classLoader));
            return new ReloadDependencyDownloader(context).download(properties, KEYS);
        }
    }

    // records the downloads; the given GAVs are on the classpath, and the class loader has the JARs downloaded before
    private DependencyDownloader recordingDownloader(
            SimpleCamelContext context, Set<String> onClasspath, ClassLoader classLoader) {
        return (DependencyDownloader) Proxy.newProxyInstance(
                ReloadDependencyDownloaderTest.class.getClassLoader(),
                new Class[] { DependencyDownloader.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "hashCode" -> {
                            return System.identityHashCode(proxy);
                        }
                        case "equals" -> {
                            return proxy == args[0];
                        }
                        case "toString" -> {
                            return "RecordingDependencyDownloader";
                        }
                        case "downloadDependency" -> {
                            downloaded.add(args[0] + ":" + args[1] + ":" + args[2]);
                            return null;
                        }
                        case "alreadyOnClasspath" -> {
                            return onClasspath.contains(args[0] + ":" + args[1] + ":" + args[2]);
                        }
                        case "getClassLoader" -> {
                            return classLoader;
                        }
                        case "getCamelContext" -> {
                            return context;
                        }
                        default -> {
                            return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
                        }
                    }
                });
    }
}
