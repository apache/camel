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
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.apache.camel.NonManagedService;
import org.apache.camel.impl.scan.AnnotatedWithAnyPackageScanFilter;
import org.apache.camel.impl.scan.AnnotatedWithPackageScanFilter;
import org.apache.camel.impl.scan.AssignableToPackageScanFilter;
import org.apache.camel.impl.scan.CompositePackageScanFilter;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.PackageScanFilter;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Default implement of {@link org.apache.camel.spi.PackageScanClassResolver}
 */
public class DefaultPackageScanClassResolver extends BasePackageScanResolver implements PackageScanClassResolver, NonManagedService {

    private volatile Map<String, List<String>> jarCache;
    private Set<PackageScanFilter> scanFilters;

    @Override
    public void addFilter(PackageScanFilter filter) {
        if (scanFilters == null) {
            scanFilters = new LinkedHashSet<>();
        }
        scanFilters.add(filter);
    }

    @Override
    public void removeFilter(PackageScanFilter filter) {
        if (scanFilters != null) {
            scanFilters.remove(filter);
        }
    }

    @Override
    public Set<Class<?>> findAnnotated(Class<? extends Annotation> annotation, String... packageNames) {
        if (packageNames == null) {
            return Collections.emptySet();
        }

        if (log.isDebugEnabled()) {
            log.debug("Searching for annotations of {} in packages: {}", annotation.getName(), Arrays.asList(packageNames));
        }

        PackageScanFilter test = getCompositeFilter(new AnnotatedWithPackageScanFilter(annotation, true));
        Set<Class<?>> classes = new LinkedHashSet<>();
        for (String pkg : packageNames) {
            find(test, pkg, classes);
        }

        log.debug("Found: {}", classes);

        return classes;
    }

    @Override
    public Set<Class<?>> findAnnotated(Set<Class<? extends Annotation>> annotations, String... packageNames) {
        if (packageNames == null) {
            return Collections.emptySet();
        }

        if (log.isDebugEnabled()) {
            log.debug("Searching for annotations of {} in packages: {}", annotations, Arrays.asList(packageNames));
        }

        PackageScanFilter test = getCompositeFilter(new AnnotatedWithAnyPackageScanFilter(annotations, true));
        Set<Class<?>> classes = new LinkedHashSet<>();
        for (String pkg : packageNames) {
            find(test, pkg, classes);
        }

        log.debug("Found: {}", classes);

        return classes;
    }

    @Override
    public Set<Class<?>> findImplementations(Class<?> parent, String... packageNames) {
        if (packageNames == null) {
            return Collections.emptySet();
        }

        if (log.isDebugEnabled()) {
            log.debug("Searching for implementations of {} in packages: {}", parent.getName(), Arrays.asList(packageNames));
        }

        PackageScanFilter test = getCompositeFilter(new AssignableToPackageScanFilter(parent));
        Set<Class<?>> classes = new LinkedHashSet<>();
        for (String pkg : packageNames) {
            find(test, pkg, classes);
        }

        log.debug("Found: {}", classes);

        return classes;
    }

    @Override
    public Set<Class<?>> findByFilter(PackageScanFilter filter, String... packageNames) {
        if (packageNames == null) {
            return Collections.emptySet();
        }

        Set<Class<?>> classes = new LinkedHashSet<>();
        for (String pkg : packageNames) {
            find(filter, pkg, classes);
        }

        log.debug("Found: {}", classes);

        return classes;
    }

    protected void find(PackageScanFilter test, String packageName, Set<Class<?>> classes) {
        packageName = packageName.replace('.', '/');

        Set<ClassLoader> set = getClassLoaders();

        for (ClassLoader classLoader : set) {
            find(test, packageName, classLoader, classes);
        }
    }

