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
package org.apache.camel.itest.springboot.arquillian;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.boot.loader.JarLauncher;
import org.springframework.boot.loader.LaunchedURLClassLoader;
import org.springframework.boot.loader.MainMethodRunner;
/**
 * A Spring-boot jar launcher that uses the current thread instead of creating a new thread for spring-boot.
 */
public class ArquillianSyncBootJarLauncher extends JarLauncher {

    private ClassLoader classLoader;

    public ArquillianSyncBootJarLauncher() {
    }

    public void run(String[] args) throws Exception {
        this.launch(args);
    }

    @Override
    protected void launch(String[] args, String mainClass, ClassLoader classLoader) throws Exception {
        this.classLoader = classLoader;

        MainMethodRunner runner = createMainMethodRunner(mainClass, args, classLoader);

        Thread.currentThread().setContextClassLoader(classLoader);
        runner.run();
    }

    @Override
    protected ClassLoader createClassLoader(URL[] urls) throws Exception {
        // The spring classloader should not be built on top of the current classloader, it should just share the test classes if available
        List<URL> parentUrls = Arrays.asList(urlsFromClassLoader(this.getClassLoader()));
        List<URL> additionalURLs = parentUrls.stream().filter(u -> u.toString().startsWith("file") && !u.toString().endsWith(".jar")).collect(Collectors.toList());

        List<URL> newURLs = Arrays.asList(urls);
        newURLs.addAll(additionalURLs);

        ClassLoader appClassLoader = null;
        // Until https://github.com/spring-projects/spring-boot/issues/12832 is resolved
        if (getJavaMajorVersion() >= 9) {
            // use the Platform classloader to resolve classes on the module path
            appClassLoader = getClass().getClassLoader().getParent();
        }
        LaunchedURLClassLoader launchedURLClassLoader = new LaunchedURLClassLoader(newURLs.toArray(new URL[0]), appClassLoader);
        return launchedURLClassLoader;
    }

    /**
     * Returns the classloader used by spring, to communicate with it.
     *
     * @return the spring classloader
     */
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    private static int getJavaMajorVersion() {
        String javaSpecVersion = System.getProperty("java.specification.version");
        if (javaSpecVersion.contains(".")) { //before jdk 9
            return Integer.parseInt(javaSpecVersion.split("\\.")[1]);
        } else {
            return Integer.parseInt(javaSpecVersion);
        }
    }

    private static URL[] urlsFromClassLoader(ClassLoader classLoader) {
        if (classLoader instanceof URLClassLoader) {
            return ((URLClassLoader) classLoader).getURLs();
        }
        return Stream
                .of(ManagementFactory.getRuntimeMXBean().getClassPath()
                        .split(File.pathSeparator))
                .map(ArquillianSyncBootJarLauncher::toURL).toArray(URL[]::new);
    }

    private static URL toURL(String classPathEntry) {
        try {
            return new File(classPathEntry).toURI().toURL();
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException(
                    "URL could not be created from '" + classPathEntry + "'", ex);
        }
    }
}