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
package org.apache.camel.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * <p>
 * ResolverUtil is used to locate classes that are available in the/a class path
 * and meet arbitrary conditions. The two most common conditions are that a
 * class implements/extends another class, or that is it annotated with a
 * specific annotation. However, through the use of the {@link Test} class it is
 * possible to search using arbitrary conditions.
 * </p>
 * <p/>
 * <p>
 * A ClassLoader is used to locate all locations (directories and jar files) in
 * the class path that contain classes within certain packages, and then to load
 * those classes and check them. By default the ClassLoader returned by
 * {@code Thread.currentThread().getContextClassLoader()} is used, but this can
 * be overridden by calling {@link #setClassLoaders(Set)} prior to
 * invoking any of the {@code find()} methods.
 * </p>
 * <p/>
 * <p>
 * General searches are initiated by calling the
 * {@link #find(ResolverUtil.Test, String)} ()} method and supplying a package
 * name and a Test instance. This will cause the named package <b>and all
 * sub-packages</b> to be scanned for classes that meet the test. There are
 * also utility methods for the common use cases of scanning multiple packages
 * for extensions of particular classes, or classes annotated with a specific
 * annotation.
 * </p>
 * <p/>
 * <p>
 * The standard usage pattern for the ResolverUtil class is as follows:
 * </p>
 * <p/>
 * <pre>
 * resolverUtil&lt;ActionBean&gt; resolver = new ResolverUtil&lt;ActionBean&gt;();
 * resolver.findImplementation(ActionBean.class, pkg1, pkg2);
 * resolver.find(new CustomTest(), pkg1);
 * resolver.find(new CustomTest(), pkg2);
 * collection&lt;ActionBean&gt; beans = resolver.getClasses();
 * </pre>
 *
 * @author Tim Fennell
 */
public class ResolverUtil<T> {
    protected static final transient Log LOG = LogFactory.getLog(ResolverUtil.class);

    /**
     * A simple interface that specifies how to test classes to determine if
     * they are to be included in the results produced by the ResolverUtil.
     */
    public static interface Test {
        /**
         * Will be called repeatedly with candidate classes. Must return True if
         * a class is to be included in the results, false otherwise.
         */
        boolean matches(Class type);
    }

    /**
     * A Test that checks to see if each class is assignable to the provided
     * class. Note that this test will match the parent type itself if it is
     * presented for matching.
     */
    public static class IsA implements Test {
        private Class parent;

        /**
         * Constructs an IsA test using the supplied Class as the parent
         * class/interface.
         */
        public IsA(Class parentType) {
            this.parent = parentType;
        }

        /**
         * Returns true if type is assignable to the parent type supplied in the
         * constructor.
         */
        public boolean matches(Class type) {
            return type != null && parent.isAssignableFrom(type);
        }

        @Override
        public String toString() {
            return "is assignable to " + parent.getSimpleName();
        }
    }

    /**
     * A Test that checks to see if each class is annotated with a specific
     * annotation. If it is, then the test returns true, otherwise false.
     */
    public static class AnnotatedWith implements Test {
        private Class<? extends Annotation> annotation;
        private boolean checkMetaAnnotations;

        /**
         * Constructs an AnnotatedWith test for the specified annotation type.
         */
        public AnnotatedWith(Class<? extends Annotation> annotation) {
            this(annotation, false);
        }

        /**
         * Constructs an AnnotatedWith test for the specified annotation type.
         */
        public AnnotatedWith(Class<? extends Annotation> annotation, boolean checkMetaAnnotations) {
            this.annotation = annotation;
            this.checkMetaAnnotations = checkMetaAnnotations;
        }

        /**
         * Returns true if the type is annotated with the class provided to the
         * constructor.
         */
        public boolean matches(Class type) {
            return type != null && ObjectHelper.hasAnnotation(type, annotation, checkMetaAnnotations);
        }

        @Override
        public String toString() {
            return "annotated with @" + annotation.getSimpleName();
        }
    }

    /**
     * The set of matches being accumulated.
     */
    private Set<Class<? extends T>> matches = new HashSet<Class<? extends T>>();

    /**
     * The ClassLoader to use when looking for classes. If null then the
     * ClassLoader returned by Thread.currentThread().getContextClassLoader()
     * will be used.
     */
    private Set<ClassLoader> classLoaders;

    /**
     * Provides access to the classes discovered so far. If no calls have been
     * made to any of the {@code find()} methods, this set will be empty.
     *
     * @return the set of classes that have been discovered.
     */
    public Set<Class<? extends T>> getClasses() {
        return matches;
    }


    /**
     * Returns the classloaders that will be used for scanning for classes. If no
     * explicit ClassLoader has been set by the calling, the context class
     * loader will and the one that has loaded this class ResolverUtil be used.
     *
     * @return the ClassLoader instances that will be used to scan for classes
     */
    public Set<ClassLoader> getClassLoaders() {
        if (classLoaders == null) {
            classLoaders = new HashSet<ClassLoader>();
            ClassLoader ccl = Thread.currentThread().getContextClassLoader();
            if (ccl != null) {
                classLoaders.add(ccl);
            }
            classLoaders.add(ResolverUtil.class.getClassLoader());
        }
        return classLoaders;
    }

    /**
     * Sets the ClassLoader instances that should be used when scanning for
     * classes. If none is set then the context classloader will be used.
     *
     * @param classLoaders a ClassLoader to use when scanning for classes
     */
    public void setClassLoaders(Set<ClassLoader> classLoaders) {
        this.classLoaders = classLoaders;
    }

    /**
     * Attempts to discover classes that are assignable to the type provided. In
     * the case that an interface is provided this method will collect
     * implementations. In the case of a non-interface class, subclasses will be
     * collected. Accumulated classes can be accessed by calling
     * {@link #getClasses()}.
     *
     * @param parent       the class of interface to find subclasses or
     *                     implementations of
     * @param packageNames one or more package names to scan (including
     *                     subpackages) for classes
     */
    public void findImplementations(Class parent, String... packageNames) {
        if (packageNames == null) {
            return;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Searching for implementations of " + parent.getName() + " in packages: " + Arrays
                .asList(packageNames));
        }

        Test test = new IsA(parent);
        for (String pkg : packageNames) {
            find(test, pkg);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Found: " + getClasses());
        }
    }

    /**
     * Attempts to discover classes that are annotated with to the annotation.
     * Accumulated classes can be accessed by calling {@link #getClasses()}.
     *
     * @param annotation   the annotation that should be present on matching
     *                     classes
     * @param packageNames one or more package names to scan (including
     *                     subpackages) for classes
     */
    public void findAnnotated(Class<? extends Annotation> annotation, String... packageNames) {
        if (packageNames == null) {
            return;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Searching for annotations of " + annotation.getName() + " in packages: " + Arrays
                .asList(packageNames));
        }

        Test test = new AnnotatedWith(annotation, true);
        for (String pkg : packageNames) {
            find(test, pkg);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Found: " + getClasses());
        }
    }

    /**
     * Scans for classes starting at the package provided and descending into
     * subpackages. Each class is offered up to the Test as it is discovered,
     * and if the Test returns true the class is retained. Accumulated classes
     * can be fetched by calling {@link #getClasses()}.
     *
     * @param test        an instance of {@link Test} that will be used to filter
     *                    classes
     * @param packageName the name of the package from which to start scanning
     *                    for classes, e.g. {@code net.sourceforge.stripes}
     */
    public void find(Test test, String packageName) {
        packageName = packageName.replace('.', '/');

        Set<ClassLoader> set = getClassLoaders();

        LOG.debug("Using only regular classloaders");
        for (ClassLoader classLoader : set) {            
            find(test, packageName, classLoader);            
        }
    }

    

    /**
     * Tries to find the reosurce in the package using the class loader.
     * <p/>
     * Will handle both plain URL based classloaders and OSGi bundle loaders.
     *
     * @param test what to find
     * @param packageName the package to search in
     * @param loader the class loader     
     */
    protected void find(Test test, String packageName, ClassLoader loader) {
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
                    loadImplementationsInDirectory(test, packageName, file);
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
                    loadImplementationsInJar(test, packageName, stream, urlPath);
                }
            } catch (IOException ioe) {
                LOG.warn("Could not read entries in url: " + url, ioe);
            }
        }
    }

    /**
     * Strategy to get the resources by the given classloader.
     * <p/>
     * Notice that in WebSphere platforms there is a {@link org.apache.camel.util.WebSphereResolverUtil}
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
    private void loadImplementationsInDirectory(Test test, String parent, File location) {
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
                    loadImplementationsInDirectory(test, packageOrClass, file);
                } else if (name.endsWith(".class")) {
                    addIfMatching(test, packageOrClass);
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
     * @param jarfile the jar file to be examined for classes
     * @param stream  the inputstream of the jar file to be examined for classes
     * @param urlPath the url of the jar file to be examined for classes
     */
    private void loadImplementationsInJar(Test test, String parent, InputStream stream, String urlPath) {
        JarInputStream jarStream = null;
        try {
            jarStream = new JarInputStream(stream);

            JarEntry entry;
            while ((entry = jarStream.getNextJarEntry()) != null) {
                String name = entry.getName();
                if (name != null) {
                    name = name.trim();
                    if (!entry.isDirectory() && name.startsWith(parent) && name.endsWith(".class")) {
                        addIfMatching(test, name);
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
    protected void addIfMatching(Test test, String fqn) {
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
                        matches.add((Class<T>)type);
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
