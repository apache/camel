/**
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
package org.apache.camel.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.PackageScanFilter;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Default implement of {@link org.apache.camel.spi.PackageScanClassResolver}
 */
public class DefaultPackageScanClassResolver implements PackageScanClassResolver {

    protected static final transient Log LOG = LogFactory.getLog(DefaultPackageScanClassResolver.class);
    private Set<ClassLoader> classLoaders;

    public void addClassLoader(ClassLoader classLoader) {
        getClassLoaders().add(classLoader);
    }

    public Set<ClassLoader> getClassLoaders() {
        if (classLoaders == null) {
            classLoaders = new HashSet<ClassLoader>();
            ClassLoader ccl = Thread.currentThread().getContextClassLoader();
            if (ccl != null) {
                classLoaders.add(ccl);
            }
            classLoaders.add(DefaultPackageScanClassResolver.class.getClassLoader());
        }
        return classLoaders;
    }

    public void setClassLoaders(Set<ClassLoader> classLoaders) {
        this.classLoaders = classLoaders;
    }

    @SuppressWarnings("unchecked")
    public Set<Class> findAnnotated(Class<? extends Annotation> annotation, String... packageNames) {
        if (packageNames == null) {
            return Collections.EMPTY_SET;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Searching for annotations of " + annotation.getName() + " in packages: " + Arrays.asList(packageNames));
        }

        PackageScanFilter test = new AnnotatedWithPackageScanFilter(annotation, true);
        Set<Class> classes = new LinkedHashSet<Class>();
        for (String pkg : packageNames) {
            find(test, pkg, classes);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Found: " + classes);
        }

        return classes;
    }

    @SuppressWarnings("unchecked")
    public Set<Class> findAnnotated(Set<Class<? extends Annotation>> annotations, String... packageNames) {
        if (packageNames == null) {
            return Collections.EMPTY_SET;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Searching for annotations of " + annotations + " in packages: " + Arrays.asList(packageNames));
        }

        PackageScanFilter test = new AnnotatedWithAnyPackageScanFilter(annotations, true);
        Set<Class> classes = new LinkedHashSet<Class>();
        for (String pkg : packageNames) {
            find(test, pkg, classes);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Found: " + classes);
        }

        return classes;
    }

    @SuppressWarnings("unchecked")
    public Set<Class> findImplementations(Class parent, String... packageNames) {
        if (packageNames == null) {
            return Collections.EMPTY_SET;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Searching for implementations of " + parent.getName() + " in packages: " + Arrays.asList(packageNames));
        }

        PackageScanFilter test = new AssignableToPackageScanFilter(parent);
        Set<Class> classes = new LinkedHashSet<Class>();
        for (String pkg : packageNames) {
            find(test, pkg, classes);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Found: " + classes);
        }

        return classes;
    }

    @SuppressWarnings("unchecked")
    public Set<Class> findByFilter(PackageScanFilter filter, String... packageNames) {
        if (packageNames == null) {
            return Collections.EMPTY_SET;
        }

        Set<Class> classes = new LinkedHashSet<Class>();
        for (String pkg : packageNames) {
            find(filter, pkg, classes);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Found: " + classes);
        }

        return classes;
    }


    protected void find(PackageScanFilter test, String packageName, Set<Class> classes) {
        packageName = packageName.replace('.', '/');

        Set<ClassLoader> set = getClassLoaders();

        for (ClassLoader classLoader : set) {            
            find(test, packageName, classLoader, classes);
        }
    }

