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
package org.apache.camel.support.scan;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.apache.camel.NonManagedService;
import org.apache.camel.spi.PackageScanResourceResolver;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceLoader;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.AntPathMatcher;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implement of {@link org.apache.camel.spi.PackageScanResourceResolver}
 */
public class DefaultPackageScanResourceResolver extends BasePackageScanResolver
        implements PackageScanResourceResolver, NonManagedService {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultPackageScanResourceResolver.class);

    private static final AntPathMatcher PATH_MATCHER = AntPathMatcher.INSTANCE;

    @Override
    public Collection<Resource> findResources(String location) throws Exception {
        return findResources(location, null);
    }

    @Override
    public Collection<Resource> findResources(String location, Predicate<String> filter) throws Exception {
        Set<Resource> answer = new HashSet<>();
        doFindResources(location, answer, filter);
        return answer;
    }

    protected void doFindResources(String location, Set<Resource> resources, Predicate<String> filter)
            throws Exception {
        // if its a pattern then we need to scan its root path and find
        // all matching resources using the sub pattern
        if (PATH_MATCHER.isPattern(location)) {
            String root = PATH_MATCHER.determineRootDir(location);
            String subPattern = location.substring(root.length());
            String scheme = ResourceHelper.getScheme(location);
            if ("file:".equals(scheme)) {
                // file based scanning
                root = root.substring(scheme.length());
                findInFileSystem(new File(root), resources, subPattern, filter);
            } else {
                if ("classpath:".equals(scheme)) {
                    root = root.substring(scheme.length());
                }
                // assume classpath based scan from root path and find all resources
                findInClasspath(root, resources, subPattern, filter);
            }
        } else {
            final ResourceLoader loader = PluginHelper.getResourceLoader(getCamelContext());
            // its a single resource so load it directly
            resources.add(loader.resolveResource(location));
        }
    }

    protected void findInFileSystem(
            File dir,
            Set<Resource> resources,
            String subPattern,
            Predicate<String> filter)
            throws Exception {

        final ResourceLoader loader = PluginHelper.getResourceLoader(getCamelContext());
        for (Path path : ResourceHelper.findInFileSystem(dir.toPath(), subPattern)) {
            String name = path.toString();
            boolean accept = filter == null || filter.test(name);
            if (accept) {
                resources.add(loader.resolveResource("file:" + name));
            }
        }
    }

    protected void findInClasspath(
            String packageName,
            Set<Resource> resources,
            String subPattern,
            Predicate<String> filter) {

        // special for root package
        if (".".equals(packageName)) {
            packageName = "";
        } else {
            packageName = packageName.replace('.', '/');
        }

        // If the URL is a jar, the URLClassloader.getResources() seems to require a trailing slash.
        // The trailing slash is harmless for other URLs
        if (!packageName.isEmpty() && !packageName.endsWith("/")) {
            packageName = packageName + "/";
        }

        Set<ClassLoader> set = getClassLoaders();
        for (ClassLoader classLoader : set) {
            doFind(packageName, classLoader, resources, subPattern, filter);
        }
    }

    protected void doFind(
            String packageName,
            ClassLoader classLoader,
            Set<Resource> resources,
            String subPattern,
            Predicate<String> filter) {

        Enumeration<URL> urls = getUrls(packageName, classLoader);
        if (urls == null) {
            return;
        }

        while (urls.hasMoreElements()) {
            URL url = null;
            try {
                url = urls.nextElement();
                LOG.trace("URL from classloader: {}", url);

                url = customResourceLocator(url);

                String urlPath = parseUrlPath(url);
                if (urlPath == null) {
                    continue;
                }

                LOG.trace("Scanning for resources in: {} matching pattern: {}", urlPath, subPattern);

                File file = new File(urlPath);
                if (file.isDirectory()) {
                    LOG.trace("Loading from directory using file: {}", file);
                    loadImplementationsInDirectory(subPattern, packageName, file, resources, filter);
                } else {
                    InputStream stream;
                    if (urlPath.startsWith("http:") || urlPath.startsWith("https:")
                            || urlPath.startsWith("sonicfs:")
                            || isAcceptableScheme(urlPath)) {
                        // load resources using http/https, sonicfs and other acceptable scheme
                        // sonic ESB requires to be loaded using a regular URLConnection
                        LOG.trace("Loading from jar using url: {}", urlPath);
                        URL urlStream = URI.create(urlPath).toURL();
                        URLConnection con = urlStream.openConnection();
                        // disable cache mainly to avoid jar file locking on Windows
                        con.setUseCaches(false);
                        stream = con.getInputStream();
                    } else {
                        LOG.trace("Loading from jar using file: {}", file);
                        stream = new FileInputStream(file);
                    }
                    loadImplementationsInJar(url, packageName, subPattern, stream, urlPath, resources, filter);
                }
            } catch (IOException e) {
                // use debug logging to avoid being to noisy in logs
                LOG.debug("Cannot read entries in url: {}", url, e);
            }
        }
    }

    /**
     * Finds matching classes within a jar files that contains a folder structure matching the package structure. If the
     * File is not a JarFile or does not exist a warning will be logged, but no error will be raised.
     *
     * @param packageName the root package name
     * @param subPattern  optional pattern to use for matching resource names
     * @param stream      the input stream of the jar file to be examined for classes
     * @param urlPath     the url of the jar file to be examined for classes
     * @param resources   the list to add loaded resources
     */
    protected void loadImplementationsInJar(
            URL url,
            String packageName,
            String subPattern,
            InputStream stream,
            String urlPath,
            Set<Resource> resources,
            Predicate<String> filter) {

        List<String> entries = doLoadImplementationsInJar(packageName, stream, urlPath, filter);
        for (String name : entries) {
            String shortName = name.substring(packageName.length());
            boolean match = PATH_MATCHER.match(subPattern, shortName);
            LOG.debug("Found resource: {} matching pattern: {} -> {}", shortName, subPattern, match);
            if (match) {
                Resource resource = new PackageScanJarResource("jar", url, name);
                resources.add(resource);
            }
        }
    }

    protected List<String> doLoadImplementationsInJar(
            String packageName,
            InputStream stream,
            String urlPath,
            Predicate<String> filter) {

        List<String> entries = new ArrayList<>();

        JarInputStream jarStream = null;
        try {
            jarStream = new JarInputStream(stream);

            JarEntry entry;
            while ((entry = jarStream.getNextJarEntry()) != null) {
                final String name = entry.getName().trim();
                if (!entry.isDirectory()) {
                    boolean accept;
                    if (filter != null) {
                        // use filter to accept or not
                        accept = filter.test(name);
                    } else {
                        // skip class files by default
                        accept = !name.endsWith(".class");
                    }
                    if (accept) {
                        // name is FQN so it must start with package name
                        if (name.startsWith(packageName)) {
                            entries.add(name);
                        }
                    }
                }

            }
        } catch (IOException ioe) {
            LOG.warn("Cannot search jar file '{} due to an IOException: {}", urlPath, ioe.getMessage(), ioe);
        } finally {
            IOHelper.close(jarStream, urlPath, LOG);
        }

        return entries;
    }

    private void loadImplementationsInDirectory(
            String subPattern,
            String parent,
            File location,
            Set<Resource> resources,
            Predicate<String> filter) {
        File[] files = location.listFiles();
        if (files == null || files.length == 0) {
            return;
        }

        StringBuilder builder;
        for (File file : files) {
            builder = new StringBuilder(100);
            String name = file.getName();
            name = name.trim();
            builder.append(parent).append("/").append(name);
            String packageOrClass = parent == null ? name : builder.toString();

            if (file.isDirectory()) {
                loadImplementationsInDirectory(subPattern, packageOrClass, file, resources, filter);
            } else if (file.isFile() && file.exists()) {
                boolean accept;
                if (filter != null) {
                    // use filter to accept or not
                    accept = filter.test(name);
                } else {
                    // skip class files by default
                    accept = !name.endsWith(".class");
                }
                if (accept) {
                    boolean match = PATH_MATCHER.match(subPattern, name);
                    LOG.debug("Found resource: {} matching pattern: {} -> {}", name, subPattern, match);
                    if (match) {
                        final ResourceLoader loader = PluginHelper.getResourceLoader(getCamelContext());
                        resources.add(loader.resolveResource("file:" + file.getPath()));
                    }
                }
            }
        }
    }

}
