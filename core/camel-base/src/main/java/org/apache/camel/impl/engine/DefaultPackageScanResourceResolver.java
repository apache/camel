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
package org.apache.camel.impl.engine;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;

import org.apache.camel.CamelContextAware;
import org.apache.camel.NonManagedService;
import org.apache.camel.spi.PackageScanResourceResolver;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.AntPathMatcher;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.KeyValueHolder;
import org.apache.camel.util.ObjectHelper;

/**
 * Default implement of {@link org.apache.camel.spi.PackageScanResourceResolver}
 */
public class DefaultPackageScanResourceResolver extends BasePackageScanResolver implements PackageScanResourceResolver, NonManagedService, CamelContextAware {

    private static final AntPathMatcher PATH_MATCHER = AntPathMatcher.INSTANCE;

    @Override
    public Set<String> findResourceNames(String location) throws Exception {
        Set<KeyValueHolder<String, InputStream>> answer = new LinkedHashSet<>();
        doFindResources(location, answer);
        return answer.stream().map(KeyValueHolder::getKey).collect(Collectors.toSet());
    }

    public Set<InputStream> findResources(String location) throws Exception {
        Set<KeyValueHolder<String, InputStream>> answer = new LinkedHashSet<>();
        doFindResources(location, answer);
        return answer.stream().map(KeyValueHolder::getValue).collect(Collectors.toSet());
    }