    protected void find(PackageScanFilter test, String packageName, ClassLoader loader, Set<Class<?>> classes) {
        if (log.isTraceEnabled()) {
            log.trace("Searching for: {} in package: {} using classloader: {}", 
                    new Object[]{test, packageName, loader.getClass().getName()});
        }

        Enumeration<URL> urls;
        try {
            urls = getResources(loader, packageName);
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

                log.trace("Scanning for classes in: {} matching criteria: {}", urlPath, test);

                File file = new File(urlPath);
                if (file.isDirectory()) {
                    log.trace("Loading from directory using file: {}", file);
                    loadImplementationsInDirectory(test, packageName, file, classes);
                } else {
                    InputStream stream = null;
                    try {
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

                        // only create jar cache on-demand when needed
                        if (jarCache == null) {
                            // use a soft cache so it can be claimed if needed
                            jarCache = LRUCacheFactory.newLRUSoftCache(1000);
                        }

                        loadImplementationsInJar(test, packageName, stream, urlPath, classes, jarCache);
                    } finally {
                        IOHelper.close(stream);
                    }
                }
            } catch (IOException e) {
                // use debug logging to avoid being to noisy in logs
                log.debug("Cannot read entries in url: {}", url, e);
            }
        }
    }

    private PackageScanFilter getCompositeFilter(PackageScanFilter filter) {
        if (scanFilters != null) {
            CompositePackageScanFilter composite = new CompositePackageScanFilter(scanFilters);
            composite.addFilter(filter);
            return composite;
        }
        return filter;
    }

    /**
     * Finds matches in a physical directory on a filesystem. Examines all files
     * within a directory - if the File object is not a directory, and ends with
     * <i>.class</i> the file is loaded and tested to see if it is acceptable
     * according to the Test. Operates recursively to find classes within a
     * folder structure matching the package structure.
     *
     * @param test     a Test used to filter the classes that are discovered
     * @param parent   the package name up to this directory in the package
     *                 hierarchy. E.g. if /classes is in the classpath and we wish to
     *                 examine files in /classes/org/apache then the values of
     *                 <i>parent</i> would be <i>org/apache</i>
     * @param location a File object representing a directory
     */
    private void loadImplementationsInDirectory(PackageScanFilter test, String parent, File location, Set<Class<?>> classes) {
        File[] files = location.listFiles();
        StringBuilder builder;

        for (File file : files) {
            builder = new StringBuilder(100);
            String name = file.getName();
            if (name != null) {
                name = name.trim();
                builder.append(parent).append("/").append(name);
                String packageOrClass = parent == null ? name : builder.toString();

                if (file.isDirectory()) {
                    loadImplementationsInDirectory(test, packageOrClass, file, classes);
                } else if (name.endsWith(".class")) {
                    addIfMatching(test, packageOrClass, classes);
                }
            }
        }
    }

    /**
     * Finds matching classes within a jar files that contains a folder
     * structure matching the package structure. If the File is not a JarFile or
     * does not exist a warning will be logged, but no error will be raised.
     *
     * @param test    a Test used to filter the classes that are discovered
     * @param parent  the parent package under which classes must be in order to
     *                be considered
     * @param stream  the inputstream of the jar file to be examined for classes
     * @param urlPath the url of the jar file to be examined for classes
     * @param classes to add found and matching classes
     * @param jarCache cache for JARs to speedup loading
     */
    private void loadImplementationsInJar(PackageScanFilter test, String parent, InputStream stream,
                                          String urlPath, Set<Class<?>> classes, Map<String, List<String>> jarCache) {
        ObjectHelper.notNull(classes, "classes");

        List<String> entries = jarCache != null ? jarCache.get(urlPath) : null;
        if (entries == null) {
            entries = doLoadJarClassEntries(stream, urlPath);
            if (jarCache != null) {
                jarCache.put(urlPath, entries);
                log.trace("Cached {} JAR with {} entries", urlPath, entries.size());
            }
        } else {
            log.trace("Using cached {} JAR with {} entries", urlPath, entries.size());
        }

        doLoadImplementationsInJar(test, parent, entries, classes);
    }

    /**
     * Loads all the class entries from the JAR.
     *
     * @param stream  the inputstream of the jar file to be examined for classes
     * @param urlPath the url of the jar file to be examined for classes
     * @return all the .class entries from the JAR
     */
    protected List<String> doLoadJarClassEntries(InputStream stream, String urlPath) {
        List<String> entries = new ArrayList<>();

        JarInputStream jarStream = null;
        try {
            jarStream = new JarInputStream(stream);

            JarEntry entry;
            while ((entry = jarStream.getNextJarEntry()) != null) {
                String name = entry.getName();
                if (name != null) {
                    name = name.trim();
                    if (!entry.isDirectory() && name.endsWith(".class")) {
                        entries.add(name);
                    }
                }
            }
        } catch (IOException ioe) {
            log.warn("Cannot search jar file '" + urlPath + " due to an IOException: " + ioe.getMessage(), ioe);
        } finally {
            IOHelper.close(jarStream, urlPath, log);
        }

        return entries;
    }

    /**
     * Adds all the matching implementations from from the JAR entries to the classes.
     *
     * @param test    a Test used to filter the classes that are discovered
     * @param parent  the parent package under which classes must be in order to be considered
     * @param entries the .class entries from the JAR
     * @param classes to add found and matching classes
     */
    private void doLoadImplementationsInJar(PackageScanFilter test, String parent, List<String> entries, Set<Class<?>> classes) {
        for (String entry : entries) {
            if (entry.startsWith(parent)) {
                addIfMatching(test, entry, classes);
            }
        }
    }

    /**
     * Add the class designated by the fully qualified class name provided to
     * the set of resolved classes if and only if it is approved by the Test
     * supplied.
     *
     * @param test the test used to determine if the class matches
     * @param fqn  the fully qualified name of a class
     */    
    protected void addIfMatching(PackageScanFilter test, String fqn, Set<Class<?>> classes) {
        try {
            String externalName = fqn.substring(0, fqn.indexOf('.')).replace('/', '.');
            Set<ClassLoader> set = getClassLoaders();
            boolean found = false;
            for (ClassLoader classLoader : set) {
                if (log.isTraceEnabled()) {
                    log.trace("Testing for class {} matches criteria [{}] using classloader: {}", externalName, test, classLoader);
                }
                try {
                    Class<?> type = classLoader.loadClass(externalName);
                    log.trace("Loaded the class: {} in classloader: {}", type, classLoader);
                    if (test.matches(type)) {
                        log.trace("Found class: {} which matches the filter in classloader: {}", type, classLoader);
                        classes.add(type);
                    }
                    found = true;
                    break;
                } catch (ClassNotFoundException e) {
                    if (log.isTraceEnabled()) {
                        log.trace("Cannot find class '" + fqn + "' in classloader: " + classLoader
                                + ". Reason: " + e.getMessage(), e);
                    }
                } catch (NoClassDefFoundError e) {
                    if (log.isTraceEnabled()) {
                        log.trace("Cannot find the class definition '" + fqn + "' in classloader: " + classLoader
                            + ". Reason: " + e.getMessage(), e);
                    }
                }
            }
            if (!found) {
                log.debug("Cannot find class '{}' in any classloaders: {}", fqn, set);
            }
        } catch (Exception e) {
            if (log.isWarnEnabled()) {
                log.warn("Cannot examine class '" + fqn + "' due to a " + e.getClass().getName()
                    + " with message: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void clearCache() {
        if (jarCache != null) {
            jarCache.clear();
            jarCache = null;
        }
    }

    @Override
    protected void doStop() throws Exception {
        clearCache();
    }

}