    protected void find(PackageScanFilter test, String packageName, ClassLoader loader, Set<Class> classes) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Searching for: " + test + " in package: " + packageName + " using classloader: "
                    + loader.getClass().getName());
        }

        Enumeration<URL> urls;
        try {
            urls = getResources(loader, packageName);
            if (!urls.hasMoreElements()) {
                LOG.trace("No URLs returned by classloader");
            }
        } catch (IOException ioe) {
            LOG.warn("Could not read package: " + packageName, ioe);
            return;
        }

        while (urls.hasMoreElements()) {
            URL url = null;
            try {
                url = urls.nextElement();
                if (LOG.isTraceEnabled()) {
                    LOG.trace("URL from classloader: " + url);
                }

                String urlPath = url.getFile();
                urlPath = URLDecoder.decode(urlPath, "UTF-8");
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Decoded urlPath: " + urlPath);
                }

                // If it's a file in a directory, trim the stupid file: spec
                if (urlPath.startsWith("file:")) {
                    urlPath = urlPath.substring(5);
                }

                // osgi bundles should be skipped
                if (url.toString().startsWith("bundle:") || urlPath.startsWith("bundle:")) {
                    LOG.trace("It's a virtual osgi bundle, skipping");
                    continue;
                }

                // Else it's in a JAR, grab the path to the jar
                if (urlPath.indexOf('!') > 0) {
                    urlPath = urlPath.substring(0, urlPath.indexOf('!'));
                }

                if (LOG.isTraceEnabled()) {
                    LOG.trace("Scanning for classes in [" + urlPath + "] matching criteria: " + test);
                }

                File file = new File(urlPath);
                if (file.isDirectory()) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Loading from directory: " + file);
                    }
                    loadImplementationsInDirectory(test, packageName, file, classes);
                } else {
                    InputStream stream;
                    if (urlPath.startsWith("http:")) {
                        // load resources using http such as java webstart
                        LOG.debug("The current jar is accessed via http");
                        URL urlStream = new URL(urlPath);
                        URLConnection con = urlStream.openConnection();
                        // disable cache mainly to avoid jar file locking on Windows
                        con.setUseCaches(false);
                        stream = con.getInputStream();
                    } else {
                        stream = new FileInputStream(file);
                    }

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Loading from jar: " + file);
                    }
                    loadImplementationsInJar(test, packageName, stream, urlPath, classes);
                }
            } catch (IOException ioe) {
                LOG.warn("Could not read entries in url: " + url, ioe);
            }
        }
    }

    /**
     * Strategy to get the resources by the given classloader.
     * <p/>
     * Notice that in WebSphere platforms there is a {@link WebSpherePacakageScanClassResolver}
     * to take care of WebSphere's odditiy of resource loading.
     *
     * @param loader  the classloader
     * @param packageName   the packagename for the package to load
     * @return  URL's for the given package
     * @throws IOException is thrown by the classloader
     */
    protected Enumeration<URL> getResources(ClassLoader loader, String packageName) throws IOException {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Getting resource URL for package: " + packageName + " with classloader: " + loader);
        }
        return loader.getResources(packageName);
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
    private void loadImplementationsInDirectory(PackageScanFilter test, String parent, File location, Set<Class> classes) {
        File[] files = location.listFiles();
        StringBuilder builder = null;

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
     */
    private void loadImplementationsInJar(PackageScanFilter test, String parent, InputStream stream, String urlPath, Set<Class> classes) {
        JarInputStream jarStream = null;
        try {
            jarStream = new JarInputStream(stream);

            JarEntry entry;
            while ((entry = jarStream.getNextJarEntry()) != null) {
                String name = entry.getName();
                if (name != null) {
                    name = name.trim();
                    if (!entry.isDirectory() && name.startsWith(parent) && name.endsWith(".class")) {
                        addIfMatching(test, name, classes);
                    }
                }
            }
        } catch (IOException ioe) {
            LOG.error("Could not search jar file '" + urlPath + "' for classes matching criteria: " + test
                + " due to an IOException: " + ioe.getMessage(), ioe);
        } finally {
            ObjectHelper.close(jarStream, urlPath, LOG);
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
    @SuppressWarnings("unchecked")
    protected void addIfMatching(PackageScanFilter test, String fqn, Set<Class> classes) {
        try {
            String externalName = fqn.substring(0, fqn.indexOf('.')).replace('/', '.');
            Set<ClassLoader> set = getClassLoaders();
            boolean found = false;
            for (ClassLoader classLoader : set) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Testing for class " + externalName + " matches criteria [" + test + "]");
                }
                try {
                    Class type = classLoader.loadClass(externalName);
                    if (test.matches(type)) {
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("Found class: " + type + " in classloader: " + classLoader);
                        }
                        classes.add(type);
                    }
                    found = true;
                    break;
                } catch (ClassNotFoundException e) {
                    LOG.debug("Could not find class '" + fqn + "' in classloader: " + classLoader
                        + ". Reason: " + e, e);
                } catch (NoClassDefFoundError e) {
                    LOG.debug("Could not find the class defintion '" + fqn + "' in classloader: " + classLoader
                              + ". Reason: " + e, e);
                }
            }
            if (!found) {
                LOG.warn("Could not find class '" + fqn + "' in any classloaders: " + set);
            }
        } catch (Throwable t) {
            LOG.warn("Could not examine class '" + fqn + "' due to a " + t.getClass().getName()
                + " with message: " + t.getMessage(), t);
        }
    }

}