    protected void doFindResources(String location, Set<KeyValueHolder<String, InputStream>> resources) throws Exception {
        // if its a pattern then we need to scan its root path and find
        // all matching resources using the sub pattern
        if (PATH_MATCHER.isPattern(location)) {
            String root = PATH_MATCHER.determineRootDir(location);
            String subPattern = location.substring(root.length());

            String scheme = ResourceHelper.getScheme(location);
            if ("file:".equals(scheme)) {
                // file based scanning
                root = root.substring(scheme.length());
                findInFileSystem(new File(root), resources, subPattern);
            } else {
                if ("classpath:".equals(scheme)) {
                    root = root.substring(scheme.length());
                }
                // assume classpath based scan from root path and find all resources
                findInClasspath(root, resources, subPattern);
            }
        } else {
            // its a single resource so load it directly
            InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), location);
            resources.add(new KeyValueHolder<>(location, is));
        }
    }

    protected void findInFileSystem(File dir, Set<KeyValueHolder<String, InputStream>> resources, String subPattern) throws Exception {
        ResourceHelper.findInFileSystem(dir.toPath(), subPattern).forEach(f -> {
            try {
                String location = f.toString();
                resources.add(new KeyValueHolder<>(location, Files.newInputStream(f)));
            } catch (IOException e) {
                // ignore
            }
        });
    }

    protected void findInClasspath(String packageName, Set<KeyValueHolder<String, InputStream>> resources, String subPattern) {
        packageName = packageName.replace('.', '/');
        // If the URL is a jar, the URLClassloader.getResources() seems to require a trailing slash.
        // The trailing slash is harmless for other URLs
        if (!packageName.endsWith("/")) {
            packageName = packageName + "/";
        }

        Set<ClassLoader> set = getClassLoaders();

        for (ClassLoader classLoader : set) {
            doFind(packageName, classLoader, resources, subPattern);
        }
    }

    protected void doFind(String packageName, ClassLoader classLoader, Set<KeyValueHolder<String, InputStream>> resources, String subPattern) {
        Enumeration<URL> urls;
        try {
            urls = getResources(classLoader, packageName);
            if (!urls.hasMoreElements()) {
                log.trace("No URLs returned by classloader");
            }
        } catch (IOException ioe) {
            log.warn("Cannot read package: {}", packageName, ioe);
            return;
        }

        while (urls.hasMoreElements()) {
            URL url = null;
            try {
                url = urls.nextElement();
                log.trace("URL from classloader: {}", url);

                url = customResourceLocator(url);

                String urlPath = url.getFile();
                urlPath = URLDecoder.decode(urlPath, "UTF-8");
                if (log.isTraceEnabled()) {
                    log.trace("Decoded urlPath: {} with protocol: {}", urlPath, url.getProtocol());
                }

                // If it's a file in a directory, trim the stupid file: spec
                if (urlPath.startsWith("file:")) {
                    // file path can be temporary folder which uses characters that the URLDecoder decodes wrong
                    // for example + being decoded to something else (+ can be used in temp folders on Mac OS)
                    // to remedy this then create new path without using the URLDecoder
                    try {
                        urlPath = new URI(url.getFile()).getPath();
                    } catch (URISyntaxException e) {
                        // fallback to use as it was given from the URLDecoder
                        // this allows us to work on Windows if users have spaces in paths
                    }

                    if (urlPath.startsWith("file:")) {
                        urlPath = urlPath.substring(5);
                    }
                }

                // osgi bundles should be skipped
                if (url.toString().startsWith("bundle:") || urlPath.startsWith("bundle:")) {
                    log.trace("Skipping OSGi bundle: {}", url);
                    continue;
                }

                // bundle resource should be skipped
                if (url.toString().startsWith("bundleresource:") || urlPath.startsWith("bundleresource:")) {
                    log.trace("Skipping bundleresource: {}", url);
                    continue;
                }

                // Else it's in a JAR, grab the path to the jar
                if (urlPath.indexOf('!') > 0) {
                    urlPath = urlPath.substring(0, urlPath.indexOf('!'));
                }

                log.trace("Scanning for resources in: {} matching pattern: {}", urlPath, subPattern);

                File file = new File(urlPath);
                if (file.isDirectory()) {
                    log.trace("Loading from directory using file: {}", file);
                    loadImplementationsInDirectory(subPattern, packageName, file, resources);
                } else {
                    InputStream stream;
                    if (urlPath.startsWith("http:") || urlPath.startsWith("https:")
                            || urlPath.startsWith("sonicfs:")
                            || isAcceptableScheme(urlPath)) {
                        // load resources using http/https, sonicfs and other acceptable scheme
                        // sonic ESB requires to be loaded using a regular URLConnection
                        log.trace("Loading from jar using url: {}", urlPath);
                        URL urlStream = new URL(urlPath);
                        URLConnection con = urlStream.openConnection();
                        // disable cache mainly to avoid jar file locking on Windows
                        con.setUseCaches(false);
                        stream = con.getInputStream();
                    } else {
                        log.trace("Loading from jar using file: {}", file);
                        stream = new FileInputStream(file);
                    }

                    loadImplementationsInJar(packageName, subPattern, stream, urlPath, resources);
                }
            } catch (IOException e) {
                // use debug logging to avoid being to noisy in logs
                log.debug("Cannot read entries in url: {}", url, e);
            }
        }
    }

    /**
     * Finds matching classes within a jar files that contains a folder
     * structure matching the package structure. If the File is not a JarFile or
     * does not exist a warning will be logged, but no error will be raised.
     *
     * @param stream  the inputstream of the jar file to be examined for classes
     * @param urlPath the url of the jar file to be examined for classes
     */
    private void loadImplementationsInJar(String packageName, String subPattern, InputStream stream,
                                          String urlPath, Set<KeyValueHolder<String, InputStream>> resources) {
        List<String> entries = new ArrayList<>();

        JarInputStream jarStream = null;
        try {
            jarStream = new JarInputStream(stream);

            JarEntry entry;
            while ((entry = jarStream.getNextJarEntry()) != null) {
                String name = entry.getName();
                if (name != null) {
                    name = name.trim();
                    if (!entry.isDirectory() && !name.endsWith(".class")) {
                        // name is FQN so it must start with package name
                        if (name.startsWith(packageName)) {
                            entries.add(name);
                        }
                    }
                }
            }
        } catch (IOException ioe) {
            log.warn("Cannot search jar file '" + urlPath + " due to an IOException: " + ioe.getMessage(), ioe);
        } finally {
            IOHelper.close(jarStream, urlPath, log);
        }

        for (String name : entries) {
            String shortName = name.substring(packageName.length());
            boolean match = PATH_MATCHER.match(subPattern, shortName);
            log.debug("Found resource: {} matching pattern: {} -> {}", shortName, subPattern, match);
            if (match) {
                // use fqn name to load resource
                InputStream is = getCamelContext().getClassResolver().loadResourceAsStream(name);
                if (is != null) {
                    resources.add(new KeyValueHolder<>(name, is));
                }
            }
        }
    }

    /**
     * Finds matches in a physical directory on a filesystem. Examines all files
     * within a directory - if the File object is not a directory, and ends with
     * <i>.class</i> the file is loaded and tested to see if it is acceptable
     * according to the Test. Operates recursively to find classes within a
     * folder structure matching the package structure.
     *
     * @param parent   the package name up to this directory in the package
     *                 hierarchy. E.g. if /classes is in the classpath and we wish to
     *                 examine files in /classes/org/apache then the values of
     *                 <i>parent</i> would be <i>org/apache</i>
     * @param location a File object representing a directory
     */
    private void loadImplementationsInDirectory(String subPattern, String parent, File location, Set<KeyValueHolder<String, InputStream>> resources) throws FileNotFoundException {
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
                loadImplementationsInDirectory(subPattern, packageOrClass, file, resources);
            } else if (file.isFile() && file.exists() && !name.endsWith(".class")) {
                boolean match = PATH_MATCHER.match(subPattern, name);
                log.debug("Found resource: {} matching pattern: {} -> {}", name, subPattern, match);
                if (match) {
                    InputStream is = new FileInputStream(file);
                    resources.add(new KeyValueHolder<>(name, is));
                }
            }
        }
    }

    @Override
    protected void doInit() throws Exception {
        ObjectHelper.notNull(getCamelContext(), "CamelContext", this);
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
